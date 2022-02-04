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

import org.opencastproject.elasticsearch.index.objects.IndexObject;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = Workflow.XML_SURROUNDING_TAG, namespace = IndexObject.INDEX_XML_NAMESPACE)
public class WorkflowSetImpl implements WorkflowSet {

  /** A list of search items. */
  @XmlElement(name = "workflow")
  private List<Workflow> resultSet = null;

  /** The pagination limit. */
  @XmlAttribute(name = "limit")
  private long limit;

  /** The pagination offset. */
  @XmlAttribute(name = "offset")
  private long offset;

  /** The search time in milliseconds */
  @XmlAttribute(name = "searchTime")
  private long searchTime;

  /** The total number of results without paging */
  @XmlAttribute(name = "totalCount")
  private long totalCount;

  /**
   * A no-arg constructor needed by JAXB
   */
  public WorkflowSetImpl() {
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.elasticsearch.index.objects.workflow.WorkflowSet#getItems()
   */
  public Workflow[] getItems() {
    return resultSet == null || resultSet.size() == 0 ? new Workflow[0] : resultSet
            .toArray(new Workflow[resultSet.size()]);
  }

  /**
   * Adds an item to the result set.
   *
   * @param item
   *          the item to add
   */
  public void addItem(Workflow item) {
    if (item == null) {
      throw new IllegalArgumentException("Parameter item cannot be null");
    }
    if (resultSet == null) {
      resultSet = new ArrayList<>();
    }
    resultSet.add(item);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.elasticsearch.index.objects.workflow.WorkflowSet#getOffset()
   */
  @Override
  public long getOffset() {
    return offset;
  }

  /**
   * Set the offset.
   *
   * @param offset
   *          The  offset.
   */
  public void setOffset(long offset) {
    this.offset = offset;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.elasticsearch.index.objects.workflow.WorkflowSet#getLimit()
   */
  @Override
  public long getLimit() {
    return limit;
  }

  /**
   * Set the limit.
   *
   * @param limit
   *          The limit.
   */
  public void setLimit(long limit) {
    this.limit = limit;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.elasticsearch.index.objects.workflow.WorkflowSet#getSearchTime()
   */
  @Override
  public long getSearchTime() {
    return searchTime;
  }

  /**
   * Set the search time.
   *
   * @param searchTime
   *          The time in ms.
   */
  public void setSearchTime(long searchTime) {
    this.searchTime = searchTime;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.elasticsearch.index.objects.workflow.WorkflowSet#getTotalCount()
   */
  @Override
  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  public static class Adapter extends XmlAdapter<WorkflowSetImpl, WorkflowSet> {
    public WorkflowSetImpl marshal(WorkflowSet set) throws Exception {
      return (WorkflowSetImpl) set;
    }

    public WorkflowSet unmarshal(WorkflowSetImpl set) throws Exception {
      return set;
    }
  }
}
