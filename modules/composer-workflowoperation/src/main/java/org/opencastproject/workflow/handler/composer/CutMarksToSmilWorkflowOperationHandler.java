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
    for (int i = 0; i < timesSequences.getLength(); i++) {
      final SMILElement item = (SMILElement) timesSequences.item(i);
      NodeList children = item.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
        Node node = children.item(j);
        SMILMediaElement e = (SMILMediaElement) node;
        double beginInSeconds = e.getBegin().item(0).getResolvedOffset();
        long beginInMs = Math.round(beginInSeconds * 1000d);
        double durationInSeconds = e.getDur();
        long durationInMs = Math.round(durationInSeconds * 1000d);
        logger.info("Seq at {} Begin in Seconds: {}", j, beginInSeconds);
        logger.info("Seq at {} Dur in Seconds: {}", j, durationInSeconds);
        logger.info("Seq at {} Begin in ms: {}", j, beginInMs);
        logger.info("Seq at {} Dur in ms: {}", j, durationInMs);
        timeList.add(new ArrayList<Long>(Arrays.asList(beginInMs, durationInMs)));
      }
    }

    // If the catalog was empty, give up
    if(timeList.size() < 1) {
      throw new WorkflowOperationException("Source Smil did not contain any timestamps!");
    }


    // Create the new SMIL document
    // Possible TODO: Handle more than exactly one track per flavor
    logger.info("Get Tracks from Mediapackage");

    List<Track> videosPresentation = $(mediaPackage.getTracks()).filter(
            MediaPackageSupport.Filters.matchesFlavor(presentationFlavor).toFn()).toList();
    for (Track track : videosPresentation) {
      logger.info("VideosPresentation track: {}", track);
    }
    if (videosPresentation.size() != 1) {
      throw new WorkflowOperationException(String.format("Videos in flavor %s does not equal 1, but %s",
              presentationFlavor.toString(), Integer.toString(videosPresentation.size())));
    }

    List<Track> videosPresenter = $(mediaPackage.getTracks()).filter(
            MediaPackageSupport.Filters.matchesFlavor(presenterFlavor).toFn()).toList();
    for (Track track : videosPresenter) {
      logger.info("VideosPresenter track: {}", track);
    }
    if (videosPresenter.size() != 1) {
      throw new WorkflowOperationException(String.format("Videos in flavor %s does not equal 1, but %s",
              presenterFlavor.toString(), Integer.toString(videosPresenter.size())));
    }

    Track presentationTrack = videosPresentation.get(0);
    Track presenterTrack = videosPresenter.get(0);

    try {
      SmilResponse smilResponse = smilService.createNewSmil(mediaPackage);

      logger.info("Start Adding tracks");
      for (List<Long> entry : timeList) {
        Long startTime = entry.get(0);
        Long duration = entry.get(1);
        // Error handle bad times?

        smilResponse = smilService.addParallel(smilResponse.getSmil());
        SmilMediaContainer par = (SmilMediaContainer) smilResponse.getEntity();
        // add tracks (as array) to par
        smilResponse = smilService.addClips(smilResponse.getSmil(), par.getId(),
                new Track[]{presenterTrack, presentationTrack}, startTime, duration);
      }

      Smil smil = smilResponse.getSmil();
      logger.info("Done Adding tracks");

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
