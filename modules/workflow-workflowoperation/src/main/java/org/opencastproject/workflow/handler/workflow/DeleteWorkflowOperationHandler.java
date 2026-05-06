/*
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
package org.opencastproject.workflow.handler.workflow;


import static org.opencastproject.util.RequireUtil.notNull;

import org.opencastproject.assetmanager.api.AssetManager;
import org.opencastproject.elasticsearch.api.SearchIndexException;
import org.opencastproject.elasticsearch.index.ElasticsearchIndex;
import org.opencastproject.elasticsearch.index.objects.event.Event;
import org.opencastproject.index.service.api.IndexService;
import org.opencastproject.index.service.impl.util.EventUtils;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * WOH that removes a mediapackage. Will fail if not all publications have been retracted before.
 */
@Component(
    immediate = true,
    service = WorkflowOperationHandler.class,
    property = {
        "service.description=Delete Workflow Operation Handler",
        "workflow.operation=delete-event"
    }
)
public class DeleteWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  private IndexService indexService;
  private ElasticsearchIndex elasticsearchIndex;
  private AssetManager assetManager;
  private static final Logger logger = LoggerFactory.getLogger(DeleteWorkflowOperationHandler.class);

  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    notNull(workflowInstance, "workflowInstance");

    MediaPackage mp = workflowInstance.getMediaPackage();
    String id = mp.getIdentifier().toString();

    try {
      final Optional<Event> optEvent = indexService.getEvent(id, elasticsearchIndex);
      if (optEvent.isEmpty()) {
        logger.error("Event " + id + "not found");
        throw new WorkflowOperationException("Event " + id + " not found");
      }
      Event event = optEvent.get();

      final boolean hasOnlyEngageLive = event.getPublications().size() == 1
          && EventUtils.ENGAGE_LIVE_CHANNEL_ID.equals(event.getPublications().get(0).getChannel());
      final boolean retract = event.hasPreview()
          || (!event.getPublications().isEmpty()  && !hasOnlyEngageLive && this.hasSnapshots(event.getIdentifier()));
      if (retract) {
        throw new WorkflowOperationException("Event " + id + " has not been properly retracted, won't delete.");
      } else {
        try {
          final boolean success = indexService.removeEvent(event.getIdentifier());
          if (success) {
            return createResult(mp, Action.CONTINUE);
          } else {
            throw new WorkflowOperationException("Event " + id + " could not be deleted for unknown reasons");
          }
        } catch (NotFoundException e) {
          throw new WorkflowOperationException("Event " + id + "not found", e);
        } catch (UnauthorizedException e) {
          throw new WorkflowOperationException("Unauthorized to delete event " + id, e);
        }
      }
    } catch (SearchIndexException e) {
      throw new WorkflowOperationException("Unexpected error while searching for event " + id, e);
    }
  }

  private boolean hasSnapshots(String eventId) {
    return assetManager.snapshotExists(eventId);
  }

  @Reference
  public void setIndexService(IndexService indexService) {
    this.indexService = indexService;
  }
  @Reference
  void setElasticsearchIndex(ElasticsearchIndex elasticsearchIndex) {
    this.elasticsearchIndex = elasticsearchIndex;
  }
  @Reference
  public void setAssetManager(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

}
