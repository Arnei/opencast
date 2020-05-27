/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.workflow.handler.composer;

import static com.entwinemedia.fn.Stream.$;
import static java.lang.String.format;

import org.opencastproject.composer.api.ComposerService;
import org.opencastproject.composer.api.EncoderException;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementBuilder;
import org.opencastproject.mediapackage.MediaPackageElementBuilderFactory;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.MediaPackageSupport;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.smil.api.SmilResponse;
import org.opencastproject.smil.api.SmilService;
import org.opencastproject.smil.api.util.SmilUtil;
import org.opencastproject.smil.entity.api.Smil;
import org.opencastproject.smil.entity.media.container.api.SmilMediaContainer;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILElement;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILParElement;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The workflow definition for converting a smil containing cut marks into a legal smil for cutting
 */
public class CutMarksToSmilWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** Workflow configuration keys */
  private static final String SOURCE_PRESENTER_FLAVOR = "source-presenter-flavor";
  private static final String SOURCE_PRESENTATION_FLAVOR = "source-presentation-flavor";
  private static final String SOURCE_SMIL_FLAVOR = "source-smil-flavor";

  private static final String TARGET_PRESENTER_FLAVOR = "target-presenter-flavor";
  private static final String TARGET_PRESENTATION_FLAVOR = "target-presentation-flavor";
  private static final String TARGET_SMIL_FLAVOR = "target-smil-flavor";

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(PartialImportWorkflowOperationHandler.class);

  /** The composer service */
  private ComposerService composerService = null;

  /** The local workspace */
  private Workspace workspace = null;

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param composerService
   *          the local composer service
   */
  public void setComposerService(ComposerService composerService) {
    this.composerService = composerService;
  }

  /**
   * Callback for declarative services configuration that will introduce us to the local workspace service.
   * Implementation assumes that the reference is configured as being static.
   *
   * @param workspace
   *          an instance of the workspace
   */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * The SMIL service to modify SMIL files.
   */
  private SmilService smilService;
  public void setSmilService(SmilService smilService) {
    this.smilService = smilService;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  @Override
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.info("Running cut marks to smil workflow operation on workflow {}", workflowInstance.getId());

    List<MediaPackageElement> elementsToClean = new ArrayList<MediaPackageElement>();

    try {
      return cutMarksToSmil(workflowInstance.getMediaPackage(), workflowInstance.getCurrentOperation(), elementsToClean);
    } catch (Exception e) {
      throw new WorkflowOperationException(e);
    } finally {
      for (MediaPackageElement elem : elementsToClean) {
        try {
          workspace.delete(elem.getURI());
        } catch (Exception e) {
          logger.warn("Unable to delete element {}: {}", elem, e);
        }
      }
    }
  }

  private WorkflowOperationResult cutMarksToSmil(MediaPackage src, WorkflowOperationInstance operation,
          List<MediaPackageElement> elementsToClean) throws EncoderException, IOException, NotFoundException,
          MediaPackageException, WorkflowOperationException, ServiceRegistryException {
    final MediaPackage mediaPackage = (MediaPackage) src.clone();
    //
    // read config options
    final MediaPackageElementFlavor smilFlavor = MediaPackageElementFlavor.parseFlavor(getConfig(operation, SOURCE_SMIL_FLAVOR));
    final MediaPackageElementFlavor presenterFlavor = parseTargetFlavor(
            getConfig(operation, SOURCE_PRESENTER_FLAVOR), "presenter");
    final MediaPackageElementFlavor presentationFlavor = parseTargetFlavor(
            getConfig(operation, SOURCE_PRESENTATION_FLAVOR), "presentation");
    final MediaPackageElementFlavor targetSmilFlavor = MediaPackageElementFlavor.parseFlavor(getConfig(operation, TARGET_SMIL_FLAVOR));

//    // DEBUG
//    Long[] testTimeList = new Long[]{1L, 2L, 3L, 5L, 6L};
//    TrackImpl testPresenter1 = new TrackImpl();
//    testPresenter1.setIdentifier("presenter-track-1");
//    testPresenter1.setFlavor(new MediaPackageElementFlavor("source", "presenter"));
//    try {
//      testPresenter1.setURI(new URI("http://hostname/video.mp4"));
//    } catch (Exception e) {
//      logger.info("Could not set URI {}", e);
//    }
//    testPresenter1.addStream(new VideoStreamImpl());
//    testPresenter1.setDuration(1000000000000L);
//    TrackImpl testPresentation1 = new TrackImpl();
//    testPresentation1.setIdentifier("presentation-track-1");
//    testPresentation1.setFlavor(new MediaPackageElementFlavor("source", "presentation"));
//    try {
//      testPresentation1.setURI(new URI("http://hostname/videoPRESENTATION.mp4"));
//    } catch (Exception e) {
//      logger.info("Could not set URI {}", e);
//    }
//    testPresentation1.addStream(new VideoStreamImpl());
//    testPresentation1.setDuration(1000000000000L);
//    try {
//      SmilResponse smilResponse = smilService.createNewSmil(mediaPackage);
//
//      for (Long entry : testTimeList) {
//        Long startTime = entry;
//        Long duration = entry + 1000L;
//        // Error handle bad times?
//
//        // TODO: Figure out how to actually use these commands correctly
//        smilResponse = smilService.addParallel(smilResponse.getSmil());
//        SmilMediaContainer par = (SmilMediaContainer) smilResponse.getEntity();
//        // add tracks (as array) to par
//        smilResponse = smilService.addClips(smilResponse.getSmil(), par.getId(),
//                new Track[]{testPresenter1, testPresentation1}, 15000L, 1000L);
//      }
//
//      Smil smil = smilResponse.getSmil();
//      logger.info("ARNE SMIL Done Adding tracks");
//
//      String cuttingSmilName = "prepared_cutting_smil.smil";
//      InputStream is = null;
//      try {
//        // Put new SMIL into workspace
//        is = IOUtils.toInputStream(smil.toXML(), "UTF-8");
//        URI smilURI = workspace.put(mediaPackage.getIdentifier().compact(), smil.getId(), cuttingSmilName, is);
//        MediaPackageElementBuilder mpeBuilder = MediaPackageElementBuilderFactory.newInstance().newElementBuilder();
//        Catalog catalog = (Catalog) mpeBuilder.elementFromURI(smilURI, MediaPackageElement.Type.Catalog,
//                targetSmilFlavor);
//        catalog.setIdentifier(smil.getId());
//        mediaPackage.add(catalog);
//      } finally {
//        IOUtils.closeQuietly(is);
//      }
//    } catch (Exception ex) {
//      throw new WorkflowOperationException(
//              format("Failed to create SMIL catalog for mediapackage %s", mediaPackage.getIdentifier().compact()), ex);
//    }

    // Happy fun time with SMIL
    // Get SMIL catalog
    final List<List<Long>> timeList = new ArrayList<List<Long>>();
    final SMILDocument smilDocumentWithTimes;
    try {
      smilDocumentWithTimes = SmilUtil.getSmilDocumentFromMediaPackage(mediaPackage, smilFlavor, workspace);
    } catch (org.xml.sax.SAXException e) {
      throw new WorkflowOperationException(e);
    }

    // Parse Smil Catalog
    final SMILParElement timesParallel = (SMILParElement) smilDocumentWithTimes.getBody().getChildNodes().item(0);
    final NodeList timesSequences = timesParallel.getTimeChildren();
    logger.info("ARNE SMIL timesSequences: {}", timesSequences);
    for (int i = 0; i < timesSequences.getLength(); i++) {
      final SMILElement item = (SMILElement) timesSequences.item(i);
      logger.info("ARNE SMIL item: {}", item);
      NodeList children = item.getChildNodes();
      logger.info("ARNE SMIL children: {}", children);

      for (int j = 0; j < children.getLength(); j++) {
        Node node = children.item(j);
        SMILMediaElement e = (SMILMediaElement) node;
        double beginInSeconds = e.getBegin().item(0).getResolvedOffset();
        long beginInMs = Math.round(beginInSeconds * 1000d);
        double durationInSeconds = e.getDur();
        long durationInMs = Math.round(durationInSeconds * 1000d);
        logger.info("ARNE SMIL Begin in Seconds: {}", beginInSeconds);
        logger.info("ARNE SMIL Dur in Seconds: {}", durationInSeconds);
        logger.info("ARNE SMIL Begin in ms: {}", beginInMs);
        logger.info("ARNE SMIL Dur in ms: {}", durationInMs);
        timeList.add(new ArrayList<Long>(Arrays.asList(beginInMs, durationInMs)));
      }

    }

    // Create the SMIL document
    logger.info("ARNE SMIL Get Tracks from Mediapackage");

    List<Track> videosPresentation = $(mediaPackage.getTracks()).filter(
            MediaPackageSupport.Filters.matchesFlavor(presentationFlavor).toFn()).toList();
    for (Track track : videosPresentation) {
      logger.info("ARNE SMIL videosPresentation track: {}", track);
    }
    logger.info("ARNE SMIL videosPresentation size: {}", videosPresentation.size());
    if (videosPresentation.size() != 1) {
      logger.error("No video or too many videos");
    }
    List<Track> videosPresenter = $(mediaPackage.getTracks()).filter(
            MediaPackageSupport.Filters.matchesFlavor(presenterFlavor).toFn()).toList();
    for (Track track : videosPresenter) {
      logger.info("ARNE SMIL videosPresenter track: {}", track);
    }
    logger.info("ARNE SMIL videosPresenter size: {}", videosPresenter.size());
    if (videosPresenter.size() != 1) {
      logger.error("No video or too many videos");
    }

    Track presentationTrack = videosPresentation.get(0);
    Track presenterTrack = videosPresenter.get(0);

    logger.info("ARNE SMIL Start Adding tracks");

    try {
      SmilResponse smilResponse = smilService.createNewSmil(mediaPackage);

      for (List<Long> entry : timeList) {
        Long startTime = entry.get(0);
        Long duration = entry.get(1);
        // Error handle bad times?

        // TODO: Figure out how to actually use these commands correctlysudo
        //smilResponse = smilService.addParallel(smilResponse.getSmil());
        //smilResponse = smilService.addClip(smilResponse.getSmil(), smilResponse.getEntity().getId(),
        //        presenterTrack, startTime, duration);
        //smilResponse = smilService.addClip(smilResponse.getSmil(), smilResponse.getEntity().getId(),
        //        presentationTrack, startTime, duration);
        //smilResponse = smilService.addClips(smilResponse.getSmil(), smilResponse.getEntity().getId(),
        //        new Track[]{presenterTrack, presentationTrack}, startTime, duration);

        smilResponse = smilService.addParallel(smilResponse.getSmil());
        SmilMediaContainer par = (SmilMediaContainer) smilResponse.getEntity();
        // add tracks (as array) to par
        smilResponse = smilService.addClips(smilResponse.getSmil(), par.getId(),
                new Track[]{presenterTrack, presentationTrack}, startTime, duration);

      }

      Smil smil = smilResponse.getSmil();
      logger.info("ARNE SMIL Done Adding tracks");

      String cuttingSmilName = "prepared_cutting_smil";
      InputStream is = null;
      try {
        // Put new SMIL into workspace
        is = IOUtils.toInputStream(smil.toXML(), "UTF-8");
        URI smilURI = workspace.put(mediaPackage.getIdentifier().compact(), smil.getId(), cuttingSmilName, is);
        MediaPackageElementBuilder mpeBuilder = MediaPackageElementBuilderFactory.newInstance().newElementBuilder();
        Catalog catalog = (Catalog) mpeBuilder.elementFromURI(smilURI, MediaPackageElement.Type.Catalog,
                targetSmilFlavor);
        catalog.setIdentifier(smil.getId());
        mediaPackage.add(catalog);
      } finally {
        IOUtils.closeQuietly(is);
      }
    } catch (Exception ex) {
      throw new WorkflowOperationException(
              format("Failed to create SMIL catalog for mediapackage %s", mediaPackage.getIdentifier().compact()), ex);
    }

    final WorkflowOperationResult result = createResult(mediaPackage, WorkflowOperationResult.Action.CONTINUE);
    logger.debug("Partial import operation completed");
    return result;
  }

  /**
   * @param flavorType
   *          either "presenter" or "presentation", just for error messages
   */
  private MediaPackageElementFlavor parseTargetFlavor(String flavor, String flavorType)
          throws WorkflowOperationException {
    final MediaPackageElementFlavor targetFlavor;
    try {
      targetFlavor = MediaPackageElementFlavor.parseFlavor(flavor);
      if ("*".equals(targetFlavor.getType()) || "*".equals(targetFlavor.getSubtype())) {
        throw new WorkflowOperationException(format(
                "Target %s flavor must have a type and a subtype, '*' are not allowed!", flavorType));
      }
    } catch (IllegalArgumentException e) {
      throw new WorkflowOperationException(format("Target %s flavor '%s' is malformed", flavorType, flavor));
    }
    return targetFlavor;
  }
}
