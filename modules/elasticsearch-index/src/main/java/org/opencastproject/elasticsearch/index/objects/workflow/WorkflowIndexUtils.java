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

import org.opencastproject.elasticsearch.api.SearchMetadata;
import org.opencastproject.elasticsearch.impl.SearchMetadataCollection;
import org.opencastproject.util.DateTimeSupport;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import javax.xml.bind.Unmarshaller;

public final class WorkflowIndexUtils {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowIndexUtils.class);

  /**
   * This is a utility class and should therefore not be instantiated.
   */
  private WorkflowIndexUtils() {
  }

  /**
   * Creates a search result item based on the data returned from the search index.
   *
   * @param metadata
   *          the search metadata
   * @param unmarshaller the unmarshaller to use
   * @return the search result item
   * @throws IOException
   *           if unmarshalling fails
   */
  public static Workflow toWorkflow(SearchMetadataCollection metadata, Unmarshaller unmarshaller) throws IOException {
    Map<String, SearchMetadata<?>> metadataMap = metadata.toMap();
    String workflowXml = (String) metadataMap.get(WorkflowIndexSchema.OBJECT).getValue();
    return Workflow.valueOf(IOUtils.toInputStream(workflowXml, Charset.defaultCharset()), unmarshaller);
  }

  /**
   * Creates search metadata from a workflow such that the event can be stored in the search index.
   *
   * @param workflow
   *          the workflow
   * @return the set of metadata
   */
  public static SearchMetadataCollection toSearchMetadata(Workflow workflow) {
    SearchMetadataCollection metadata = new SearchMetadataCollection(
            Long.toString(workflow.getIdentifier()).concat(workflow.getOrganizationId()), Workflow.DOCUMENT_TYPE);
    // Mandatory fields
    metadata.addField(WorkflowIndexSchema.ID, workflow.getIdentifier(), true);
    metadata.addField(WorkflowIndexSchema.ORGANIZATION_ID, workflow.getOrganizationId(), false);
    metadata.addField(WorkflowIndexSchema.OBJECT, workflow.toXML(), false);

    // Optional fields
    if (workflow.getState() != null) {
      metadata.addField(WorkflowIndexSchema.STATE, workflow.getState(), true);
    }
    if (StringUtils.trimToNull(workflow.getTemplate()) != null) {
      metadata.addField(WorkflowIndexSchema.TEMPLATE, workflow.getTemplate(), true);
    }
    if (StringUtils.trimToNull(workflow.getTitle()) != null) {
      metadata.addField(WorkflowIndexSchema.TITLE, workflow.getTitle(), true);
    }
    if (StringUtils.trimToNull(workflow.getDescription()) != null) {
      metadata.addField(WorkflowIndexSchema.DESCRIPTION, workflow.getDescription(), true);
    }
    if (workflow.getParentId() != null) {
      metadata.addField(WorkflowIndexSchema.PARENT_ID, workflow.getParentId(), true);
    }
    if (StringUtils.trimToNull(workflow.getCreator()) != null) {
      metadata.addField(WorkflowIndexSchema.CREATOR_NAME, workflow.getCreator(), true);
    }
    if (StringUtils.trimToNull(workflow.getCurrentOperation()) != null) {
      metadata.addField(WorkflowIndexSchema.CURRENT_OPERATION, workflow.getCurrentOperation(), true);
    }
    if (workflow.getDateCreated() != null) {
      metadata.addField(WorkflowIndexSchema.DATE_CREATED,
              DateTimeSupport.toUTC(workflow.getDateCreated().getTime()), true);
    }
    if (workflow.getDateCompleted() != null) {
      metadata.addField(WorkflowIndexSchema.DATE_COMPLETED,
              DateTimeSupport.toUTC(workflow.getDateCompleted().getTime()), true);
    }
    if (StringUtils.trimToNull(workflow.getMediaPackage()) != null) {
      metadata.addField(WorkflowIndexSchema.MEDIAPACKAGE, workflow.getMediaPackage(), true);
    }
//    if (StringUtils.trimToNull(workflow.getAccessPolicy()) != null) {
//      metadata.addField(WorkflowIndexSchema.ACCESS_POLICY, workflow.getAccessPolicy(), false);
//      addAuthorization(metadata, workflow.getAccessPolicy());
//    }

    if (StringUtils.trimToNull(workflow.getSeriesId()) != null) {
      metadata.addField(WorkflowIndexSchema.SERIES, workflow.getSeriesId(), false);
    }
    return metadata;
  }

//  /**
//   * Adds authorization fields to the input document.
//   *
//   * @param doc
//   *          the input document
//   * @param aclString
//   *          the access control list string
//   */
//  private static void addAuthorization(SearchMetadataCollection doc, String aclString) {
//    Map<String, List<String>> permissions = new HashMap<String, List<String>>();
//
//    // Define containers for common permissions
//    for (Permissions.Action action : Permissions.Action.values()) {
//      permissions.put(action.toString(), new ArrayList<String>());
//    }
//
//    AccessControlList acl = AccessControlParser.parseAclSilent(aclString);
//    for (AccessControlEntry entry : acl.getEntries()) {
//      if (!entry.isAllow()) {
//        logger.info("Workflow index does not support denial via ACL, ignoring {}", entry);
//        continue;
//      }
//      List<String> actionPermissions = permissions.get(entry.getAction());
//      if (actionPermissions == null) {
//        actionPermissions = new ArrayList<String>();
//        permissions.put(entry.getAction(), actionPermissions);
//      }
//      actionPermissions.add(entry.getRole());
//    }
//
//    // Write the permissions to the input document
//    for (Map.Entry<String, List<String>> entry : permissions.entrySet()) {
//      String fieldName = WorkflowIndexSchema.ACL_PERMISSION_PREFIX.concat(entry.getKey());
//      doc.addField(fieldName, entry.getValue(), false);
//    }
//  }
}
