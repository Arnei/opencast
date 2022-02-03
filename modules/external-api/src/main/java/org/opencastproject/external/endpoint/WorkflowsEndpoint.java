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
package org.opencastproject.external.endpoint;

import static com.entwinemedia.fn.data.json.Jsons.BLANK;
import static com.entwinemedia.fn.data.json.Jsons.ZERO;
import static com.entwinemedia.fn.data.json.Jsons.arr;
import static com.entwinemedia.fn.data.json.Jsons.f;
import static com.entwinemedia.fn.data.json.Jsons.obj;
import static com.entwinemedia.fn.data.json.Jsons.v;
import static java.time.ZoneOffset.UTC;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.opencastproject.util.RestUtil.getEndpointUrl;
import static org.opencastproject.util.doc.rest.RestParameter.Type.BOOLEAN;
import static org.opencastproject.util.doc.rest.RestParameter.Type.INTEGER;
import static org.opencastproject.util.doc.rest.RestParameter.Type.STRING;

import org.opencastproject.elasticsearch.api.SearchIndexException;
import org.opencastproject.elasticsearch.api.SearchResult;
import org.opencastproject.elasticsearch.api.SearchResultItem;
import org.opencastproject.elasticsearch.index.ElasticsearchIndex;
import org.opencastproject.elasticsearch.index.objects.event.Event;
import org.opencastproject.elasticsearch.index.objects.event.EventSearchQuery;
import org.opencastproject.elasticsearch.index.objects.workflow.Workflow;
import org.opencastproject.elasticsearch.index.objects.workflow.WorkflowIndexSchema;
import org.opencastproject.elasticsearch.index.objects.workflow.WorkflowSearchQuery;
import org.opencastproject.external.common.ApiMediaType;
import org.opencastproject.external.common.ApiResponses;
import org.opencastproject.external.common.ApiVersion;
import org.opencastproject.index.service.api.IndexService;
import org.opencastproject.index.service.util.RestUtils;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.rest.RestConstants;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.systems.OpencastConstants;
import org.opencastproject.util.DateTimeSupport;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.RestUtil;
import org.opencastproject.util.UrlSupport;
import org.opencastproject.util.data.Option;
import org.opencastproject.util.data.Tuple;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;
import org.opencastproject.util.requests.SortCriterion;
import org.opencastproject.workflow.api.RetryStrategy;
import org.opencastproject.workflow.api.WorkflowDefinition;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workflow.api.WorkflowSetImpl;
import org.opencastproject.workflow.api.WorkflowStateException;

import com.entwinemedia.fn.data.Opt;
import com.entwinemedia.fn.data.json.Field;
import com.entwinemedia.fn.data.json.JValue;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
@Produces({ ApiMediaType.JSON, ApiMediaType.VERSION_1_1_0, ApiMediaType.VERSION_1_2_0, ApiMediaType.VERSION_1_3_0,
            ApiMediaType.VERSION_1_4_0, ApiMediaType.VERSION_1_5_0, ApiMediaType.VERSION_1_6_0,
            ApiMediaType.VERSION_1_7_0 })
@RestService(name = "externalapiworkflowinstances", title = "External API Workflow Instances Service", notes = {},
             abstractText = "Provides resources and operations related to the workflow instances")
@Component(
    immediate = true,
    service = WorkflowsEndpoint.class,
    property = {
        "service.description=External API - Workflow Instances Endpoint",
        "opencast.service.type=org.opencastproject.external.workflows.instances",
        "opencast.service.path=/api/workflows"
    }
)
public class WorkflowsEndpoint {
  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(WorkflowsEndpoint.class);

  /** The default number of results returned */
  private static final int DEFAULT_LIMIT = 20;
  /** The constant used to negate a querystring parameter. This is only supported on some parameters. */
  public static final String NEGATE_PREFIX = "-";
  /** The constant used to switch the direction of the sorting querystring parameter. */
  public static final String DESCENDING_SUFFIX = "_DESC";

  /** Base URL of this endpoint */
  protected String endpointBaseUrl;

  /* OSGi service references */
  private WorkflowService workflowService;
  private ElasticsearchIndex elasticsearchIndex;
  private SecurityService securityService;
  private IndexService indexService;

  /** OSGi DI */
  @Reference(name = "workflowService")
  public void setWorkflowService(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  /** OSGi DI */
  @Reference(name = "ElasticsearchIndex")
  public void setElasticsearchIndex(ElasticsearchIndex elasticsearchIndex) {
    this.elasticsearchIndex = elasticsearchIndex;
  }

  /** OSGi DI */
  @Reference(name = "SecurityService")
  void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /** OSGi DI */
  @Reference(name = "IndexService")
  public void setIndexService(IndexService indexService) {
    this.indexService = indexService;
  }

  /**
   * OSGi activation method
   */
  @Activate
  void activate(ComponentContext cc) {
    logger.info("Activating External API - Workflow Instances Endpoint");

    final Tuple<String, String> endpointUrl = getEndpointUrl(cc, OpencastConstants.EXTERNAL_API_URL_ORG_PROPERTY,
            RestConstants.SERVICE_PATH_PROPERTY);
    endpointBaseUrl = UrlSupport.concat(endpointUrl.getA(), endpointUrl.getB());
    logger.debug("Configured service endpoint is {}", endpointBaseUrl);
  }

  @POST
  @Path("")
  @RestQuery(name = "createworkflowinstance", description = "Creates a workflow instance.", returnDescription = "", restParameters = {
          @RestParameter(name = "event_identifier", description = "The event identifier this workflow should run against", isRequired = true, type = STRING),
          @RestParameter(name = "workflow_definition_identifier", description = "The identifier of the workflow definition to use", isRequired = true, type = STRING),
          @RestParameter(name = "configuration", description = "The optional configuration for this workflow", isRequired = false, type = STRING),
          @RestParameter(name = "withoperations", description = "Whether the workflow operations should be included in the response", isRequired = false, type = BOOLEAN),
          @RestParameter(name = "withconfiguration", description = "Whether the workflow configuration should be included in the response", isRequired = false, type = BOOLEAN), }, responses = {
          @RestResponse(description = "A new workflow is created and its identifier is returned in the Location header.", responseCode = HttpServletResponse.SC_CREATED),
          @RestResponse(description = "The request is invalid or inconsistent.", responseCode = HttpServletResponse.SC_BAD_REQUEST),
          @RestResponse(description = "The event or workflow definition could not be found.", responseCode = HttpServletResponse.SC_NOT_FOUND) })
  public Response createWorkflowInstance(@HeaderParam("Accept") String acceptHeader,
          @FormParam("event_identifier") String eventId,
          @FormParam("workflow_definition_identifier") String workflowDefinitionIdentifier,
          @FormParam("configuration") String configuration, @QueryParam("withoperations") boolean withOperations,
          @QueryParam("withconfiguration") boolean withConfiguration) {
    if (isBlank(eventId)) {
      return RestUtil.R.badRequest("Required parameter 'event_identifier' is missing or invalid");
    }

    if (isBlank(workflowDefinitionIdentifier)) {
      return RestUtil.R.badRequest("Required parameter 'workflow_definition_identifier' is missing or invalid");
    }

    try {
      // Media Package
      Opt<Event> event = indexService.getEvent(eventId, elasticsearchIndex);
      if (event.isNone()) {
        return ApiResponses.notFound("Cannot find an event with id '%s'.", eventId);
      }
      MediaPackage mp = indexService.getEventMediapackage(event.get());

      // Workflow definition
      WorkflowDefinition wd;
      try {
        wd = workflowService.getWorkflowDefinitionById(workflowDefinitionIdentifier);
      } catch (NotFoundException e) {
        return ApiResponses.notFound("Cannot find a workflow definition with id '%s'.", workflowDefinitionIdentifier);
      }

      // Configuration
      Map<String, String> properties = new HashMap<>();
      if (isNoneBlank(configuration)) {
        JSONParser parser = new JSONParser();
        try {
          properties.putAll((JSONObject) parser.parse(configuration));
        } catch (ParseException e) {
          return RestUtil.R.badRequest("Passed parameter 'configuration' is invalid JSON.");
        }
      }

      // Start workflow
      WorkflowInstance wi = workflowService.start(wd, mp, null, properties);
      return ApiResponses.Json.created(acceptHeader, URI.create(getWorkflowUrl(wi.getId())),
              workflowInstanceToJSON(wi, withOperations, withConfiguration));
    } catch (IllegalStateException e) {
      final ApiVersion requestedVersion = ApiMediaType.parse(acceptHeader).getVersion();
      return ApiResponses.Json.conflict(requestedVersion, obj(f("message", v(e.getMessage(), BLANK))));
    } catch (Exception e) {
      logger.error("Could not create workflow instances", e);
      return ApiResponses.serverError("Could not create workflow instances, reason: '%s'", e.getMessage());
    }
  }

  @GET
  @Path("{workflowInstanceId}")
  @RestQuery(name = "getworkflowinstance", description = "Returns a single workflow instance.", returnDescription = "", pathParameters = {
          @RestParameter(name = "workflowInstanceId", description = "The workflow instance id", isRequired = true, type = INTEGER) }, restParameters = {
          @RestParameter(name = "withoperations", description = "Whether the workflow operations should be included in the response", isRequired = false, type = BOOLEAN),
          @RestParameter(name = "withconfiguration", description = "Whether the workflow configuration should be included in the response", isRequired = false, type = BOOLEAN) }, responses = {
          @RestResponse(description = "The workflow instance is returned.", responseCode = HttpServletResponse.SC_OK),
          @RestResponse(description = "The user doesn't have the rights to make this request.", responseCode = HttpServletResponse.SC_FORBIDDEN),
          @RestResponse(description = "The specified workflow instance does not exist.", responseCode = HttpServletResponse.SC_NOT_FOUND) })
  public Response getWorkflowInstance(@HeaderParam("Accept") String acceptHeader,
          @PathParam("workflowInstanceId") Long id, @QueryParam("withoperations") boolean withOperations,
          @QueryParam("withconfiguration") boolean withConfiguration) {
    WorkflowInstance wi;
    try {
      wi = workflowService.getWorkflowById(id);
    } catch (NotFoundException e) {
      return ApiResponses.notFound("Cannot find workflow instance with id '%d'.", id);
    } catch (UnauthorizedException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception e) {
      logger.error("The workflow service was not able to get the workflow instance", e);
      return ApiResponses.serverError("Could not retrieve workflow instance, reason: '%s'", e.getMessage());
    }

    return ApiResponses.Json.ok(acceptHeader, workflowInstanceToJSON(wi, withOperations, withConfiguration));
  }

  @GET
  @Produces(MediaType.TEXT_XML)
  @Path("instances.xml")
  @RestQuery(name = "workflowsasxml", description = "List all workflow instances matching the query parameters", returnDescription = "An XML representation of the set of workflows matching these query parameters", restParameters = {
          @RestParameter(name = "state", isRequired = false, description = "Filter results by workflows' current state", type = STRING),
          @RestParameter(name = "template", isRequired = false, description = "Filter results by workflows' template", type = STRING),
          @RestParameter(name = "title", isRequired = false, description = "Filter results by workflows' title", type = STRING),
          @RestParameter(name = "description", isRequired = false, description = "Filter results by workflows' description", type = STRING),
          @RestParameter(name = "creator", isRequired = false, description = "Filter results by workflows' creator", type = STRING),
          @RestParameter(name = "op", isRequired = false, description = "Filter results by workflows' current operation.", type = STRING),
          @RestParameter(name = "dateCreatedFrom", isRequired = false, description = "Filter results by workflow start date.", type = STRING),
          @RestParameter(name = "dateCreatedTo", isRequired = false, description = "Filter results by workflow start date.", type = STRING),
          @RestParameter(name = "dateCompletedFrom", isRequired = false, description = "Filter results by workflow end date.", type = STRING),
          @RestParameter(name = "dateCompletedTo", isRequired = false, description = "Filter results by workflow end date.", type = STRING),
          @RestParameter(name = "mp", isRequired = false, description = "Filter results by mediapackage identifier.", type = STRING),
          @RestParameter(name = "mpContributors", isRequired = false, description = "Filter results by the mediapackage's contributor", type = STRING),
          @RestParameter(name = "mpLanguage", isRequired = false, description = "Filter results by mediapackage's language.", type = STRING),
          @RestParameter(name = "mpLicense", isRequired = false, description = "Filter results by mediapackage's license.", type = STRING),
          @RestParameter(name = "mpTitle", isRequired = false, description = "Filter results by mediapackage's title.", type = STRING),
          @RestParameter(name = "mpSubject", isRequired = false, description = "Filter results by mediapackage's subject.", type = STRING),
          @RestParameter(name = "seriesId", isRequired = false, description = "Filter results by series identifier", type = STRING),
          @RestParameter(name = "seriesTitle", isRequired = false, description = "Filter results by series title", type = STRING),
          @RestParameter(name = "q", isRequired = false, description = "Filter results by free text query", type = STRING),
          @RestParameter(name = "sort", isRequired = false, description = "Sort the results based upon a list of comma seperated sorting criteria."
                  + "In the comma seperated list each type of sorting is specified as a pair such as: :ASC or :DESC."
                  + "Adding the suffix ASC or DESC sets the order as ascending or descending order and is mandatory.", type = STRING),
          @RestParameter(name = "offset", isRequired = false, description = "Return results after this offset", type = INTEGER),
          @RestParameter(name = "limit", isRequired = false, description = "The number of results to return. Default is " + DEFAULT_LIMIT, type = INTEGER),
          @RestParameter(name = "compact", isRequired = false, description = "Whether to return a compact version of "
                  + "the workflow instance, with mediapackage elements, workflow and workflow operation configurations and "
                  + "non-current operations removed.", type = BOOLEAN)},
          responses = {
                  @RestResponse(responseCode = SC_OK, description = "An XML representation of the workflow set."),
                  @RestResponse(responseCode = SC_BAD_REQUEST, description = "Invalid data was provided in the request.") })
  // CHECKSTYLE:OFF
  // The number of method parameters is too large for checkstyle's taste, but we need to handle many potential query
  // parameters. CXF provides a bean approach to accepting many parameters, but it is not part of the JAX-RS spec.
  // So for now, we disable checkstyle here.
  public Response getWorkflowsAsXml(@QueryParam("state") String state,
          @QueryParam("template") String template, @QueryParam("title") String title,
          @QueryParam("description") String description, @QueryParam("creator") String creator,
          @QueryParam("op") String currentOperation,
          @QueryParam("dateCreatedFrom") String dateCreatedFrom, @QueryParam("dateCreatedTo") String dateCreatedTo,
          @QueryParam("dateCompletedFrom") String dateCompletedFrom, @QueryParam("dateCompletedTo") String dateCompletedTo,
          @QueryParam("mp") String mediapackageId,
          @QueryParam("mpContributors") List<String> mpContributors, @QueryParam("mpLanguage") String mpLanguage,
          @QueryParam("mpLicense") String mpLicense, @QueryParam("mpTitle") String mpTitle,
          @QueryParam("mpSubject") String mpSubject,
          @QueryParam("seriesId") String seriesId,
          @QueryParam("seriesTitle") String seriesTitle,
          @QueryParam("q") String text, @QueryParam("sort") String sort,
          @QueryParam("offset") int offset, @QueryParam("limit") int limit, @QueryParam("compact") boolean compact)
          throws Exception {
    // CHECKSTYLE:ON
    Option<String> optSort = Option.option(trimToNull(sort));

    // If there were any event parameters specified, get events before workflows
    // The ids of the events can then be used in the workflow query
    List<String> limitingMediaPackageIds = new ArrayList<>();
    if (mpContributors != null || mpLanguage != null || mpLicense != null || mpTitle != null || mpSubject != null) {
      EventSearchQuery eventQuery = new EventSearchQuery(securityService.getOrganization().getId(),
              securityService.getUser());
      for (String contributor : mpContributors) {
        if (StringUtils.isNotEmpty(contributor)) {
          eventQuery.withContributor(contributor);
        }
      }
      if (StringUtils.isNotEmpty(mpLanguage)) {
        eventQuery.withLanguage(mpLanguage);
      }
      if (StringUtils.isNotEmpty(mpTitle)) {
        eventQuery.withTitle(mpTitle);
      }
      if (StringUtils.isNotEmpty(mpSubject)) {
        eventQuery.withSubject(mpSubject);
      }
      if (StringUtils.isNotEmpty(seriesTitle)) {
        eventQuery.withSeriesName(seriesTitle);
      }

      SearchResult<Event> results = null;
      try {
        results = elasticsearchIndex.getByQuery(eventQuery);
      } catch (SearchIndexException e) {
        logger.error("The External Search Index was not able to get the events list", e);
        throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
      }

      // No mediapackage means no workflows either
      if (results.getHitCount() == 0) {
        return Response.ok(new WorkflowSetImpl()).build();
      }

      // Prepare to add to the workflow query later
      for (SearchResultItem<Event> resultItem : results.getItems()) {
        final Event event = resultItem.getSource();
        limitingMediaPackageIds.add(event.getIdentifier());
      }
    }

    WorkflowSearchQuery q = new WorkflowSearchQuery(securityService.getOrganization().getId(),
            securityService.getUser());
    q.withLimit(limit < 1 ? DEFAULT_LIMIT : limit);
    if (offset > 0) {
      q.withOffset(offset);
    }
    if (!StringUtils.isBlank(text)) {
      q.withText(text);
    }
    try {
      if (StringUtils.isNotEmpty(state)) {
        q.withState(WorkflowInstance.WorkflowState.valueOf(state.toUpperCase()));
      }
    } catch (IllegalArgumentException e) {
      logger.debug("Unknown workflow state.", e);
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }
    q.withTemplate(template);
    q.withTitle(title);
    q.withDescription(description);
    q.withCreator(creator);
    q.withCurrentOperation(currentOperation);
    try {
      if (StringUtils.isNotEmpty(dateCreatedFrom) && StringUtils.isNotEmpty(dateCreatedTo)) {
        q.withDateCreatedFrom(new Date(DateTimeSupport.fromUTC(dateCreatedFrom)));
        q.withDateCreatedTo(new Date(DateTimeSupport.fromUTC(dateCreatedTo)));
      }
      if (StringUtils.isNotEmpty(dateCompletedFrom) && StringUtils.isNotEmpty(dateCreatedTo)) {
        q.withDateCompletedFrom(new Date(DateTimeSupport.fromUTC(dateCompletedFrom)));
        q.withDateCompletedTo(new Date(DateTimeSupport.fromUTC(dateCompletedTo)));
      }
    } catch (Exception e) {
      logger.warn("Unable to parse date parameter, {}", e);
      throw new IllegalArgumentException("Unable to parse date parameter");
    }
    for (String limitingMediaPackage : limitingMediaPackageIds) {
      q.withMediaPackage(limitingMediaPackage);
    }
    q.withMediaPackage(mediapackageId);
    q.withSeriesId(seriesId);

    if (optSort.isSome()) {
      Set<SortCriterion> sortCriteria = RestUtils.parseSortQueryParameter(optSort.get());
      for (SortCriterion criterion : sortCriteria) {

        switch (criterion.getFieldName()) {
          case WorkflowIndexSchema.ID:
          case WorkflowIndexSchema.STATE:
          case WorkflowIndexSchema.TEMPLATE:
          case WorkflowIndexSchema.TITLE:
          case WorkflowIndexSchema.DESCRIPTION:
          case WorkflowIndexSchema.PARENT_ID:
          case WorkflowIndexSchema.CREATOR_NAME:
          case WorkflowIndexSchema.ORGANIZATION_ID:
          case WorkflowIndexSchema.CURRENT_OPERATION:
          case WorkflowIndexSchema.DATE_CREATED:
          case WorkflowIndexSchema.DATE_COMPLETED:
          case WorkflowIndexSchema.MEDIAPACKAGE:
          case WorkflowIndexSchema.SERIES:
            q.sortBy(criterion.getFieldName(), criterion.getOrder());
            break;
          default:
            logger.info("Unknown sort criteria {}", criterion.getFieldName());
            return Response.status(SC_BAD_REQUEST).build();
        }
      }
    }

    SearchResult<Workflow> results = null;
    try {
      results = elasticsearchIndex.getByQuery(q);
    } catch (SearchIndexException e) {
      logger.error("The External Search Index was not able to get the events list", e);
      throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
    }

    // TODO: Either return the documents from the ElasticsearchIndex
    //  Or get the workflows from the workflowService and return those instead
    WorkflowSetImpl workflowSet = new WorkflowSetImpl();
    for (SearchResultItem<Workflow> resultItem : results.getItems()) {
      final Workflow workflow = resultItem.getSource();
      workflowSet.addItem(workflowService.getWorkflowById(workflow.getIdentifier()));
    }
    // TODO: Fix search metadata in return value (e.g. limit, offset)

    // Marshalling of a full workflow takes a long time. Therefore, we strip everything that's not needed.
    if (compact) {
      WorkflowSetImpl compactSet = new WorkflowSetImpl();
      for (WorkflowInstance instance : workflowSet.getItems()) {

        // Remove all operations but the current one
        WorkflowOperationInstance currentOp = instance.getCurrentOperation();
        List<WorkflowOperationInstance> operations = instance.getOperations();
        operations.clear(); // instance.getOperations() is a copy
        if (currentOp != null) {
          for (String key : currentOp.getConfigurationKeys()) {
            currentOp.removeConfiguration(key);
          }
          operations.add(currentOp);
        }
        instance.setOperations(operations);

        // Remove all mediapackage elements (but keep the duration)
        MediaPackage mediaPackage = instance.getMediaPackage();
        Long duration = instance.getMediaPackage().getDuration();
        for (MediaPackageElement element : mediaPackage.elements()) {
          mediaPackage.remove(element);
        }
        mediaPackage.setDuration(duration);
        instance.setMediaPackage(mediaPackage);

        compactSet.addItem(instance);
      }
      return Response.ok(compactSet).build();
    }

    return Response.ok(workflowSet).build();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("instances.json")
  @RestQuery(name = "workflowsasjson", description = "List all workflow instances matching the query parameters", returnDescription = "A JSON representation of the set of workflows matching these query parameters", restParameters = {
          @RestParameter(name = "state", isRequired = false, description = "Filter results by workflows' current state", type = STRING),
          @RestParameter(name = "template", isRequired = false, description = "Filter results by workflows' template", type = STRING),
          @RestParameter(name = "title", isRequired = false, description = "Filter results by workflows' title", type = STRING),
          @RestParameter(name = "description", isRequired = false, description = "Filter results by workflows' description", type = STRING),
          @RestParameter(name = "creator", isRequired = false, description = "Filter results by workflows' creator", type = STRING),
          @RestParameter(name = "op", isRequired = false, description = "Filter results by workflows' current operation.", type = STRING),
          @RestParameter(name = "dateCreatedFrom", isRequired = false, description = "Filter results by workflow start date.", type = STRING),
          @RestParameter(name = "dateCreatedTo", isRequired = false, description = "Filter results by workflow start date.", type = STRING),
          @RestParameter(name = "dateCompletedFrom", isRequired = false, description = "Filter results by workflow end date.", type = STRING),
          @RestParameter(name = "dateCompletedTo", isRequired = false, description = "Filter results by workflow end date.", type = STRING),
          @RestParameter(name = "mp", isRequired = false, description = "Filter results by mediapackage identifier.", type = STRING),
          @RestParameter(name = "mpContributors", isRequired = false, description = "Filter results by the mediapackage's contributor", type = STRING),
          @RestParameter(name = "mpLanguage", isRequired = false, description = "Filter results by mediapackage's language.", type = STRING),
          @RestParameter(name = "mpLicense", isRequired = false, description = "Filter results by mediapackage's license.", type = STRING),
          @RestParameter(name = "mpTitle", isRequired = false, description = "Filter results by mediapackage's title.", type = STRING),
          @RestParameter(name = "mpSubject", isRequired = false, description = "Filter results by mediapackage's subject.", type = STRING),
          @RestParameter(name = "seriesId", isRequired = false, description = "Filter results by series identifier", type = STRING),
          @RestParameter(name = "seriesTitle", isRequired = false, description = "Filter results by series title", type = STRING),
          @RestParameter(name = "q", isRequired = false, description = "Filter results by free text query", type = STRING),
          @RestParameter(name = "sort", isRequired = false, description = "Sort the results based upon a list of comma seperated sorting criteria."
                  + "In the comma seperated list each type of sorting is specified as a pair such as: :ASC or :DESC."
                  + "Adding the suffix ASC or DESC sets the order as ascending or descending order and is mandatory.", type = STRING),
          @RestParameter(name = "offset", isRequired = false, description = "Return results after this offset", type = INTEGER),
          @RestParameter(name = "limit", isRequired = false, description = "The number of results to return. Default is " + DEFAULT_LIMIT, type = INTEGER),
          @RestParameter(name = "compact", isRequired = false, description = "Whether to return a compact version of "
                  + "the workflow instance, with mediapackage elements, workflow and workflow operation configurations and "
                  + "non-current operations removed.", type = BOOLEAN)},
          responses = {
                  @RestResponse(responseCode = SC_OK, description = "A JSON representation of the workflow set."),
                  @RestResponse(responseCode = SC_BAD_REQUEST, description = "Invalid data was provided in the request.") })
  // CHECKSTYLE:OFF
  public Response getWorkflowsAsJson(@QueryParam("state") String state,
          @QueryParam("template") String template, @QueryParam("title") String title,
          @QueryParam("description") String description, @QueryParam("creator") String creator,
          @QueryParam("op") String currentOperation,
          @QueryParam("dateCreatedFrom") String dateCreatedFrom, @QueryParam("dateCreatedTo") String dateCreatedTo,
          @QueryParam("dateCompletedFrom") String dateCompletedFrom, @QueryParam("dateCompletedTo") String dateCompletedTo,
          @QueryParam("mp") String mediapackageId,
          @QueryParam("mpContributors") List<String> mpContributors, @QueryParam("mpLanguage") String mpLanguage,
          @QueryParam("mpLicense") String mpLicense, @QueryParam("mpTitle") String mpTitle,
          @QueryParam("mpSubject") String mpSubject,
          @QueryParam("seriesId") String seriesId,
          @QueryParam("seriesTitle") String seriesTitle,
          @QueryParam("q") String text, @QueryParam("sort") String sort,
          @QueryParam("offset") int offset, @QueryParam("limit") int limit, @QueryParam("compact") boolean compact)
          throws Exception {
    // CHECKSTYLE:ON
    return getWorkflowsAsXml(state, template, title, description, creator, currentOperation,
            dateCreatedFrom, dateCreatedTo, dateCompletedFrom, dateCompletedTo,
            mediapackageId,
            mpContributors, mpLanguage, mpLicense, mpTitle, mpSubject,
            seriesId,
            seriesTitle,
            text,
            sort, offset, limit, compact);
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("count")
  @RestQuery(name = "count", description = "Returns the number of workflow instances in a specific state and operation", returnDescription = "Returns the number of workflow instances in a specific state and operation", restParameters = {
          @RestParameter(name = "state", isRequired = false, description = "The workflow state", type = STRING),
          @RestParameter(name = "operation", isRequired = false, description = "The current operation", type = STRING) }, responses = { @RestResponse(responseCode = SC_OK, description = "The number of workflow instances.") })
  public Response getCount(@QueryParam("state") String state,
          @QueryParam("operation") String operation) {
    WorkflowSearchQuery q = new WorkflowSearchQuery(securityService.getOrganization().getId(),
            securityService.getUser());
    try {
      if (StringUtils.isNotEmpty(state)) {
        q.withState(WorkflowInstance.WorkflowState.valueOf(state.toUpperCase()));
      }
    } catch (IllegalArgumentException e) {
      logger.debug("Unknown workflow state.", e);
      throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }
    q.withCurrentOperation(operation);

    SearchResult<Workflow> results = null;
    try {
      results = elasticsearchIndex.getByQuery(q);
    } catch (SearchIndexException e) {
      logger.error("The External Search Index was not able to get the events list", e);
      throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
    }

    return Response.ok(results.getHitCount()).build();
  }

  @PUT
  @Path("{workflowInstanceId}")
  @RestQuery(name = "updateworkflowinstance", description = "Creates a workflow instance.", returnDescription = "", pathParameters = {
          @RestParameter(name = "workflowInstanceId", description = "The workflow instance id", isRequired = true, type = INTEGER) }, restParameters = {
          @RestParameter(name = "configuration", description = "The optional configuration for this workflow", isRequired = false, type = STRING),
          @RestParameter(name = "state", description = "The optional state transition for this workflow", isRequired = false, type = STRING),
          @RestParameter(name = "withoperations", description = "Whether the workflow operations should be included in the response", isRequired = false, type = BOOLEAN),
          @RestParameter(name = "withconfiguration", description = "Whether the workflow configuration should be included in the response", isRequired = false, type = BOOLEAN), }, responses = {
          @RestResponse(description = "The workflow instance is updated.", responseCode = HttpServletResponse.SC_OK),
          @RestResponse(description = "The request is invalid or inconsistent.", responseCode = HttpServletResponse.SC_BAD_REQUEST),
          @RestResponse(description = "The user doesn't have the rights to make this request.", responseCode = HttpServletResponse.SC_FORBIDDEN),
          @RestResponse(description = "The workflow instance could not be found.", responseCode = HttpServletResponse.SC_NOT_FOUND),
          @RestResponse(description = "The workflow instance cannot transition to this state.", responseCode = HttpServletResponse.SC_CONFLICT) })
  public Response updateWorkflowInstance(@HeaderParam("Accept") String acceptHeader,
          @PathParam("workflowInstanceId") Long id, @FormParam("configuration") String configuration,
          @FormParam("state") String stateStr, @QueryParam("withoperations") boolean withOperations,
          @QueryParam("withconfiguration") boolean withConfiguration) {
    try {
      boolean changed = false;
      WorkflowInstance wi = workflowService.getWorkflowById(id);

      // Configuration
      if (isNoneBlank(configuration)) {
        JSONParser parser = new JSONParser();
        try {
          Map<String, String> properties = new HashMap<>((JSONObject) parser.parse(configuration));

          // Remove old configuration
          wi.getConfigurationKeys().forEach(wi::removeConfiguration);
          // Add new configuration
          properties.forEach(wi::setConfiguration);

          changed = true;
        } catch (ParseException e) {
          return RestUtil.R.badRequest("Passed parameter 'configuration' is invalid JSON.");
        }
      }

      // TODO: does it make sense to change the media package?

      if (changed) {
        workflowService.update(wi);
      }

      // State change
      if (isNoneBlank(stateStr)) {
        WorkflowInstance.WorkflowState state;
        try {
          state = jsonToEnum(WorkflowInstance.WorkflowState.class, stateStr);
        } catch (IllegalArgumentException e) {
          return RestUtil.R.badRequest(String.format("Invalid workflow state '%s'", stateStr));
        }

        WorkflowInstance.WorkflowState currentState = wi.getState();
        if (state != currentState) {
          // Allowed transitions:
          //
          //   instantiated -> paused, stopped, running
          //   running      -> paused, stopped
          //   failing      -> paused, stopped
          //   paused       -> paused, stopped, running
          //   succeeded    -> paused, stopped
          //   stopped      -> paused, stopped
          //   failed       -> paused, stopped
          switch (state) {
            case PAUSED:
              workflowService.suspend(wi.getId());
              break;
            case STOPPED:
              workflowService.stop(wi.getId());
              break;
            case RUNNING:
              if (currentState == WorkflowInstance.WorkflowState.INSTANTIATED
                      || currentState == WorkflowInstance.WorkflowState.PAUSED) {
                workflowService.resume(wi.getId());
              } else {
                return RestUtil.R.conflict(
                        String.format("Cannot resume from workflow state '%s'", currentState.toString().toLowerCase()));
              }
              break;
            default:
              return RestUtil.R.conflict(
                      String.format("Cannot transition state from '%s' to '%s'", currentState.toString().toLowerCase(),
                              stateStr));
          }
        }
      }

      wi = workflowService.getWorkflowById(id);
      return ApiResponses.Json.ok(acceptHeader, workflowInstanceToJSON(wi, withOperations, withConfiguration));
    } catch (NotFoundException e) {
      return ApiResponses.notFound("Cannot find workflow instance with id '%d'.", id);
    } catch (UnauthorizedException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception e) {
      logger.error("The workflow service was not able to get the workflow instance", e);
      return ApiResponses.serverError("Could not retrieve workflow instance, reason: '%s'", e.getMessage());
    }
  }

  @DELETE
  @Path("{workflowInstanceId}")
  @RestQuery(name = "deleteworkflowinstance", description = "Deletes a workflow instance.", returnDescription = "", pathParameters = {
          @RestParameter(name = "workflowInstanceId", description = "The workflow instance id", isRequired = true, type = INTEGER) }, responses = {
          @RestResponse(description = "The workflow instance has been deleted.", responseCode = HttpServletResponse.SC_NO_CONTENT),
          @RestResponse(description = "The user doesn't have the rights to make this request.", responseCode = HttpServletResponse.SC_FORBIDDEN),
          @RestResponse(description = "The specified workflow instance does not exist.", responseCode = HttpServletResponse.SC_NOT_FOUND),
          @RestResponse(description = "The workflow instance cannot be deleted in this state.", responseCode = HttpServletResponse.SC_CONFLICT) })
  public Response deleteWorkflowInstance(@HeaderParam("Accept") String acceptHeader,
          @PathParam("workflowInstanceId") Long id) {
    try {
      workflowService.remove(id);
    } catch (WorkflowStateException e) {
      return RestUtil.R.conflict("Cannot delete workflow instance in this workflow state");
    } catch (NotFoundException e) {
      return ApiResponses.notFound("Cannot find workflow instance with id '%d'.", id);
    } catch (UnauthorizedException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception e) {
      logger.error("Could not delete workflow instances", e);
      return ApiResponses.serverError("Could not delete workflow instances, reason: '%s'", e.getMessage());
    }

    return Response.noContent().build();
  }

  private JValue workflowInstanceToJSON(WorkflowInstance wi, boolean withOperations, boolean withConfiguration) {
    List<Field> fields = new ArrayList<>();

    fields.add(f("identifier", v(wi.getId())));
    fields.add(f("title", v(wi.getTitle(), BLANK)));
    fields.add(f("description", v(wi.getDescription(), BLANK)));
    fields.add(f("workflow_definition_identifier", v(wi.getTemplate(), BLANK)));
    fields.add(f("event_identifier", v(wi.getMediaPackage().getIdentifier().toString())));
    fields.add(f("creator", v(wi.getCreatorName())));
    fields.add(f("state", enumToJSON(wi.getState())));
    if (withOperations) {
      fields.add(f("operations", arr(wi.getOperations()
                                       .stream()
                                       .map(this::workflowOperationInstanceToJSON)
                                       .collect(Collectors.toList()))));
    }
    if (withConfiguration) {
      fields.add(f("configuration", obj(wi.getConfigurationKeys()
                                          .stream()
                                          .map(key -> f(key, wi.getConfiguration(key)))
                                          .collect(Collectors.toList()))));
    }

    return obj(fields);
  }

  private JValue workflowOperationInstanceToJSON(WorkflowOperationInstance woi) {
    List<Field> fields = new ArrayList<>();
    DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_DATE_TIME;

    // The job ID can be null if the workflow was just created
    fields.add(f("identifier", v(woi.getId(), BLANK)));
    fields.add(f("operation", v(woi.getTemplate())));
    fields.add(f("description", v(woi.getDescription(), BLANK)));
    fields.add(f("state", enumToJSON(woi.getState())));
    fields.add(f("time_in_queue", v(woi.getTimeInQueue(), ZERO)));
    fields.add(f("host", v(woi.getExecutionHost(), BLANK)));
    fields.add(f("if", v(woi.getExecutionCondition(), BLANK)));
    fields.add(f("unless", v(woi.getSkipCondition(), BLANK)));
    fields.add(f("fail_workflow_on_error", v(woi.isFailWorkflowOnException())));
    fields.add(f("error_handler_workflow", v(woi.getExceptionHandlingWorkflow(), BLANK)));
    fields.add(f("retry_strategy", v(new RetryStrategy.Adapter().marshal(woi.getRetryStrategy()), BLANK)));
    fields.add(f("max_attempts", v(woi.getMaxAttempts())));
    fields.add(f("failed_attempts", v(woi.getFailedAttempts())));
    fields.add(f("configuration", obj(woi.getConfigurationKeys()
                                         .stream()
                                         .map(key -> f(key, woi.getConfiguration(key)))
                                         .collect(Collectors.toList()))));
    if (woi.getDateStarted() != null) {
      fields.add(f("start", v(dateFormatter.format(woi.getDateStarted().toInstant().atZone(UTC)))));
    } else {
      fields.add(f("start", BLANK));
    }
    if (woi.getDateCompleted() != null) {
      fields.add(f("completion", v(dateFormatter.format(woi.getDateCompleted().toInstant().atZone(UTC)))));
    } else {
      fields.add(f("completion", BLANK));
    }

    return obj(fields);
  }

  private JValue enumToJSON(Enum e) {
    return e == null ? null : v(e.toString().toLowerCase());
  }

  private <T extends Enum<T>> T jsonToEnum(Class<T> enumType, String name) {
    return Enum.valueOf(enumType, name.toUpperCase());
  }

  private String getWorkflowUrl(long workflowInstanceId) {
    return UrlSupport.concat(endpointBaseUrl, Long.toString(workflowInstanceId));
  }
}
