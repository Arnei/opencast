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

package org.opencastproject.elasticsearch.index.objects.workflow;

import static org.opencastproject.security.api.SecurityConstants.GLOBAL_ADMIN_ROLE;

import org.opencastproject.elasticsearch.api.SearchTerms;
import org.opencastproject.elasticsearch.impl.AbstractElasticsearchQueryBuilder;
import org.opencastproject.elasticsearch.impl.IndexSchema;
import org.opencastproject.security.api.Role;
import org.opencastproject.security.api.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class WorkflowQueryBuilder extends AbstractElasticsearchQueryBuilder<WorkflowSearchQuery> {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(WorkflowQueryBuilder.class);

  /**
   * Creates a new elastic search query based on the workflow query.
   *
   * @param query
   *          the workflow query
   */
  public WorkflowQueryBuilder(WorkflowSearchQuery query) {
    super(query);
  }

  @Override
  public void buildQuery(WorkflowSearchQuery query) {

    // Organization
    if (query.getOrganization() == null) {
      throw new IllegalStateException("No organization set on the workflow search query!");
    }

    and(WorkflowIndexSchema.ORGANIZATION_ID, query.getOrganization());

    // Workflow identifier
    if (query.getIdentifiers().length > 0) {
      and(WorkflowIndexSchema.ID, query.getIdentifiers());
    }

    // Action
    if (query.getActions() != null && query.getActions().length > 0) {
      User user = query.getUser();
      if (!user.hasRole(GLOBAL_ADMIN_ROLE) && !user.hasRole(user.getOrganization().getAdminRole())) {
        for (Role role : user.getRoles()) {
          for (String action : query.getActions()) {
            and(WorkflowIndexSchema.ACL_PERMISSION_PREFIX.concat(action), role.getName());
          }
        }
      }
    }

    if (query.getState() != null) {
      and(WorkflowIndexSchema.STATE, query.getState());
    }

    if (query.getTemplate() != null) {
      and(WorkflowIndexSchema.TEMPLATE, query.getTemplate());
    }

    // Title
    if (query.getTitle() != null) {
      and(WorkflowIndexSchema.TITLE, query.getTitle());
    }

    if (query.getDescription() != null) {
      and(WorkflowIndexSchema.DESCRIPTION, query.getDescription());
    }

    if (query.getParentId() != null) {
      and(WorkflowIndexSchema.PARENT_ID, query.getParentId());
    }

    if (query.getCreator() != null) {
      and(WorkflowIndexSchema.CREATOR_NAME, query.getCreator());
    }

    if (query.getCurrentOperation() != null) {
      and(WorkflowIndexSchema.CURRENT_OPERATION, query.getCurrentOperation());
    }

    if (query.getDateCreatedFrom() != null && query.getDateCreatedTo() != null) {
//      and(WorkflowIndexSchema.DATE_CREATED, query.getDateCreated());
      and(WorkflowIndexSchema.DATE_CREATED, query.getDateCreatedFrom(), query.getDateCreatedTo());
    }

    if (query.getDateCompletedFrom() != null && query.getDateCompletedFrom() != null) {
//      and(WorkflowIndexSchema.DATE_COMPLETED, query.getDateCompleted());
      and(WorkflowIndexSchema.DATE_COMPLETED, query.getDateCompletedFrom(), query.getDateCompletedFrom());

    }

    if (query.getMediaPackageIds().length > 0) {
      and(WorkflowIndexSchema.MEDIAPACKAGE, query.getMediaPackageIds());
    }

    if (query.getSeriesId() != null) {
      and(WorkflowIndexSchema.SERIES, query.getSeriesId());
    }

    // Text
    if (query.getTerms() != null) {
      for (SearchTerms<String> terms : query.getTerms()) {
        StringBuffer queryText = new StringBuffer();
        for (String term : terms.getTerms()) {
          if (queryText.length() > 0) {
            queryText.append(" ");
          }
          queryText.append(term);
        }
        if (query.isFuzzySearch()) {
          fuzzyText = queryText.toString();
        } else {
          this.text = queryText.toString();
        }
        if (SearchTerms.Quantifier.All.equals(terms.getQuantifier())) {
          if (groups == null) {
            groups = new ArrayList<ValueGroup>();
          }
          if (query.isFuzzySearch()) {
            logger.warn("All quantifier not supported in conjunction with wildcard text");
          }
          groups.add(new ValueGroup(IndexSchema.TEXT, (Object[]) terms.getTerms().toArray(new String[terms.size()])));
        }
      }
    }

    // Filter query
    if (query.getFilter() != null) {
      this.filter = query.getFilter();
    }

  }
}
