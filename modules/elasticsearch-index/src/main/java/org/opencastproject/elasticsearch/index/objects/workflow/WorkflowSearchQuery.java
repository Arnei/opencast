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

import org.opencastproject.elasticsearch.impl.AbstractSearchQuery;
import org.opencastproject.security.api.Permissions;
import org.opencastproject.security.api.User;
import org.opencastproject.util.requests.SortCriterion;
import org.opencastproject.workflow.api.WorkflowInstance;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorkflowSearchQuery extends AbstractSearchQuery {

  protected List<String> identifiers = new ArrayList<String>();
  private User user = null;
  private Set<String> actions = new HashSet<String>();
  private WorkflowInstance.WorkflowState state = null;
  private String template = null;
  private String title = null;
  private String description = null;
  private Long parentId = null;
  private String creator = null;
  private String organizationId = null;
  private String currentOperation = null;
  private Date dateCreatedFrom = null;
  private Date dateCreatedTo = null;
  private Date dateCompletedFrom = null;
  private Date dateCompletedTo = null;
  private List<String> mediaPackageIds = new ArrayList<>();
  private List<String> mpContributors = new ArrayList<>();
  private String mpLanguage = null;
  private String mpLicense = null;
  private String mpTitle = null;
  private String mpSubject = null;
  private String seriesId = null;
  private String seriesTitle = null;
  private String accessPolicy = null;

  public WorkflowSearchQuery() {
  }

  /**
   * Creates a query that will return workflow documents.
   */
  public WorkflowSearchQuery(String organization, User user) {
    super(Workflow.DOCUMENT_TYPE);

    if (organization == null) {
      throw new IllegalStateException("The organization for this query was null.");
    }
    if (user == null) {
      throw new IllegalStateException("The user for this query was null.");
    }

    this.organizationId = organization;
    this.user = user;
    this.actions.add(Permissions.Action.READ.toString());
    if (!user.getOrganization().getId().equals(organization)) {
      throw new IllegalStateException("User's organization must match search organization");
    }
  }

  /**
   * Returns the list of workflow identifiers or an empty array if no identifiers have been specified.
   *
   * @return the identifiers
   */
  public String[] getIdentifiers() {
    return identifiers.toArray(new String[identifiers.size()]);
  }

  /**
   * Selects workflow with the given identifier.
   * <p>
   * Note that this method may be called multiple times to support selection of multiple workflow.
   *
   * @param id
   *          the workflow identifier
   * @return the enhanced search query
   */
  public WorkflowSearchQuery withIdentifier(String id) {
    if (StringUtils.isBlank(id)) {
      throw new IllegalArgumentException("Identifier cannot be null");
    }
    this.identifiers.add(id);
    return this;
  }

  /**
   * Returns the user of this search query
   *
   * @return the user of this search query
   */
  public User getUser() {
    return user;
  }

  /**
   * Filter the workflow without any action checked.
   *
   * @return the enhanced search query
   */
  public WorkflowSearchQuery withoutActions() {
    this.actions.clear();
    return this;
  }

  /**
   * Filter the workflow with the given action.
   * <p>
   * Note that this method may be called multiple times to support filtering by multiple actions.
   *
   * @param action
   *          the action
   * @return the enhanced search query
   */
  public WorkflowSearchQuery withAction(Permissions.Action action) {
    if (action == null) {
      throw new IllegalArgumentException("Action cannot be null");
    }
    this.actions.add(action.toString());
    return this;
  }

  /**
   * Returns the list of actions or an empty array if no actions have been specified.
   *
   * @return the actions
   */
  public String[] getActions() {
    return actions.toArray(new String[actions.size()]);
  }

  /**
   * Limit results to workflow instances in a specific state.
   *
   * @param state
   *          the workflow state
   * @return this query
   */
  public WorkflowSearchQuery withState(WorkflowInstance.WorkflowState state) {
    if (state != null) {
      this.state = state;
    }
    return this;
  }

  public WorkflowInstance.WorkflowState getState() {
    return state;
  }

  /**
   * Limit results to workflow instances with a specific template.
   *
   * @param template
   *          the mediapackage template
   */
  public WorkflowSearchQuery withTemplate(String template) {
    if (StringUtils.isNotBlank(template)) {
      this.template = template;
    }
    return this;
  }

  /**
   * Returns the media package title that workflow instances need to match.
   *
   * @return the media package title
   */
  public String getTemplate() {
    return template;
  }

  /**
   * Limit results to workflow instances with a specific title.
   *
   * @param title
   *          the mediapackage title
   */
  public WorkflowSearchQuery withTitle(String title) {
    if (StringUtils.isNotBlank(title)) {
      this.title = title;
    }
    return this;
  }

  /**
   * Returns the media package title that workflow instances need to match.
   *
   * @return the media package title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Limit results to workflow instances with a specific description.
   *
   * @param description
   *          the mediapackage description
   */
  public WorkflowSearchQuery withDescription(String description) {
    if (StringUtils.isNotBlank(description)) {
      this.description = description;
    }
    return this;
  }

  /**
   * Returns the media package title that workflow instances need to match.
   *
   * @return the media package title
   */
  public String getDescription() {
    return description;
  }

  /**
   * Limit results to workflow instances with a specific parentId.
   *
   * @param parentId
   *          the mediapackage parentId
   */
  public WorkflowSearchQuery withParentId(Long parentId) {
    this.parentId = parentId;
    return this;
  }

  /**
   * Returns the media package title that workflow instances need to match.
   *
   * @return the media package title
   */
  public Long getParentId() {
    return parentId;
  }

  /**
   * Limit results to workflow instances with a specific creator.
   *
   * @param creator
   *          the mediapackage creator
   */
  public WorkflowSearchQuery withCreator(String creator) {
    if (StringUtils.isNotBlank(creator)) {
      this.creator = creator;
    }
    return this;
  }

  /**
   * Returns the media package creator that workflow instances need to match.
   *
   * @return the media package creator
   */
  public String getCreator() {
    return creator;
  }

  /**
   * Limit results to workflow instances with a specific organization.
   *
   * @param organization
   *          the mediapackage organization
   */
  public WorkflowSearchQuery withOrganization(String organization) {
    if (StringUtils.isNotBlank(organization)) {
      this.organizationId = organization;
    }
    return this;
  }

  /**
   * Returns the media package creator that workflow instances need to match.
   *
   * @return the media package creator
   */
  public String getOrganization() {
    return organizationId;
  }

  /**
   * Limit results to workflow instances that are currently handling the specified operation.
   *
   * @param currentOperation
   *          the current operation
   * @return this query
   */
  public WorkflowSearchQuery withCurrentOperation(String currentOperation) {
    if (StringUtils.isNotBlank(currentOperation)) {
//      currentOperationTerms.add(new QueryTerm(currentOperation, true));
      this.currentOperation = currentOperation;
    }
    return this;
  }

  public String getCurrentOperation() {
    return currentOperation;
  }

  /**
   * Limit the results to workflow instances with a creation date starting with <code>dateCreated</code>.
   *
   * @param dateCreatedFrom
   *          the starting date
   */
  public WorkflowSearchQuery withDateCreatedFrom(Date dateCreatedFrom) {
    this.dateCreatedFrom = dateCreatedFrom;
    return this;
  }

  public WorkflowSearchQuery withDateCreatedTo(Date dateCreatedTo) {
    this.dateCreatedTo = dateCreatedTo;
    return this;
  }

  /**
   * Limit the results to workflow instances with a creation date no later than <code>dateCompleted</code>.
   *
   * @param dateCompletedFrom
   *          the ending date
   */
  public WorkflowSearchQuery withDateCompletedFrom(Date dateCompletedFrom) {
    this.dateCompletedFrom = dateCompletedFrom;
    return this;
  }

  public WorkflowSearchQuery withDateCompletedTo(Date dateCompletedTo) {
    this.dateCompletedTo = dateCompletedTo;
    return this;
  }

  /**
   * Limit results to workflow instances for a specific media package
   *
   * @param mediaPackageId
   *          the media package identifier
   */
  public WorkflowSearchQuery withMediaPackage(String mediaPackageId) {
    if (StringUtils.isNotBlank(mediaPackageId)) {
      this.mediaPackageIds.add(mediaPackageId);
    }
    return this;
  }

  /**
   * Limit results to workflow instances with a specific mediapackage contributor.
   *
   * @param mpContributor
   *          the mediapackage contributor
   */
  public WorkflowSearchQuery withMediaPackageContributor(String mpContributor) {
    if (StringUtils.isNotBlank(mpContributor)) {
      this.mpContributors.add(mpContributor);
    }
    return this;
  }

  /**
   * Limit results to workflow instances with a specific mediapackage language.
   *
   * @param mpLanguage
   *          the mediapackage language
   */
  public WorkflowSearchQuery withMediaPackageLanguage(String mpLanguage) {
    if (StringUtils.isNotBlank(mpLanguage)) {
      this.mpLanguage = mpLanguage;
    }
    return this;
  }

  /**
   * Limit results to workflow instances with a specific mediapackage license.
   *
   * @param mpLicense
   *          the mediapackage license
   */
  public WorkflowSearchQuery withMediaPackageLicense(String mpLicense) {
    if (StringUtils.isNotBlank(mpLicense)) {
      this.mpLicense = mpLicense;
    }
    return this;
  }

  /**
   * Limit results to workflow instances with a specific mediapackage title.
   *
   * @param mpTitle
   *          the mediapackage title
   */
  public WorkflowSearchQuery withMediaPackageTitle(String mpTitle) {
    if (StringUtils.isNotBlank(mpTitle)) {
      this.mpTitle = mpTitle;
    }
    return this;
  }

  /**
   * Limit results to workflow instances with a specific mediapackage subject.
   *
   * @param mpSubject
   *          the mediapackage subject
   */
  public WorkflowSearchQuery withMediaPackageSubject(String mpSubject) {
    if (StringUtils.isNotBlank(mpSubject)) {
      this.mpSubject = mpSubject;
    }
    return this;
  }

  /**
   * Limit results to workflow instances for a specific workflow
   *
   * @param seriesId
   *          the series identifier
   */
  public WorkflowSearchQuery withSeriesId(String seriesId) {
    if (StringUtils.isNotBlank(seriesId)) {
      this.seriesId = seriesId;
    }
    return this;
  }

  /**
   * Limit results to workflow instances with a specific series title
   *
   * @param seriesTitle
   *          the series title
   */
  public WorkflowSearchQuery withSeriesTitle(String seriesTitle) {
    if (StringUtils.isNotBlank(seriesTitle)) {
      this.seriesTitle = seriesTitle;
    }
    return this;
  }

  /**
   * Sort the results by the specified field, either ascending or descending.
   *
   * @param field
   *          the sort field
   * @param order
   *          whether to sort ascending (true) or descending (false)
   */
  public WorkflowSearchQuery sortBy(String field, SortCriterion.Order order) {
    withSortOrder(field, order);
    return this;
  }

  /**
   * Returns the media package series identifier that workflow instances need to match.
   *
   * @return the media package series identifier
   */
  public String getSeriesId() {
    return seriesId;
  }

  /**
   * Returns the media package title that workflow instances need to match.
   *
   * @return the media package title
   */
  public String getSeriesTitle() {
    return seriesTitle;
  }

  /**
   * Returns the media package identifier that workflow instances need to match.
   *
   * @return the media package identifier
   */
  public String[] getMediaPackageIds() {
    return mediaPackageIds.toArray(new String[mediaPackageIds.size()]);
  }

  /**
   * Returns the selection start date for workflow instances.
   *
   * @return the start date
   */
  public Date getDateCreatedTo() {
    return dateCreatedTo;
  }
  public Date getDateCreatedFrom() {
    return dateCreatedFrom;
  }

  /**
   * Returns the selection end date for workflow instances.
   *
   * @return the end date
   */
  public Date getDateCompletedFrom() {
    return dateCompletedFrom;
  }
  public Date getDateCompletedTo() {
    return dateCompletedTo;
  }

  /**
   * Returns the media package contributor that workflow instances need to match.
   *
   * @return the media package contributor
   */
  public String[] getMediaPackageContributor() {
    return mpContributors.toArray(new String[mpContributors.size()]);
  }

  /**
   * Returns the media package language that workflow instances need to match.
   *
   * @return the media package language
   */
  public String getMediaPackageLanguage() {
    return mpLanguage;
  }

  /**
   * Returns the media package license that workflow instances need to match.
   *
   * @return the media package license
   */
  public String getMediaPackageLicense() {
    return mpLicense;
  }

  /**
   * Returns the media package subject that workflow instances need to match.
   *
   * @return the media package subject
   */
  public String getMediaPackageSubject() {
    return mpSubject;
  }

  /**
   * A tuple of a query value and whether this search term should be included or excluded from the search results.
   */
  public static class QueryTerm {

    private String value = null;
    private boolean include = false;

    /** Constructs a new query term */
    public QueryTerm(String value, boolean include) {
      this.value = value;
      this.include = include;
    }

    /**
     * @return the value
     */
    public String getValue() {
      return value;
    }

    /**
     * @return whether this query term is to be excluded
     */
    public boolean isInclude() {
      return include;
    }
  }

}
