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
import org.opencastproject.smil.entity.api.Smil;
import org.opencastproject.smil.entity.media.container.api.SmilMediaContainer;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workspace.api.Workspace;

import com.google.gson.Gson;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;


/**
 * The workflow definition for converting a smil containing cut marks into a legal smil for cutting
 */
public class CutMarksToSmilWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** Workflow configuration keys */
  private static final String SOURCE_PRESENTER_FLAVOR = "source-presenter-flavor";
  private static final String SOURCE_PRESENTATION_FLAVOR = "source-presentation-flavor";
  private static final String SOURCE_JSON_FLAVOR = "source-json-flavor";

  private static final String TARGET_PRESENTER_FLAVOR = "target-presenter-flavor";
  private static final String TARGET_PRESENTATION_FLAVOR = "target-presentation-flavor";
  private static final String TARGET_SMIL_FLAVOR = "target-smil-flavor";

  private static final String CUTTING_SMIL_NAME = "prepared_cutting_smil";

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

  /** JSON Parser */
  private static final Gson gson = new Gson();

  /** Stores information read from JSON */
  class Times {
    private Long begin;
    private Long duration;

    public Long getStartTime() {
      return begin;
    }
    public void setStartTime(Long startTime) {
      this.begin = startTime;
    }
    public Long getDuration() {
      return duration;
    }
    public void setDuration(Long duration) {
      this.duration = duration;
    }
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

    // Read config options
    final MediaPackageElementFlavor jsonFlavor = MediaPackageElementFlavor.parseFlavor(
            getConfig(operation, SOURCE_JSON_FLAVOR));
    final MediaPackageElementFlavor presenterFlavor = parseTargetFlavor(
            getConfig(operation, SOURCE_PRESENTER_FLAVOR), "presenter");
    final MediaPackageElementFlavor presentationFlavor = parseTargetFlavor(
            getConfig(operation, SOURCE_PRESENTATION_FLAVOR), "presentation");
    final MediaPackageElementFlavor targetSmilFlavor = MediaPackageElementFlavor.parseFlavor(
            getConfig(operation, TARGET_SMIL_FLAVOR));

    // Is there a catalog?
    Catalog[] catalogs = mediaPackage.getCatalogs(jsonFlavor);
    if (catalogs.length != 1) {
      logger.warn("Number of catalogs in the source flavor does not equal one. Skipping...");
      return skip(mediaPackage);
    }

    // Parse JSON
    Times[] cutmarks;
    Catalog jsonWithTimes = catalogs[0];
    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(getMediaPackageElementPath(jsonWithTimes)))) {
      cutmarks = gson.fromJson(bufferedReader, Times[].class);
    } catch (Exception e) {
      throw new WorkflowOperationException("Could not read JSON: " + e);
    }

    // If the catalog was empty, give up
    if (cutmarks.length < 1) {
      logger.warn("Source JSON did not contain any timestamps! Skipping...");
      return skip(mediaPackage);
    }

    // Check parsing results
    for (Times entry : cutmarks) {
      logger.info("Entry begin {}, Entry duration {}", entry.begin, entry.duration);
      if (entry.begin < 0 || entry.duration < 0) {
        throw new WorkflowOperationException("Times cannot be negative!");
      }
    }

    /** Create the new SMIL document **/
    logger.info("Get Tracks from Mediapackage");
    List<Track> videosPresenter = $(mediaPackage.getTracks()).filter(
            MediaPackageSupport.Filters.matchesFlavor(presenterFlavor).toFn()).toList();
    List<Track> videosPresentation = $(mediaPackage.getTracks()).filter(
            MediaPackageSupport.Filters.matchesFlavor(presentationFlavor).toFn()).toList();

    // Check for number of videos to avoid any issues
    // Possible TODO: Handle more than exactly one track per flavor
    if (videosPresenter.size() != 1 || videosPresentation.size() != 1) {
      for (Track track : videosPresenter) {
        logger.info("VideosPresenter track: {}", track);
      }
      for (Track track : videosPresentation) {
        logger.info("VideosPresentation track: {}", track);
      }
      throw new WorkflowOperationException("The number of videos in each flavor must be exactly one.");
    }

    Track presenterTrack = videosPresenter.get(0);
    Track presentationTrack = videosPresentation.get(0);
    logger.info("PresenterTrack duration: {}, PresentationTrack duration {}", presenterTrack.getDuration(),
            presentationTrack.getDuration());

    try {
      SmilResponse smilResponse = smilService.createNewSmil(mediaPackage);

      logger.info("Start Adding tracks");
      for (Times mark : cutmarks) {
        smilResponse = smilService.addParallel(smilResponse.getSmil());
        SmilMediaContainer par = (SmilMediaContainer) smilResponse.getEntity();
        // add tracks (as array) to par
        smilResponse = smilService
                .addClips(smilResponse.getSmil(),
                        par.getId(),
                        new Track[] { presenterTrack, presentationTrack },
                        mark.begin,
                        mark.duration);
      }

      Smil smil = smilResponse.getSmil();
      logger.info("Done Adding tracks");
      InputStream is = null;
      try {
        // Put new SMIL into workspace and add to mediapackage
        is = IOUtils.toInputStream(smil.toXML(), "UTF-8");
        URI smilURI = workspace.put(mediaPackage.getIdentifier().toString(), smil.getId(), CUTTING_SMIL_NAME, is);
        MediaPackageElementBuilder mpeBuilder = MediaPackageElementBuilderFactory.newInstance().newElementBuilder();
        Catalog catalog = (Catalog) mpeBuilder
                .elementFromURI(smilURI, MediaPackageElement.Type.Catalog, targetSmilFlavor);
        catalog.setIdentifier(smil.getId());
        mediaPackage.add(catalog);
      } finally {
        IOUtils.closeQuietly(is);
      }
    } catch (Exception ex) {
      throw new WorkflowOperationException(
              format("Failed to create SMIL catalog for mediapackage %s", mediaPackage.getIdentifier().toString()), ex);
    }

    return skip(mediaPackage);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.AbstractWorkflowOperationHandler#skip(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  private WorkflowOperationResult skip(MediaPackage mediaPackage) {
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

  private String getMediaPackageElementPath(MediaPackageElement mpe) throws WorkflowOperationException {
    File mediaFile;
    try {
      mediaFile = workspace.get(mpe.getURI());
    } catch (NotFoundException e) {
      throw new WorkflowOperationException(
              "Error finding the media file in the workspace", e);
    } catch (IOException e) {
      throw new WorkflowOperationException(
              "Error reading the media file in the workspace", e);
    }

    String filePath = mediaFile.getAbsolutePath();
    return filePath;
  }
}
