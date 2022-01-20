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
import org.opencastproject.security.api.User;
import org.opencastproject.util.requests.SortCriterion;
import org.opencastproject.workflow.api.WorkflowInstance;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class WorkflowSearchQuery extends AbstractSearchQuery {

  protected List<String> identifiers = new ArrayList<String>();
  private User user = null;
//  private Set<String> actions = new HashSet<String>();
  private WorkflowInstance.WorkflowState state = null;
  private String template = null;
  private String title = null;
  private String description = null;
  private Long parentId = null;
  private String creator = null;
  private String organizationId = null;
  private String currentOperation = null;
  private Date dateCreated = null;
  private Date dateCompleted = null;
  private String mediaPackageId = null;
  private String mpContributor = null;
  private String mpLanguage = null;
  private String mpLicense = null;
  private String mpTitle = null;
  private String mpSubject = null;
  private String seriesId = null;
  private String seriesTitle = null;

  /**
   * The list of current operation terms that have been added to this query.
   */
  protected List<QueryTerm> currentOperationTerms = new ArrayList<QueryTerm>();

  /**
   * The list of state terms that have been added to this query.
   */
  protected List<QueryTerm> stateTerms = new ArrayList<QueryTerm>();

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
//    this.actions.add(Permissions.Action.READ.toString());
    if (!user.getOrganization().getId().equals(organization)) {
      throw new IllegalStateException("User's organization must match search organization");
    }
  }

  /**
   * Returns the list of workflow identifiers or an empty array if no identifiers have been specified.
   *
   * @return the identifiers
   */
  public String[] getIdentifier() {
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

//  /**
//   * Filter the workflow without any action checked.
//   *
//   * @return the enhanced search query
//   */
//  public WorkflowSearchQuery withoutActions() {
//    this.actions.clear();
//    return this;
//  }
//
//  /**
//   * Filter the workflow with the given action.
//   * <p>
//   * Note that this method may be called multiple times to support filtering by multiple actions.
//   *
//   * @param action
//   *          the action
//   * @return the enhanced search query
//   */
//  public WorkflowSearchQuery withAction(Permissions.Action action) {
//    if (action == null) {
//      throw new IllegalArgumentException("Action cannot be null");
//    }
//    this.actions.add(action.toString());
//    return this;
//  }
//
//  /**
//   * Returns the list of actions or an empty array if no actions have been specified.
//   *
//   * @return the actions
//   */
//  public String[] getActions() {
//    return actions.toArray(new String[actions.size()]);
//  }

  /**
   * Limit results to workflow instances with active states.
   *
   * @return Reference to itself
   */
  public WorkflowSearchQuery isActive() {
    for (WorkflowInstance.WorkflowState state: WorkflowInstance.WorkflowState.values()) {
      if (!state.isTerminated()) {
        stateTerms.add(new QueryTerm(state.toString(), true));
      }
    }
    return this;
  }

//  /** Include a limit for the number of items to return in the result */
//  public WorkflowSearchQuery withCount(long count) {
//    this.count = count;
//    return this;
//  }
//
//  /** Include a paging offset for the items returned. Will delete any {@link #withStartIndex(long)} settings. */
//  public WorkflowSearchQuery withStartPage(long startPage) {
//    this.startPage = startPage;
//    this.startIndex = 0;
//    return this;
//  }
//
//  /** Include a start index for the items returned. Will delete any {@link #withStartPage(long)} settings. */
//  public WorkflowSearchQuery withStartIndex(long index) {
//    this.startIndex = index;
//    this.startPage = 0;
//    return this;
//  }
//
//  /** Limit results to workflow instances matching a free text search */
//  public WorkflowSearchQuery withText(String text) {
//    if (StringUtils.isNotBlank(text))
//      this.text = text;
//    return this;
//  }

  /**
   * Limit results to workflow instances in a specific state. This method overrides and will be overridden by future
   * calls to {@link #withoutState(WorkflowInstance.WorkflowState)}
   *
   * @param state
   *          the workflow state
   * @return this query
   */
  public WorkflowSearchQuery withState(WorkflowInstance.WorkflowState state) {
    if (state != null) {
      stateTerms.add(new QueryTerm(state.toString(), true));
    }
    return this;
  }

  /**
   * Limit results to workflow instances not in a specific state. This method overrides and will be overridden by future
   * calls to {@link #withState(WorkflowInstance.WorkflowState)}
   *
   * @param state
   *          the workflow state
   * @return this query
   */
  public WorkflowSearchQuery withoutState(WorkflowInstance.WorkflowState state) {
    if (state != null) {
      stateTerms.add(new QueryTerm(state.toString(), false));
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
   * Limit results to workflow instances that are currently handling the specified operation. This method overrides and
   * will be overridden by future calls to {@link #withoutCurrentOperation(String)}
   *
   * @param currentOperation
   *          the current operation
   * @return this query
   */
  public WorkflowSearchQuery withCurrentOperation(String currentOperation) {
    if (StringUtils.isNotBlank(currentOperation)) {
      currentOperationTerms.add(new QueryTerm(currentOperation, true));
    }
    return this;
  }

  /**
   * Limit results to workflow instances to those that are not currently in the specified operation. This method
   * overrides and will be overridden by future calls to {@link #withCurrentOperation(String)}
   *
   * @param currentOperation
   *          the current operation
   * @return this query
   */
  public WorkflowSearchQuery withoutCurrentOperation(String currentOperation) {
    if (StringUtils.isNotBlank(currentOperation)) {
      currentOperationTerms.add(new QueryTerm(currentOperation, false));
    }
    return this;
  }

  public String getCurrentOperation() {
    return currentOperation;
  }

  /**
   * Returns the list of current operations that workflow instances need to match.
   *
   * @return the current operations
   */
  public List<QueryTerm> getCurrentOperations() {
    return currentOperationTerms;
  }

  /**
   * Limit the results to workflow instances with a creation date starting with <code>dateCreated</code>.
   *
   * @param dateCreated
   *          the starting date
   */
  public WorkflowSearchQuery withDateCreated(Date dateCreated) {
    this.dateCreated = dateCreated;
    return this;
  }

  /**
   * Limit the results to workflow instances with a creation date no later than <code>dateCompleted</code>.
   *
   * @param dateCompleted
   *          the ending date
   */
  public WorkflowSearchQuery withDateCompleted(Date dateCompleted) {
    this.dateCompleted = dateCompleted;
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
      this.mediaPackageId = mediaPackageId;
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
      this.mpContributor = mpContributor;
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

//  /**
//   * Returns the number of result items to return.
//   *
//   * @return the number of result items
//   */
//  public long getCount() {
//    return count;
//  }
//
//  /**
//   * Returns the number of the first page within the full result set.
//   *
//   * @return the first page
//   */
//  public long getStartPage() {
//    return startPage;
//  }
//
//  /** Returns the start index within the full result set. */
//  public long getStartIndex() {
//    return startIndex;
//  }
//
//  /**
//   * Returns the text that workflow instances need to match by any metadata field (fulltext).
//   *
//   * @return the text
//   */
//  public String getText() {
//    return text;
//  }

  /**
   * Returns the list of states that workflow instances need to match.
   *
   * @return the states
   */
  public List<QueryTerm> getStates() {
    return stateTerms;
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
  public String getMediaPackageId() {
    return mediaPackageId;
  }

  /**
   * Returns the selection start date for workflow instances.
   *
   * @return the start date
   */
  public Date getDateCreated() {
    return dateCreated;
  }

  /**
   * Returns the selection end date for workflow instances.
   *
   * @return the end date
   */
  public Date getDateCompleted() {
    return dateCompleted;
  }

  /**
   * Returns the media package contributor that workflow instances need to match.
   *
   * @return the media package contributor
   */
  public String getMediaPackageContributor() {
    return mpContributor;
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
   * Returns the search terms for operations. A term can either mean to include or exclude the specified operation.
   *
   * @return the operation search terms
   */
  public List<QueryTerm> getCurrentOperationTerms() {
    return currentOperationTerms;
  }

  /**
   * Returns the search terms for workflow states. A term can either mean to include or exclude the specified state.
   *
   * @return the state search terms
   */
  public List<QueryTerm> getStateTerms() {
    return stateTerms;
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
