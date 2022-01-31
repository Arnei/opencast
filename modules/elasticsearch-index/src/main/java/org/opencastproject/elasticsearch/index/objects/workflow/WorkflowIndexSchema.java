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

import org.opencastproject.elasticsearch.impl.IndexSchema;

public interface WorkflowIndexSchema extends IndexSchema {

  /** The unique identifier */
  String ID = "id";

  String OBJECT = "object";

  String STATE = "state";

  /** The workflow title */
  String TEMPLATE = "template";

  /** The description of the workflow */
  String TITLE = "title";

  /** The subject of the workflow */
  String DESCRIPTION = "description";

  /** The organization for the workflow */
  String PARENT_ID = "parentId";

  /** The language for the workflow */
  String CREATOR_NAME = "creatorName";

  /** The creator of the workflow */
  String ORGANIZATION_ID = "organizationId";

  /** The contributors to the workflow */
  String OPERATIONS = "operations";

  /** The contributors to the workflow */
  String CURRENT_OPERATION = "currentOperation";

  /** The date and time the workflow was created in UTC format e.g. 2011-07-16T20:39:05Z */
  String DATE_CREATED = "dateCreated";

  /** The date and time the workflow was created in UTC format e.g. 2011-07-16T20:39:05Z */
  String DATE_COMPLETED = "dateCompleted";

  /** The organizers for the workflow */
  String MEDIAPACKAGE = "mediaPackage";

  /** The publisher of the workflow */
  String SERIES = "series";
}
