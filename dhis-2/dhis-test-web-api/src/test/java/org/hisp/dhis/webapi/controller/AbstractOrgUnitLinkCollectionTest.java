/*
 * Copyright (c) 2004-2026, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors 
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.webapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.stat.Statistics;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.dxf2.metadata.collection.CollectionService;
import org.hisp.dhis.hibernate.LinkTableWriter;
import org.hisp.dhis.http.HttpClientAdapter.HttpResponse;
import org.hisp.dhis.http.HttpStatus;
import org.hisp.dhis.jsontree.JsonArray;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.test.webapi.PostgresControllerIntegrationTestBase;
import org.hisp.dhis.test.webapi.json.domain.JsonStats;
import org.hisp.dhis.test.webapi.json.domain.JsonTypeReport;
import org.hisp.dhis.test.webapi.json.domain.JsonWebMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adding and removing org units of an owning many-to-many collection (e.g. org unit group members,
 * program org units) through the collection endpoints, from both the owner (owning) side and the
 * org unit (inverse) side. Subclasses describe one relationship. Runs on Postgres so the
 * second-level cache is active, as in production.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
abstract class AbstractOrgUnitLinkCollectionTest extends PostgresControllerIntegrationTestBase {

  @Autowired protected IdentifiableObjectManager manager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private CollectionService collectionService;
  @Autowired private LinkTableWriter linkTableWriter;
  @Autowired private PlatformTransactionManager transactionManager;

  /** The owner type, e.g. OrganisationUnitGroup. */
  protected abstract Class<? extends IdentifiableObject> ownerClass();

  /** A new, unsaved owner. */
  protected abstract IdentifiableObject newOwner();

  /** Adds the org unit to the owner's collection in memory, on both sides. */
  protected abstract void addOrgUnit(IdentifiableObject owner, OrganisationUnit unit);

  /** The owner's org unit collection, e.g. OrganisationUnitGroup.getMembers(). */
  protected abstract Set<OrganisationUnit> orgUnits(IdentifiableObject owner);

  /** The owner's API endpoint, e.g. organisationUnitGroups. */
  protected abstract String ownerEndpoint();

  /** The org unit's collection property for this relationship, e.g. organisationUnitGroups. */
  protected abstract String orgUnitProperty();

  /** The owning collection's field name, e.g. members. */
  protected abstract String ownerField();

  /** The inverse collection's field name on OrganisationUnit, e.g. groups. */
  protected abstract String inverseField();

  /** The owner's table, e.g. orgunitgroup. */
  protected abstract String ownerTable();

  /** The owner's id column, e.g. orgunitgroupid. */
  protected abstract String ownerIdColumn();

  /** The join table, e.g. orgunitgroupmembers. */
  protected abstract String joinTable();

  /** The owner's API collection property, organisationUnits for all current owners. */
  protected String ownerProperty() {
    return "organisationUnits";
  }

  private String ownerRole() {
    return ownerClass().getName() + "." + ownerField();
  }

  private String inverseRole() {
    return OrganisationUnit.class.getName() + "." + inverseField();
  }

  private String ownerId;
  private String memberId;
  private String otherId;

  @BeforeEach
  void setUpOwner() {
    OrganisationUnit member = createOrganisationUnit("Member");
    OrganisationUnit other = createOrganisationUnit("Other");
    IdentifiableObject owner = newOwner();
    doInTransaction(
        () -> {
          manager.save(member);
          manager.save(other);
          addOrgUnit(owner, member);
          manager.save(owner);
        });
    ownerId = owner.getUid();
    memberId = member.getUid();
    otherId = other.getUid();
    // each request starts with a fresh session in production, so nothing here is loaded yet
    entityManager.clear();
  }

  @Test
  void testOwnerSide_AddNewMember() {
    assertStats(POST(ownerPath(otherId)).content(HttpStatus.OK), 1, 0, 0);
    assertMembers(memberId, otherId);
  }

  @Test
  void testOwnerSide_AddExistingMember() {
    assertStats(POST(ownerPath(memberId)).content(HttpStatus.OK), 0, 0, 1);
    assertMembers(memberId);
  }

  @Test
  void testOwnerSide_RemoveMember() {
    assertStats(DELETE(ownerPath(memberId)).content(HttpStatus.OK), 0, 1, 0);
    assertMembers();
  }

  @Test
  void testOwnerSide_RemoveNonMember() {
    assertStats(DELETE(ownerPath(otherId)).content(HttpStatus.OK), 0, 0, 1);
    assertMembers(memberId);
  }

  @Test
  void testOrgUnitSide_AddNewMember() {
    assertStats(POST(orgUnitPath(otherId)).content(HttpStatus.OK), 1, 0, 0);
    assertMembers(memberId, otherId);
  }

  @Test
  void testOrgUnitSide_AddExistingMember() {
    assertStats(POST(orgUnitPath(memberId)).content(HttpStatus.OK), 0, 0, 1);
    assertMembers(memberId);
  }

  @Test
  void testOrgUnitSide_RemoveMember() {
    assertStats(DELETE(orgUnitPath(memberId)).content(HttpStatus.OK), 0, 1, 0);
    assertMembers();
  }

  @Test
  void testOrgUnitSide_RemoveNonMember() {
    assertStats(DELETE(orgUnitPath(otherId)).content(HttpStatus.OK), 0, 0, 1);
    assertMembers(memberId);
  }

  @Test
  void testBulkAdditionsAndDeletions() {
    assertStats(
        POST(
                "/" + ownerEndpoint() + "/" + ownerId + "/" + ownerProperty(),
                "{'additions':[{'id':'" + otherId + "'}],'deletions':[{'id':'" + memberId + "'}]}")
            .content(HttpStatus.OK),
        1,
        1,
        0);
    assertMembers(otherId);
  }

  /**
   * Adding or removing one org unit must not load the owner's whole set: on large owners that
   * hydrates hundreds of thousands of org units for a one-row change.
   */
  @ParameterizedTest
  @ValueSource(strings = {"owner", "orgUnit"})
  void testAddAndRemoveDoNotLoadOrgUnits(String side) {
    doInTransaction(
        () -> {
          IdentifiableObject owner = manager.get(ownerClass(), ownerId);
          for (int i = 0; i < 20; i++) {
            OrganisationUnit unit = createOrganisationUnit("Filler" + i);
            manager.save(unit);
            addOrgUnit(owner, unit);
          }
          manager.update(owner);
        });
    String path = "owner".equals(side) ? ownerPath(otherId) : orgUnitPath(otherId);
    Statistics statistics = statistics();

    entityManager.clear();
    statistics.clear();
    assertStats(POST(path).content(HttpStatus.OK), 1, 0, 0);
    assertEquals(0, statistics.getCollectionStatistics(ownerRole()).getLoadCount(), "add");

    entityManager.clear();
    statistics.clear();
    assertStats(DELETE(path).content(HttpStatus.OK), 0, 1, 0);
    assertEquals(0, statistics.getCollectionStatistics(ownerRole()).getLoadCount(), "remove");

    assertEquals(21, countLinks());
  }

  /**
   * Replacing the org unit's list (what the Metadata Management app sends when an org unit is
   * saved) applies only the difference, item by item, so it must not load the owner's set either.
   */
  @Test
  void testReplaceFromOrgUnitSideDoesNotLoadOrgUnits() {
    Statistics statistics = statistics();
    statistics.clear();
    JsonObject message =
        PUT(
                "/organisationUnits/" + otherId + "/" + orgUnitProperty(),
                "{'identifiableObjects':[{'id':'" + ownerId + "'}]}")
            .content(HttpStatus.OK);
    assertStats(message, 1, 0, 0);
    assertEquals(0, statistics.getCollectionStatistics(ownerRole()).getLoadCount(), "add");
    assertMembers(memberId, otherId);

    statistics.clear();
    assertStats(
        PUT("/organisationUnits/" + otherId + "/" + orgUnitProperty(), "{'identifiableObjects':[]}")
            .content(HttpStatus.OK),
        0,
        1,
        0);
    assertEquals(0, statistics.getCollectionStatistics(ownerRole()).getLoadCount(), "remove");
    assertMembers(memberId);
  }

  /**
   * Guards that the direct write does not change which side updates the owner's lastUpdated. Only a
   * change made from the org unit side updates it; that asymmetry is pre-existing, likely
   * unintended, and to be fixed separately for all owned collections.
   */
  @ParameterizedTest
  @ValueSource(strings = {"owner", "orgUnit"})
  void testLastUpdatedOfOwner(String side) throws InterruptedException {
    boolean bumped = "orgUnit".equals(side);
    String path = bumped ? orgUnitPath(otherId) : ownerPath(otherId);
    Timestamp before = ownerLastUpdated();
    Thread.sleep(20);
    assertStats(POST(path).content(HttpStatus.OK), 1, 0, 0);
    Timestamp afterAdd = ownerLastUpdated();
    assertEquals(bumped, afterAdd.after(before), "add");
    Thread.sleep(20);
    assertStats(DELETE(path).content(HttpStatus.OK), 0, 1, 0);
    assertEquals(bumped, ownerLastUpdated().after(afterAdd), "remove");
  }

  private Timestamp ownerLastUpdated() {
    return jdbcTemplate.queryForObject(
        "select lastupdated from " + ownerTable() + " where uid = ?", Timestamp.class, ownerId);
  }

  /**
   * The org unit's cached inverse collection must not survive a change. The service is called
   * directly here, without the controller's clear-everything call after the request, so only the
   * eviction done by the direct write is tested.
   */
  @Test
  void testInverseCacheEntryIsEvicted() throws Exception {
    long otherDbId = manager.get(OrganisationUnit.class, otherId).getId();
    entityManager.clear();
    assertEquals(Set.of(), orgUnitOwners(otherId));
    assertTrue(cache().containsCollection(inverseRole(), otherDbId), "cached before");

    doInTransaction(
        () -> {
          try {
            collectionService.addCollectionItems(
                manager.get(ownerClass(), ownerId),
                ownerProperty(),
                List.of(manager.get(OrganisationUnit.class, otherId)));
          } catch (Exception ex) {
            throw new IllegalStateException(ex);
          }
        });

    assertFalse(cache().containsCollection(inverseRole(), otherDbId), "evicted after");
    entityManager.clear();
    assertEquals(Set.of(ownerId), orgUnitOwners(otherId));
  }

  /** A set that is already loaded in the session is changed in memory, as before. */
  @Test
  void testLoadedSetIsChangedInMemory() {
    doInTransaction(
        () -> {
          IdentifiableObject owner = manager.get(ownerClass(), ownerId);
          OrganisationUnit other = manager.get(OrganisationUnit.class, otherId);
          assertEquals(1, orgUnits(owner).size());
          try {
            collectionService.addCollectionItems(owner, ownerProperty(), List.of(other));
          } catch (Exception ex) {
            throw new IllegalStateException(ex);
          }
          assertTrue(orgUnits(owner).contains(other), "session sees the new org unit");
        });
    assertMembers(memberId, otherId);
  }

  /**
   * Two concurrent adds of the same org unit: the second waits for the first to commit, then finds
   * the row and inserts nothing. Without the conflict handling it fails with a key violation.
   */
  @Test
  void testConcurrentAddsOfSameOrgUnit() throws Exception {
    long ownerDbId = manager.get(ownerClass(), ownerId).getId();
    long otherDbId = manager.get(OrganisationUnit.class, otherId).getId();
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    CountDownLatch firstInserted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first =
          executor.submit(
              () ->
                  tx.execute(
                      status -> {
                        boolean added = linkTableWriter.add(ownerRole(), ownerDbId, otherDbId);
                        firstInserted.countDown();
                        await(releaseFirst);
                        return added;
                      }));
      await(firstInserted);
      Future<Boolean> second =
          executor.submit(
              () -> tx.execute(status -> linkTableWriter.add(ownerRole(), ownerDbId, otherDbId)));
      // the second insert blocks on the first one's uncommitted row
      Thread.sleep(300);
      assertFalse(second.isDone(), "second add waits for the first to commit");
      releaseFirst.countDown();

      assertTrue(first.get(10, TimeUnit.SECONDS), "first add inserts");
      assertFalse(second.get(10, TimeUnit.SECONDS), "second add finds the row");
    } finally {
      executor.shutdownNow();
    }
    assertMembers(memberId, otherId);
  }

  /**
   * A user who can edit the org unit but not the owner must not change the owner's org units. From
   * the owner side the request is refused up front (403). From the org unit side the row is written
   * before the owner's update is refused, and the refused update rolls the whole transaction back,
   * which also undoes the row; the response is currently a 500 (a separate, pre-existing issue).
   */
  @ParameterizedTest
  @ValueSource(strings = {"owner", "orgUnit"})
  void testUserWithoutEditAccessToOwner(String side) {
    doInTransaction(
        () -> {
          IdentifiableObject owner = manager.get(ownerClass(), ownerId);
          owner.getSharing().setPublicAccess("r-------");
          owner.getSharing().setOwner((String) null);
          manager.update(owner);
        });
    entityManager.clear();
    switchToNewUser("noOwnerEdit", "F_ORGANISATIONUNIT_ADD");
    boolean ownerSide = "owner".equals(side);

    HttpResponse add = POST(ownerSide ? ownerPath(otherId) : orgUnitPath(otherId));
    HttpResponse remove = DELETE(ownerSide ? ownerPath(memberId) : orgUnitPath(memberId));

    if (ownerSide) {
      assertEquals(HttpStatus.FORBIDDEN, add.status(), "add");
      assertEquals(HttpStatus.FORBIDDEN, remove.status(), "remove");
    } else {
      assertFalse(add.success(), "add");
      assertFalse(remove.success(), "remove");
    }
    switchToAdminUser();
    assertMembers(memberId);
  }

  @Test
  void testSupportedRoles() {
    assertTrue(linkTableWriter.supports(ownerRole()));
    // not on the allowlist (yet)
    assertFalse(linkTableWriter.supports("org.hisp.dhis.dataset.DataSet.sources"));
    // inverse side
    assertFalse(linkTableWriter.supports(inverseRole()));
  }

  /** {@code on conflict do nothing} needs a unique constraint on the pair. */
  @Test
  void testJoinTableHasPrimaryKeyOnPair() {
    List<String> columns =
        jdbcTemplate.queryForList(
            "select a.attname from pg_index i"
                + " join pg_attribute a on a.attrelid = i.indrelid and a.attnum = any(i.indkey)"
                + " where i.indrelid = '"
                + joinTable()
                + "'::regclass and i.indisprimary",
            String.class);
    assertEquals(Set.of(ownerIdColumn(), "organisationunitid"), Set.copyOf(columns));
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(10, TimeUnit.SECONDS), "latch timed out");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  private org.hibernate.Cache cache() {
    return entityManagerFactory.unwrap(SessionFactoryImplementor.class).getCache();
  }

  private Set<String> orgUnitOwners(String ouId) {
    return ids(
        GET("/organisationUnits/{id}?fields={p}[id]", ouId, orgUnitProperty())
            .content(HttpStatus.OK)
            .getArray(orgUnitProperty()));
  }

  private Statistics statistics() {
    Statistics statistics =
        entityManagerFactory.unwrap(SessionFactoryImplementor.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    return statistics;
  }

  private int countLinks() {
    return jdbcTemplate.queryForObject(
        "select count(*) from %s l join %s o on o.%s = l.%s where o.uid = ?"
            .formatted(joinTable(), ownerTable(), ownerIdColumn(), ownerIdColumn()),
        Integer.class,
        ownerId);
  }

  private String ownerPath(String ouId) {
    return "/" + ownerEndpoint() + "/" + ownerId + "/" + ownerProperty() + "/" + ouId;
  }

  private String orgUnitPath(String ouId) {
    return "/organisationUnits/" + ouId + "/" + orgUnitProperty() + "/" + ownerId;
  }

  private static void assertStats(JsonObject message, int updated, int deleted, int ignored) {
    JsonStats stats =
        message.as(JsonWebMessage.class).getResponse().as(JsonTypeReport.class).getStats();
    assertEquals(updated, stats.getUpdated(), "updated");
    assertEquals(deleted, stats.getDeleted(), "deleted");
    assertEquals(ignored, stats.getIgnored(), "ignored");
  }

  /** Checks the join table and both API sides agree on the owner's org units. */
  private void assertMembers(String... expected) {
    // read back as a new request would, not through entities left in the shared test session
    entityManager.clear();
    Set<String> expectedIds = Set.of(expected);
    List<String> rows =
        jdbcTemplate.queryForList(
            ("select ou.uid from %s l join %s o on o.%s = l.%s"
                    + " join organisationunit ou on ou.organisationunitid = l.organisationunitid"
                    + " where o.uid = ?")
                .formatted(joinTable(), ownerTable(), ownerIdColumn(), ownerIdColumn()),
            String.class,
            ownerId);
    assertEquals(expectedIds, Set.copyOf(rows), "join table");
    assertEquals(rows.size(), Set.copyOf(rows).size(), "no duplicate rows");

    JsonArray units =
        GET("/{e}/{id}?fields={p}[id]", ownerEndpoint(), ownerId, ownerProperty())
            .content(HttpStatus.OK)
            .getArray(ownerProperty());
    assertEquals(expectedIds, ids(units), "owner side");

    for (String ouId : List.of(memberId, otherId)) {
      assertEquals(
          expectedIds.contains(ouId) ? Set.of(ownerId) : Set.of(),
          orgUnitOwners(ouId),
          "org unit side");
    }
  }

  private static Set<String> ids(JsonArray array) {
    return array.asList(JsonObject.class).stream()
        .map(o -> o.getString("id").string())
        .collect(Collectors.toSet());
  }
}
