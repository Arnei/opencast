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
import org.opencastproject.util.IoSupport;
import org.opencastproject.util.XmlSafeParser;
import org.opencastproject.workflow.api.WorkflowInstance;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Object wrapper for a workflow.
 */
@XmlType(name = "workflow", namespace = IndexObject.INDEX_XML_NAMESPACE, propOrder = { "identifier", "state",
        "template", "title", "description", "parentId", "creator", "organizationId", "currentOperation", "dateCreated",
        "dateCompleted", "mediaPackage", "mpContributors", "mpLanguage", "mpLicense", "mpTitle", "mpSubject",
        "seriesId", "seriesTitle"})
@XmlRootElement(name = "workflow", namespace = IndexObject.INDEX_XML_NAMESPACE)
@XmlAccessorType(XmlAccessType.NONE)
public class Workflow implements IndexObject {

  /** The document type */
  public static final String DOCUMENT_TYPE = "workflow";

  /** The name of the surrounding XML tag to wrap a result of multiple workflow */
  public static final String XML_SURROUNDING_TAG = "workflow-list";

  @XmlElement(name = "identifier")
  protected long identifier;

  @XmlElement()
  protected WorkflowInstance.WorkflowState state;

  @XmlElement(name = "template")
  protected String template;

  @XmlElement(name = "title")
  protected String title;

  @XmlElement(name = "description")
  protected String description;

  @XmlElement(name = "parent", nillable = true)
  protected Long parentId;

  @XmlElement(name = "creator", namespace = "http://org.opencastproject.security")
  protected String creator;

  @XmlElement(name = "organizationId", namespace = "http://org.opencastproject.security")
  protected String organizationId;

  @XmlElement
  protected String currentOperation = null;

  @XmlElement
  protected Date dateCreated = null;

  @XmlElement
  protected Date dateCompleted = null;
//  protected long count;
//  protected long startPage;
//  protected long startIndex;
//  protected String text;
//  protected String workflowDefinitionId;
  @XmlElement
  protected String mediaPackage;

  @XmlElementWrapper(name = "contributors")
  @XmlElement(name = "contributor")
  private List<String> mpContributors = null;

  @XmlElement
  protected String mpLanguage;

  @XmlElement
  protected String mpLicense;

  @XmlElement
  protected String mpTitle;

  @XmlElement
  protected String mpSubject;

  @XmlElement
  protected String seriesId;

  @XmlElement
  protected String seriesTitle;
  //  protected WorkflowSearchQuery.Sort sort = Sort.DATE_CREATED;
  //  protected boolean sortAscending = true;

  /** Context for serializing and deserializing */
  private static JAXBContext context = null;

  /**
   * Returns the workflow identifier.
   *
   * @return the identifier
   */
  public long getIdentifier() {
    return identifier;
  }

  public WorkflowInstance.WorkflowState getState() {
    return state;
  }

  public String getTemplate() {
    return template;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Long getParentId() {
    return parentId;
  }

  public String getCreator() {
    return creator;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public String getCurrentOperation() {
    return currentOperation;
  }

  public Date getDateCreated() {
    return dateCreated;
  }

  public Date getDateCompleted() {
    return dateCompleted;
  }

  public String getMediaPackage() {
    return mediaPackage;
  }

  public List<String> getMpContributors() {
    return mpContributors;
  }

  public String getMpLanguage() {
    return mpLanguage;
  }

  public String getMpLicense() {
    return mpLicense;
  }

  public String getMpTitle() {
    return mpTitle;
  }

  public String getMpSubject() {
    return mpSubject;
  }

  public String getSeriesId() {
    return seriesId;
  }

  public String getSeriesTitle() {
    return seriesTitle;
  }

  public void setIdentifier(long identifier) {
    this.identifier = identifier;
  }

  public void setState(WorkflowInstance.WorkflowState state) {
    this.state = state;
  }

  public void setTemplate(String template) {
    this.template = template;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public void setCreator(String creator) {
    this.creator = creator;
  }

  public void setOrganizationId(String organization) {
    this.organizationId = organizationId;
  }

  public void setCurrentOperation(String currentOperation) {
    this.currentOperation = currentOperation;
  }

  public void setDateCreated(Date dateCreated) {
    this.dateCreated = dateCreated;
  }

  public void setDateCompleted(Date dateCompleted) {
    this.dateCompleted = dateCompleted;
  }

  public void setMediaPackage(String mediaPackage) {
    this.mediaPackage = mediaPackage;
  }

  public void setMpContributors(List<String> mpContributors) {
    this.mpContributors = mpContributors;
  }

  public void setMpLanguage(String mpLanguage) {
    this.mpLanguage = mpLanguage;
  }

  public void setMpLicense(String mpLicense) {
    this.mpLicense = mpLicense;
  }

  public void setMpTitle(String mpTitle) {
    this.mpTitle = mpTitle;
  }

  public void setMpSubject(String mpSubject) {
    this.mpSubject = mpSubject;
  }

  public void setSeriesId(String seriesId) {
    this.seriesId = seriesId;
  }

  public void setSeriesTitle(String seriesTitle) {
    this.seriesTitle = seriesTitle;
  }

  /**
   * Required default no arg constructor for JAXB.
   */
  public Workflow() {

  }

  /**
   * The workflow identifier.
   *
   * @param identifier
   *          the object identifier
   * @param organization
   *          the organization
   */
  public Workflow(long identifier, String organization) {
    this.identifier = identifier;
    this.organizationId = organization;
  }

  public static Workflow valueOf(InputStream xml, Unmarshaller unmarshaller) throws IOException {
    try {
      if (context == null) {
        createJAXBContext();
      }
      return unmarshaller.unmarshal(XmlSafeParser.parse(xml), Workflow.class).getValue();
    } catch (JAXBException e) {
      throw new IOException(e.getLinkedException() != null ? e.getLinkedException() : e);
    } catch (SAXException e) {
      throw new IOException(e);
    } finally {
      IoSupport.closeQuietly(xml);
    }
  }

  /**
   * Initialize the JAXBContext.
   */
  private static void createJAXBContext() throws JAXBException {
    context = JAXBContext.newInstance(Workflow.class);
  }

  /**
   * Create an unmarshaller for series
   * @return an unmarshaller for series
   * @throws IOException
   */
  public static Unmarshaller createUnmarshaller() throws IOException {
    try {
      if (context == null) {
        createJAXBContext();
      }
      return context.createUnmarshaller();
    } catch (JAXBException e) {
      throw new IOException(e.getLinkedException() != null ? e.getLinkedException() : e);
    }
  }

  /**
   * Serializes the workflow to an XML format.
   *
   * @return A String with this workflows content as XML.
   */
  public String toXML() {
    try {
      if (context == null) {
        createJAXBContext();
      }
      StringWriter writer = new StringWriter();
      Marshaller marshaller = Workflow.context.createMarshaller();
      marshaller.marshal(this, writer);
      return writer.toString();
    } catch (JAXBException e) {
      throw new IllegalStateException(e.getLinkedException() != null ? e.getLinkedException() : e);
    }
  }
}
