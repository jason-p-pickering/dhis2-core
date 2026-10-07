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
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.dxf2.metadata.collection.CollectionService;
import org.hisp.dhis.hibernate.LinkTableWriter;
import org.hisp.dhis.http.HttpStatus;
import org.hisp.dhis.jsontree.JsonArray;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitGroup;
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
 * Adding and removing org unit group members through the collection endpoints, from both the group
 * (owning) side and the org unit (inverse) side. Runs on Postgres so the second-level cache is
 * active, as in production.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
class OrganisationUnitGroupMembersCollectionTest extends PostgresControllerIntegrationTestBase {

  @Autowired private IdentifiableObjectManager manager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private CollectionService collectionService;
  @Autowired private LinkTableWriter linkTableWriter;
  @Autowired private PlatformTransactionManager transactionManager;

  private static final String MEMBERS_ROLE = OrganisationUnitGroup.class.getName() + ".members";

  private static final String GROUPS_ROLE = OrganisationUnit.class.getName() + ".groups";

  private String groupId;
  private String memberId;
  private String otherId;

  @BeforeEach
  void setUpGroup() {
    OrganisationUnit member = createOrganisationUnit("Member");
    OrganisationUnit other = createOrganisationUnit("Other");
    OrganisationUnitGroup group = createOrganisationUnitGroup('A');
    doInTransaction(
        () -> {
          manager.save(member);
          manager.save(other);
          group.addOrganisationUnit(member);
          manager.save(group);
        });
    groupId = group.getUid();
    memberId = member.getUid();
    otherId = other.getUid();
    // each request starts with a fresh session in production, so nothing here is loaded yet
    entityManager.clear();
  }

  @Test
  void testGroupSide_AddNewMember() {
    assertStats(POST(groupPath(otherId)).content(HttpStatus.OK), 1, 0, 0);
    assertMembers(memberId, otherId);
  }

  @Test
  void testGroupSide_AddExistingMember() {
    assertStats(POST(groupPath(memberId)).content(HttpStatus.OK), 0, 0, 1);
    assertMembers(memberId);
  }

  @Test
  void testGroupSide_RemoveMember() {
    assertStats(DELETE(groupPath(memberId)).content(HttpStatus.OK), 0, 1, 0);
    assertMembers();
  }

  @Test
  void testGroupSide_RemoveNonMember() {
    assertStats(DELETE(groupPath(otherId)).content(HttpStatus.OK), 0, 0, 1);
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
                "/organisationUnitGroups/" + groupId + "/organisationUnits",
                "{'additions':[{'id':'" + otherId + "'}],'deletions':[{'id':'" + memberId + "'}]}")
            .content(HttpStatus.OK),
        1,
        1,
        0);
    assertMembers(otherId);
  }

  /**
   * Adding or removing one member must not load the group's whole member set: on large groups that
   * hydrates hundreds of thousands of org units for a one-row change.
   */
  @ParameterizedTest
  @ValueSource(strings = {"group", "orgUnit"})
  void testAddAndRemoveDoNotLoadMembers(String side) {
    doInTransaction(
        () -> {
          OrganisationUnitGroup group = manager.get(OrganisationUnitGroup.class, groupId);
          for (int i = 0; i < 20; i++) {
            OrganisationUnit unit = createOrganisationUnit("Filler" + i);
            manager.save(unit);
            group.addOrganisationUnit(unit);
          }
          manager.update(group);
        });
    String path = "group".equals(side) ? groupPath(otherId) : orgUnitPath(otherId);
    Statistics statistics = statistics();

    entityManager.clear();
    statistics.clear();
    assertStats(POST(path).content(HttpStatus.OK), 1, 0, 0);
    assertEquals(0, statistics.getCollectionStatistics(MEMBERS_ROLE).getLoadCount(), "add");

    entityManager.clear();
    statistics.clear();
    assertStats(DELETE(path).content(HttpStatus.OK), 0, 1, 0);
    assertEquals(0, statistics.getCollectionStatistics(MEMBERS_ROLE).getLoadCount(), "remove");

    assertEquals(21, countMembers());
  }

  /**
   * Guards that the direct write does not change which side updates the group's lastUpdated. Only a
   * change made from the org unit side updates it; that asymmetry is pre-existing, likely
   * unintended, and to be fixed separately for all owned collections.
   */
  @ParameterizedTest
  @ValueSource(strings = {"group", "orgUnit"})
  void testLastUpdatedOfGroup(String side) throws InterruptedException {
    boolean bumped = "orgUnit".equals(side);
    String path = bumped ? orgUnitPath(otherId) : groupPath(otherId);
    Timestamp before = groupLastUpdated();
    Thread.sleep(20);
    assertStats(POST(path).content(HttpStatus.OK), 1, 0, 0);
    Timestamp afterAdd = groupLastUpdated();
    assertEquals(bumped, afterAdd.after(before), "add");
    Thread.sleep(20);
    assertStats(DELETE(path).content(HttpStatus.OK), 0, 1, 0);
    assertEquals(bumped, groupLastUpdated().after(afterAdd), "remove");
  }

  private Timestamp groupLastUpdated() {
    return jdbcTemplate.queryForObject(
        "select lastupdated from orgunitgroup where uid = ?", Timestamp.class, groupId);
  }

  /**
   * The org unit's cached group list must not survive a member change. The service is called
   * directly here, without the controller's clear-everything call after the request, so only the
   * eviction done by the direct write is tested.
   */
  @Test
  void testInverseCacheEntryIsEvicted() throws Exception {
    long otherDbId = manager.get(OrganisationUnit.class, otherId).getId();
    entityManager.clear();
    assertEquals(Set.of(), orgUnitGroups(otherId));
    assertTrue(cache().containsCollection(GROUPS_ROLE, otherDbId), "cached before");

    doInTransaction(
        () -> {
          try {
            collectionService.addCollectionItems(
                manager.get(OrganisationUnitGroup.class, groupId),
                "organisationUnits",
                List.of(manager.get(OrganisationUnit.class, otherId)));
          } catch (Exception ex) {
            throw new IllegalStateException(ex);
          }
        });

    assertFalse(cache().containsCollection(GROUPS_ROLE, otherDbId), "evicted after");
    entityManager.clear();
    assertEquals(Set.of(groupId), orgUnitGroups(otherId));
  }

  /** A member set that is already loaded in the session is changed in memory, as before. */
  @Test
  void testLoadedMemberSetIsChangedInMemory() {
    doInTransaction(
        () -> {
          OrganisationUnitGroup group = manager.get(OrganisationUnitGroup.class, groupId);
          OrganisationUnit other = manager.get(OrganisationUnit.class, otherId);
          assertEquals(1, group.getMembers().size());
          try {
            collectionService.addCollectionItems(group, "organisationUnits", List.of(other));
          } catch (Exception ex) {
            throw new IllegalStateException(ex);
          }
          assertTrue(group.getMembers().contains(other), "session sees the new member");
        });
    assertMembers(memberId, otherId);
  }

  /**
   * Two concurrent adds of the same member: the second waits for the first to commit, then finds
   * the row and inserts nothing. Without the conflict handling it fails with a key violation.
   */
  @Test
  void testConcurrentAddsOfSameMember() throws Exception {
    long groupDbId = manager.get(OrganisationUnitGroup.class, groupId).getId();
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
                        boolean added = linkTableWriter.add(MEMBERS_ROLE, groupDbId, otherDbId);
                        firstInserted.countDown();
                        await(releaseFirst);
                        return added;
                      }));
      await(firstInserted);
      Future<Boolean> second =
          executor.submit(
              () -> tx.execute(status -> linkTableWriter.add(MEMBERS_ROLE, groupDbId, otherDbId)));
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

  @Test
  void testSupportedRoles() {
    assertTrue(linkTableWriter.supports(MEMBERS_ROLE));
    // not on the allowlist (yet)
    assertFalse(linkTableWriter.supports("org.hisp.dhis.dataset.DataSet.sources"));
    // inverse side
    assertFalse(linkTableWriter.supports(GROUPS_ROLE));
  }

  /** {@code on conflict do nothing} needs a unique constraint on the pair. */
  @Test
  void testJoinTableHasPrimaryKeyOnPair() {
    List<String> columns =
        jdbcTemplate.queryForList(
            "select a.attname from pg_index i"
                + " join pg_attribute a on a.attrelid = i.indrelid and a.attnum = any(i.indkey)"
                + " where i.indrelid = 'orgunitgroupmembers'::regclass and i.indisprimary",
            String.class);
    assertEquals(Set.of("orgunitgroupid", "organisationunitid"), Set.copyOf(columns));
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

  private Set<String> orgUnitGroups(String ouId) {
    return ids(
        GET("/organisationUnits/{id}?fields=organisationUnitGroups[id]", ouId)
            .content(HttpStatus.OK)
            .getArray("organisationUnitGroups"));
  }

  private Statistics statistics() {
    Statistics statistics =
        entityManagerFactory.unwrap(SessionFactoryImplementor.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    return statistics;
  }

  private int countMembers() {
    return jdbcTemplate.queryForObject(
        "select count(*) from orgunitgroupmembers m"
            + " join orgunitgroup g on g.orgunitgroupid = m.orgunitgroupid where g.uid = ?",
        Integer.class,
        groupId);
  }

  private String groupPath(String ouId) {
    return "/organisationUnitGroups/" + groupId + "/organisationUnits/" + ouId;
  }

  private String orgUnitPath(String ouId) {
    return "/organisationUnits/" + ouId + "/organisationUnitGroups/" + groupId;
  }

  private static void assertStats(JsonObject message, int updated, int deleted, int ignored) {
    JsonStats stats =
        message.as(JsonWebMessage.class).getResponse().as(JsonTypeReport.class).getStats();
    assertEquals(updated, stats.getUpdated(), "updated");
    assertEquals(deleted, stats.getDeleted(), "deleted");
    assertEquals(ignored, stats.getIgnored(), "ignored");
  }

  /** Checks the join table and both API sides agree on the group's members. */
  private void assertMembers(String... expected) {
    // read back as a new request would, not through entities left in the shared test session
    entityManager.clear();
    Set<String> expectedIds = Set.of(expected);
    List<String> rows =
        jdbcTemplate.queryForList(
            "select ou.uid from orgunitgroupmembers m"
                + " join orgunitgroup g on g.orgunitgroupid = m.orgunitgroupid"
                + " join organisationunit ou on ou.organisationunitid = m.organisationunitid"
                + " where g.uid = ?",
            String.class,
            groupId);
    assertEquals(expectedIds, Set.copyOf(rows), "join table");
    assertEquals(rows.size(), Set.copyOf(rows).size(), "no duplicate rows");

    JsonArray members =
        GET("/organisationUnitGroups/{id}?fields=organisationUnits[id]", groupId)
            .content(HttpStatus.OK)
            .getArray("organisationUnits");
    assertEquals(expectedIds, ids(members), "group side");

    for (String ouId : List.of(memberId, otherId)) {
      JsonArray groups =
          GET("/organisationUnits/{id}?fields=organisationUnitGroups[id]", ouId)
              .content(HttpStatus.OK)
              .getArray("organisationUnitGroups");
      assertEquals(
          expectedIds.contains(ouId) ? Set.of(groupId) : Set.of(), ids(groups), "org unit side");
    }
  }

  private static Set<String> ids(JsonArray array) {
    return array.asList(JsonObject.class).stream()
        .map(o -> o.getString("id").string())
        .collect(Collectors.toSet());
  }
}
