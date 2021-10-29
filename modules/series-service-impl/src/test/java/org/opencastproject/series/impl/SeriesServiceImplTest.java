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

package org.opencastproject.series.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.opencastproject.util.data.Collections.list;
import static org.opencastproject.util.persistence.PersistenceUtil.newTestEntityManagerFactory;

import org.opencastproject.elasticsearch.api.SearchIndexException;
import org.opencastproject.elasticsearch.api.SearchResult;
import org.opencastproject.elasticsearch.api.SearchResultItem;
import org.opencastproject.elasticsearch.index.AbstractSearchIndex;
import org.opencastproject.elasticsearch.index.series.Series;
import org.opencastproject.elasticsearch.index.series.SeriesSearchQuery;
import org.opencastproject.message.broker.api.MessageSender;
import org.opencastproject.metadata.dublincore.DublinCore;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogList;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogService;
import org.opencastproject.metadata.dublincore.DublinCoreUtil;
import org.opencastproject.metadata.dublincore.DublinCoreValue;
import org.opencastproject.metadata.dublincore.DublinCores;
import org.opencastproject.security.api.AccessControlEntry;
import org.opencastproject.security.api.AccessControlList;
import org.opencastproject.security.api.AccessControlParser;
import org.opencastproject.security.api.AccessControlUtil;
import org.opencastproject.security.api.DefaultOrganization;
import org.opencastproject.security.api.JaxbOrganization;
import org.opencastproject.security.api.JaxbRole;
import org.opencastproject.security.api.JaxbUser;
import org.opencastproject.security.api.Permissions;
import org.opencastproject.security.api.SecurityConstants;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.User;
import org.opencastproject.series.impl.persistence.SeriesServiceDatabaseImpl;
import org.opencastproject.util.DateTimeSupport;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.PathSupport;
import org.opencastproject.util.requests.SortCriterion;

import com.entwinemedia.fn.data.Opt;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Test for Series Service.
 *
 */
public class SeriesServiceImplTest {

  private SeriesServiceDatabaseImpl seriesDatabase;
  private AbstractSearchIndex esIndex;
  private DublinCoreCatalogService dcService;
  private String root;

  private SeriesServiceImpl seriesService;

  private DublinCoreCatalog testCatalog;
  private DublinCoreCatalog testCatalog2;
  private DublinCoreCatalog testCatalogNew;
  private DublinCoreCatalog testCatalogNew2;
  private DublinCoreCatalog testCatalogNew3;

  private AccessControlList accessControlList1;
  private AccessControlList accessControlList2;

  private Series series1;
  private Series series2;
  private Series series3;
  private Series aclSeries1;
  private Series aclSeries2;

  private static final String ELEMENT_TYPE = "testelement";
  private static final byte[] ELEMENT_DATA_1 = "abcdefghijklmnopqrstuvwxyz".getBytes();
  private static final byte[] ELEMENT_DATA_2 = "0123456789".getBytes();

  private final JaxbOrganization defaultOrganization = new DefaultOrganization();
  private final User defaultUser = new JaxbUser("sample", null, "WithPermissions",
          "with@permissions.com", "test", defaultOrganization,
          new HashSet<>(Arrays.asList(new JaxbRole("ROLE_STUDENT", defaultOrganization, "test"),
                  new JaxbRole("ROLE_OTHERSTUDENT", defaultOrganization, "test"),
                  new JaxbRole(defaultOrganization.getAnonymousRole(), defaultOrganization, "test"))));


  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    long currentTime = System.currentTimeMillis();

    // Mock up a security service
    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    User user = new JaxbUser("admin", "test", new DefaultOrganization(), new JaxbRole(
            SecurityConstants.GLOBAL_ADMIN_ROLE, new DefaultOrganization()));
    EasyMock.expect(securityService.getOrganization()).andReturn(new DefaultOrganization()).anyTimes();
    EasyMock.expect(securityService.getUser()).andReturn(user).anyTimes();
    EasyMock.replay(securityService);

    seriesDatabase = new SeriesServiceDatabaseImpl();
    seriesDatabase.setEntityManagerFactory(newTestEntityManagerFactory(SeriesServiceDatabaseImpl.PERSISTENCE_UNIT));
    dcService = new DublinCoreCatalogService();
    seriesDatabase.setDublinCoreService(dcService);
    seriesDatabase.activate(null);
    seriesDatabase.setSecurityService(securityService);

    root = PathSupport.concat("target", Long.toString(currentTime));

    MessageSender messageSender = EasyMock.createNiceMock(MessageSender.class);
    EasyMock.replay(messageSender);

    esIndex = EasyMock.createNiceMock(AbstractSearchIndex.class);
    EasyMock.expect(esIndex.addOrUpdateSeries(EasyMock.anyString(), EasyMock.anyObject(Function.class),
            EasyMock.anyString(), EasyMock.anyObject(User.class))).andReturn(Optional.empty()).atLeastOnce();

    accessControlList1 = new AccessControlList();
    List<AccessControlEntry> acl1 = accessControlList1.getEntries();
    acl1.add(new AccessControlEntry("admin", "delete", true));
    accessControlList2 = new AccessControlList();
    List<AccessControlEntry> acl2 = accessControlList2.getEntries();
    acl2.add(new AccessControlEntry("student", Permissions.Action.READ.toString(), true));

    long time = DateTimeSupport.fromUTC("2014-04-27T14:35:50Z");
    series1 = createSeries("1", "title 1", "contributor 1", "organizer 1", time, 1L);
    time = DateTimeSupport.fromUTC("2014-04-28T14:35:50Z");
    series2 = createSeries("2", "title 2", "contributor 2", "organizer 2", time, null);
    time = DateTimeSupport.fromUTC("2014-04-29T14:35:50Z");
    series3 = createSeries("3", "title 3", "contributor 3", "organizer 3", time, null);

    aclSeries1 = new Series("acl1", new DefaultOrganization().getId());
    aclSeries1.setAccessPolicy(AccessControlParser.toJsonSilent(accessControlList1));
    aclSeries2 = new Series("acl2", new DefaultOrganization().getId());
    aclSeries2.setAccessPolicy(AccessControlParser.toJsonSilent(accessControlList2));

    setUpEsIndexMockUp();

    EasyMock.replay(esIndex);


    seriesService = new SeriesServiceImpl();
    seriesService.setPersistence(seriesDatabase);
    seriesService.setSecurityService(securityService);
    seriesService.setMessageSender(messageSender);
    seriesService.setAdminUiIndex(esIndex);
    seriesService.setExternalApiIndex(esIndex);

    BundleContext bundleContext = EasyMock.createNiceMock(BundleContext.class);
    EasyMock.expect(bundleContext.getProperty((String) EasyMock.anyObject())).andReturn("System Admin");
    EasyMock.replay(bundleContext);

    ComponentContext componentContext = EasyMock.createNiceMock(ComponentContext.class);
    EasyMock.expect(componentContext.getBundleContext()).andReturn(bundleContext).anyTimes();
    EasyMock.replay(componentContext);

    seriesService.activate(componentContext);

    InputStream in = null;
    try {
      in = getClass().getResourceAsStream("/dublincore.xml");
      testCatalog = dcService.load(in);
    } finally {
      IOUtils.closeQuietly(in);
    }
    try {
      in = getClass().getResourceAsStream("/dublincore2.xml");
      testCatalog2 = dcService.load(in);
    } finally {
      IOUtils.closeQuietly(in);
    }
    try {
      in = getClass().getResourceAsStream("/dublincore_new.xml");
      testCatalogNew = dcService.load(in);
    } finally {
      IOUtils.closeQuietly(in);
    }
    try {
      in = getClass().getResourceAsStream("/dublincore_new2.xml");
      testCatalogNew2 = dcService.load(in);
    } finally {
      IOUtils.closeQuietly(in);
    }
    try {
      in = getClass().getResourceAsStream("/dublincore_new3.xml");
      testCatalogNew3 = dcService.load(in);
    } finally {
      IOUtils.closeQuietly(in);
    }
  }

  private void setUpEsIndexMockUp() throws SearchIndexException {
    SearchResultItem<Series> item1 = EasyMock.createMock(SearchResultItem.class);
    EasyMock.expect(item1.getSource()).andReturn(series1).anyTimes();
    SearchResultItem<Series> item2 = EasyMock.createMock(SearchResultItem.class);
    EasyMock.expect(item2.getSource()).andReturn(series2).anyTimes();
    SearchResultItem<Series> item3 = EasyMock.createMock(SearchResultItem.class);
    EasyMock.expect(item3.getSource()).andReturn(series3).anyTimes();

    SearchResultItem<Series>[] ascSeriesItems = new SearchResultItem[3];
    ascSeriesItems[0] = item1;
    ascSeriesItems[1] = item2;
    ascSeriesItems[2] = item3;

    SearchResultItem<Series>[] descSeriesItems = new SearchResultItem[3];
    descSeriesItems[0] = item3;
    descSeriesItems[1] = item2;
    descSeriesItems[2] = item1;

    SearchResultItem<Series>[] singleSeriesItems = new SearchResultItem[1];
    singleSeriesItems[0] = item1;

    SearchResultItem<Series> aclItem1 = EasyMock.createMock(SearchResultItem.class);
    EasyMock.expect(aclItem1.getSource()).andReturn(aclSeries1).anyTimes();
    SearchResultItem<Series> aclItem2 = EasyMock.createMock(SearchResultItem.class);
    EasyMock.expect(aclItem2.getSource()).andReturn(aclSeries2).anyTimes();

    SearchResultItem<Series>[] aclSeriesItems1 = new SearchResultItem[1];
    aclSeriesItems1[0] = aclItem1;
    SearchResultItem<Series>[] aclSeriesItems2 = new SearchResultItem[1];
    aclSeriesItems2[0] = aclItem2;

    // Setup series search results
    final SearchResult<Series> ascSeriesSearchResult = EasyMock.createMock(SearchResult.class);
    EasyMock.expect(ascSeriesSearchResult.getItems()).andReturn(ascSeriesItems).anyTimes();
    EasyMock.expect(ascSeriesSearchResult.getHitCount()).andReturn((long) ascSeriesItems.length);
    EasyMock.expect(ascSeriesSearchResult.getDocumentCount()).andReturn((long) ascSeriesItems.length);
    EasyMock.expect(ascSeriesSearchResult.getSearchTime()).andReturn(0L);
    final SearchResult<Series> descSeriesSearchResult = EasyMock.createMock(SearchResult.class);
    EasyMock.expect(descSeriesSearchResult.getItems()).andReturn(descSeriesItems).anyTimes();
    EasyMock.expect(descSeriesSearchResult.getHitCount()).andReturn((long) descSeriesItems.length);
    EasyMock.expect(descSeriesSearchResult.getDocumentCount()).andReturn((long) descSeriesItems.length);
    EasyMock.expect(descSeriesSearchResult.getSearchTime()).andReturn(0L);
    final SearchResult<Series> singleSeriesSearchResult = EasyMock.createMock(SearchResult.class);
    EasyMock.expect(singleSeriesSearchResult.getItems()).andReturn(singleSeriesItems).anyTimes();
    final SearchResult<Series> aclSeriesSearchResult = EasyMock.createMock(SearchResult.class);
    EasyMock.expect(aclSeriesSearchResult.getItems()).andReturn(aclSeriesItems1).anyTimes();
    final SearchResult<Series> aclSeriesSearchResult2 = EasyMock.createMock(SearchResult.class);
    EasyMock.expect(aclSeriesSearchResult2.getItems()).andReturn(aclSeriesItems2).anyTimes();
    final SearchResult<Series> emptySearchResult = EasyMock.createMock(SearchResult.class);
    EasyMock.expect(emptySearchResult.getItems()).andReturn(null).anyTimes();
    EasyMock.expect(emptySearchResult.getPageSize()).andReturn(0L).anyTimes();
    EasyMock.expect(emptySearchResult.getDocumentCount()).andReturn(0L).anyTimes();
    EasyMock.expect(emptySearchResult.getSearchTime()).andReturn(0L).anyTimes();

    final Capture<SeriesSearchQuery> captureSeriesSearchQuery = EasyMock.newCapture();
    EasyMock.expect(esIndex.getByQuery(EasyMock.capture(captureSeriesSearchQuery)))
      .andAnswer(new IAnswer<SearchResult<Series>>() {

        @Override
        public SearchResult<Series> answer() throws Throwable {
          if (captureSeriesSearchQuery.hasCaptured()
              && captureSeriesSearchQuery.getValue().getIdentifier().length == 1) {
            if ("acl1".equals(captureSeriesSearchQuery.getValue().getIdentifier()[0])) {
              return aclSeriesSearchResult;
            } else if ("acl2".equals(captureSeriesSearchQuery.getValue().getIdentifier()[0])) {
              return aclSeriesSearchResult2;
            } else {
              return emptySearchResult;
            }
          } else if (captureSeriesSearchQuery.hasCaptured()
              && captureSeriesSearchQuery.getValue().getTitle() != null) {
            if ("title 1".equals(captureSeriesSearchQuery.getValue().getTitle())) {
              return singleSeriesSearchResult;
            } else {
              return emptySearchResult;
            }
          } else if (captureSeriesSearchQuery.hasCaptured()
              && captureSeriesSearchQuery.getValue().getSeriesTitleSortOrder() == SortCriterion.Order.Ascending) {
            return ascSeriesSearchResult;
          } else if (captureSeriesSearchQuery.hasCaptured()
              && captureSeriesSearchQuery.getValue().getSeriesTitleSortOrder() == SortCriterion.Order.Descending) {
            return descSeriesSearchResult;
          } else if (captureSeriesSearchQuery.hasCaptured()
              && captureSeriesSearchQuery.getValue().getSeriesSubjectSortOrder() == SortCriterion.Order.Ascending) {
            return ascSeriesSearchResult;
          } else if (captureSeriesSearchQuery.hasCaptured()
              && captureSeriesSearchQuery.getValue().getSeriesSubjectSortOrder() == SortCriterion.Order.Descending) {
            return descSeriesSearchResult;
          } else if (captureSeriesSearchQuery.hasCaptured()
              && captureSeriesSearchQuery.getValue().getSeriesIdentifierSortOrder() == SortCriterion.Order.Ascending) {
            return ascSeriesSearchResult;
          } else if (captureSeriesSearchQuery.hasCaptured()
              && captureSeriesSearchQuery.getValue().getSeriesIdentifierSortOrder() == SortCriterion.Order.Descending) {
            return descSeriesSearchResult;
          } else {
            return emptySearchResult;
          }
        }

      }).anyTimes();


    EasyMock.replay(item1, item2, item3, aclItem1, aclItem2);
    EasyMock.replay(ascSeriesSearchResult, descSeriesSearchResult, aclSeriesSearchResult, aclSeriesSearchResult2,
            emptySearchResult, singleSeriesSearchResult);
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
    seriesDatabase = null;
//    index.deactivate();
    FileUtils.deleteQuietly(new File(root));
//    index = null;
  }

  @Test
  public void testSeriesManagement() throws Exception {
    testCatalog.set(DublinCore.PROPERTY_TITLE, "Some title");
    seriesService.updateSeries(testCatalog);
    DublinCoreCatalog retrivedSeries = seriesService.getSeries(testCatalog.getFirst(DublinCore.PROPERTY_IDENTIFIER));
    Assert.assertEquals("Some title", retrivedSeries.getFirst(DublinCore.PROPERTY_TITLE));

    testCatalog.set(DublinCore.PROPERTY_TITLE, "Some other title");
    seriesService.updateSeries(testCatalog);
    retrivedSeries = seriesService.getSeries(testCatalog.getFirst(DublinCore.PROPERTY_IDENTIFIER));
    Assert.assertEquals("Some other title", retrivedSeries.getFirst(DublinCore.PROPERTY_TITLE));

    seriesService.deleteSeries(testCatalog.getFirst(DublinCore.PROPERTY_IDENTIFIER));
    try {
      seriesService.getSeries(testCatalog.getFirst(DublinCore.PROPERTY_IDENTIFIER));
      Assert.fail("Series should not be available after removal.");
    } catch (NotFoundException e) {
      // expected
    }
  }

  @Test
  public void testSorting() throws Exception {
    seriesService.updateSeries(testCatalogNew);
    seriesService.updateSeries(testCatalogNew2);
    seriesService.updateSeries(testCatalogNew3);
    {
      SeriesSearchQuery q = new SeriesSearchQuery(defaultOrganization.getId(), defaultUser)
              .sortByTitle(SortCriterion.Order.Ascending);
      DublinCoreCatalogList r = seriesService.getSeries(q);
      Assert.assertEquals(3, r.getCatalogList().size());
      Assert.assertEquals("title 1", r.getCatalogList().get(0).getFirst(DublinCore.PROPERTY_TITLE));
    }
    {
      SeriesSearchQuery q = new SeriesSearchQuery(defaultOrganization.getId(), defaultUser)
              .sortByTitle(SortCriterion.Order.Descending);
      DublinCoreCatalogList r = seriesService.getSeries(q);
      Assert.assertEquals(3, r.getCatalogList().size());
      Assert.assertEquals("title 3",
              r.getCatalogList().get(0).getFirst(DublinCore.PROPERTY_TITLE));
    }
    {
      SeriesSearchQuery q = new SeriesSearchQuery(defaultOrganization.getId(), defaultUser)
          .sortBySubject(SortCriterion.Order.Ascending);
      DublinCoreCatalogList r = seriesService.getSeries(q);
      Assert.assertEquals(3, r.getCatalogList().size());
      Assert.assertEquals("subject 1",
          r.getCatalogList().get(0).getFirst(DublinCore.PROPERTY_SUBJECT));
    }
    {
      SeriesSearchQuery q = new SeriesSearchQuery(defaultOrganization.getId(), defaultUser)
              .sortBySubject(SortCriterion.Order.Descending);
      DublinCoreCatalogList r = seriesService.getSeries(q);
      Assert.assertEquals(3, r.getCatalogList().size());
      Assert.assertEquals("subject 3", r.getCatalogList().get(0).getFirst(DublinCore.PROPERTY_SUBJECT));
    }
    { // sort by series id, verify sort asc
      SeriesSearchQuery q = new SeriesSearchQuery(defaultOrganization.getId(), defaultUser)
          .sortByIdentifer(SortCriterion.Order.Ascending);
      DublinCoreCatalogList r = seriesService.getSeries(q);
      Assert.assertEquals(3, r.getCatalogList().size());
      String id1 = r.getCatalogList().get(0).getFirst(DublinCore.PROPERTY_IDENTIFIER);
      String id2 = r.getCatalogList().get(1).getFirst(DublinCore.PROPERTY_IDENTIFIER);
      Assert.assertTrue(id1.compareTo(id2) < 1);
    }
    { // sort by series id, verify sort desc
      SeriesSearchQuery q = new SeriesSearchQuery(defaultOrganization.getId(), defaultUser)
              .sortByIdentifer(SortCriterion.Order.Descending);
      DublinCoreCatalogList r = seriesService.getSeries(q);
      Assert.assertEquals(3, r.getCatalogList().size());
      String id1 = r.getCatalogList().get(0).getFirst(DublinCore.PROPERTY_IDENTIFIER);
      String id2 = r.getCatalogList().get(1).getFirst(DublinCore.PROPERTY_IDENTIFIER);
      Assert.assertTrue(id1.compareTo(id2) > -1);
    }
  }

  @Test
  public void testSeriesQuery() throws Exception {
    seriesService.updateSeries(testCatalogNew);
    SeriesSearchQuery q = new SeriesSearchQuery(defaultOrganization.getId(), defaultUser)
            .withTitle("other");
    List<DublinCoreCatalog> result = seriesService.getSeries(q).getCatalogList();
    Assert.assertEquals(0, result.size());

    q.withTitle(series1.getTitle());
    result = seriesService.getSeries(q).getCatalogList();
    Assert.assertEquals(1, result.size());
  }

  @Test
  public void testAddingSeriesWithoutID() throws Exception {
    testCatalog.remove(DublinCore.PROPERTY_IDENTIFIER);
    DublinCoreCatalog newSeries = seriesService.updateSeries(testCatalog);
    Assert.assertNotNull("New series DC should be returned", newSeries);
    String id = newSeries.getFirst(DublinCore.PROPERTY_IDENTIFIER);
    Assert.assertNotNull("New series should have id set", id);
  }

  @Test
  public void testACLManagement() throws Exception {
    List<AccessControlEntry> acl;

    try {
      seriesService.updateAccessControl("failid", accessControlList1);
      Assert.fail("Should fail when adding ACL to nonexistent series,");
    } catch (NotFoundException e) {
      // expected
    }

    seriesService.updateSeries(testCatalog);
    seriesService.updateAccessControl(testCatalog.getFirst(DublinCore.PROPERTY_IDENTIFIER), accessControlList1);
    AccessControlList retrievedACL = seriesService.getSeriesAccessControl("acl1");
    Assert.assertNotNull(retrievedACL);
    acl = retrievedACL.getEntries();
    Assert.assertEquals(acl.size(), 1);
    Assert.assertEquals("admin", acl.get(0).getRole());

    seriesService.updateAccessControl(testCatalog.getFirst(DublinCore.PROPERTY_IDENTIFIER), accessControlList2);
    retrievedACL = seriesService.getSeriesAccessControl("acl2");
    Assert.assertNotNull(retrievedACL);
    acl = retrievedACL.getEntries();
    Assert.assertEquals(acl.size(), 1);
    Assert.assertEquals("student", acl.get(0).getRole());
  }

  @Test
  public void testDublinCoreCatalogEquality1() {
    DublinCoreCatalog a = DublinCores.mkOpencast().getCatalog();
    DublinCoreCatalog b = DublinCores.mkOpencast().getCatalog();
    a.set(DublinCore.PROPERTY_IDENTIFIER, "123");
    assertFalse(DublinCoreUtil.equals(a, b));
    b.set(DublinCore.PROPERTY_IDENTIFIER, "123");
    assertTrue(DublinCoreUtil.equals(a, b));
    a.set(DublinCore.PROPERTY_CONTRIBUTOR, list(DublinCoreValue.mk("Peter"), DublinCoreValue.mk("Paul")));
    b.set(DublinCore.PROPERTY_CONTRIBUTOR, list(DublinCoreValue.mk("Paul"), DublinCoreValue.mk("Peter")));
    assertFalse(DublinCoreUtil.equals(a, b));
    //
    b.set(DublinCore.PROPERTY_CONTRIBUTOR, list(DublinCoreValue.mk("Peter"), DublinCoreValue.mk("Paul")));
    assertTrue(DublinCoreUtil.equals(a, b));
    //
    a.set(DublinCore.PROPERTY_SPATIAL, "room1");
    a.set(DublinCore.PROPERTY_DESCRIPTION, "this is a test lecture");
    b.set(DublinCore.PROPERTY_DESCRIPTION, "this is a test lecture");
    b.set(DublinCore.PROPERTY_SPATIAL, "room1");
    assertTrue(DublinCoreUtil.equals(a, b));
  }

  @Test
  public void testDublinCoreCatalogEquality2() {
    DublinCoreCatalog a = DublinCores.mkOpencast().getCatalog();
    DublinCoreCatalog b = DublinCores.mkOpencast().getCatalog();
    a.set(DublinCore.PROPERTY_DESCRIPTION, "this is a test lecture");
    a.set(DublinCore.PROPERTY_SPATIAL, "room1");
    a.set(DublinCore.PROPERTY_IDENTIFIER, "123");
    a.set(DublinCore.PROPERTY_CONTRIBUTOR, list(DublinCoreValue.mk("Peter"), DublinCoreValue.mk("Paul")));
    b.set(DublinCore.PROPERTY_CONTRIBUTOR, list(DublinCoreValue.mk("Peter"), DublinCoreValue.mk("Paul")));
    b.set(DublinCore.PROPERTY_DESCRIPTION, "this is a test lecture");
    b.set(DublinCore.PROPERTY_SPATIAL, "room1");
    b.set(DublinCore.PROPERTY_IDENTIFIER, "123");
    assertTrue(DublinCoreUtil.equals(a, b));
  }

  @Test
  public void testDublinCoreCatalogPreservation() throws Exception {
    seriesService.updateSeries(testCatalog2);
    DublinCoreCatalog dc = seriesService.getSeries("10.0000/5820");
    assertTrue(DublinCoreUtil.equals(testCatalog2, testCatalog2));
    assertTrue(DublinCoreUtil.equals(dc, dc));
    assertTrue(DublinCoreUtil.equals(testCatalog2, dc));
  }

  @Test
  public void testACLEquality1() {
    AccessControlList a = new AccessControlList(new AccessControlEntry("a", Permissions.Action.READ.toString(), true),
            new AccessControlEntry("b", Permissions.Action.WRITE.toString(), false));
    AccessControlList b = new AccessControlList(new AccessControlEntry("b", Permissions.Action.WRITE.toString(), false),
            new AccessControlEntry("a", Permissions.Action.READ.toString(), true));
    assertTrue(AccessControlUtil.equals(a, b));
  }

  @Test
  public void testACLEquality2() {
    AccessControlList a = new AccessControlList();
    AccessControlList b = new AccessControlList();
    assertTrue(AccessControlUtil.equals(a, b));
  }

  @Test
  public void testACLEquality3() {
    AccessControlList a = new AccessControlList();
    AccessControlList b = new AccessControlList(
            new AccessControlEntry("b", Permissions.Action.WRITE.toString(), false));
    assertFalse(AccessControlUtil.equals(a, b));
  }

  @Test
  public void testACLEquality4() {
    AccessControlList a = new AccessControlList(
            new AccessControlEntry("b", Permissions.Action.WRITE.toString(), false));
    AccessControlList b = new AccessControlList(new AccessControlEntry("b", Permissions.Action.WRITE.toString(), false),
            new AccessControlEntry("b", Permissions.Action.READ.toString(), false));
    assertFalse(AccessControlUtil.equals(a, b));
  }

  @Test
  public void testACLEquality5() {
    AccessControlList a = new AccessControlList(
            new AccessControlEntry("a", Permissions.Action.WRITE.toString(), false),
            new AccessControlEntry("a", Permissions.Action.WRITE.toString(), false));
    AccessControlList b = new AccessControlList(
            new AccessControlEntry("a", Permissions.Action.WRITE.toString(), false),
            new AccessControlEntry("b", Permissions.Action.READ.toString(), false));
    assertFalse(AccessControlUtil.equals(a, b));
  }

  @Test
  public void testACLEquality6() {
    AccessControlList a = new AccessControlList(
            new AccessControlEntry("a", Permissions.Action.WRITE.toString(), false),
            new AccessControlEntry("a", Permissions.Action.WRITE.toString(), false),
            new AccessControlEntry("b", Permissions.Action.READ.toString(), true));
    AccessControlList b = new AccessControlList(
            new AccessControlEntry("a", Permissions.Action.WRITE.toString(), false),
            new AccessControlEntry("b", Permissions.Action.READ.toString(), true));
    assertTrue(AccessControlUtil.equals(a, b));
  }

  @Test
  public void testACLEquality7() {
    // It is possible to apply this contradictory ACL to a series or event in OC,
    // where "a" is allowed AND disallowed from writing to the resource.
    // See MH-13089
    AccessControlList a = new AccessControlList(
            new AccessControlEntry("a", Permissions.Action.WRITE.toString(), false),
            new AccessControlEntry("a", Permissions.Action.WRITE.toString(), true),
            new AccessControlEntry("b", Permissions.Action.READ.toString(), true));
    AccessControlList b = new AccessControlList(
            new AccessControlEntry("a", Permissions.Action.WRITE.toString(), false),
            new AccessControlEntry("b", Permissions.Action.READ.toString(), true));
    assertFalse(AccessControlUtil.equals(a, b));
  }

  @Test
  public void testSeriesElements() throws Exception {
    seriesService.updateSeries(testCatalog);
    final String seriesId = testCatalog.getFirst(DublinCoreCatalog.PROPERTY_IDENTIFIER);

    assertTrue(seriesService.addSeriesElement(seriesId, ELEMENT_TYPE, ELEMENT_DATA_1));
    assertFalse(seriesService.addSeriesElement(seriesId, ELEMENT_TYPE, ELEMENT_DATA_1));
    assertArrayEquals(ELEMENT_DATA_1, seriesService.getSeriesElementData(seriesId, ELEMENT_TYPE).get());

    assertTrue(seriesService.updateSeriesElement(seriesId, ELEMENT_TYPE, ELEMENT_DATA_2));
    assertArrayEquals(ELEMENT_DATA_2, seriesService.getSeriesElementData(seriesId, ELEMENT_TYPE).get());

    assertTrue(seriesService.deleteSeriesElement(seriesId, ELEMENT_TYPE));
    assertFalse(seriesService.deleteSeriesElement(seriesId, ELEMENT_TYPE));
    assertEquals(Opt.none(), seriesService.getSeriesElementData(seriesId, ELEMENT_TYPE));
  }

  private Series createSeries(String id, String title, String contributor, String organizer, long time, Long themeId) {
    Series series = new Series(id, defaultOrganization.getId());
    series.setCreatedDateTime(new Date(time));
    series.addContributor(contributor);
    series.addOrganizer(organizer);
    series.setTitle(title);
    if (themeId != null) {
      series.setTheme(themeId);
    }
    return series;
  }

}
