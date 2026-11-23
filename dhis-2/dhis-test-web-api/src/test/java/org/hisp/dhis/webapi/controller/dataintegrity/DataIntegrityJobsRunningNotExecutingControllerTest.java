/*
 * Copyright (c) 2004-2022, University of Oslo
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
package org.hisp.dhis.webapi.controller.dataintegrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.dataintegrity.DataIntegritySeverity;
import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.test.webapi.json.domain.JsonDataIntegrityCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests the programmatic {@code job_configurations_running_not_executing} data integrity check,
 * which reports jobs marked as running that this server does not run. In tests the scheduler runs
 * no job, so every job marked as running without a recent sign of life is reported.
 *
 * <p>The job store reads in its own session, so the fixture rows are committed in a separate
 * transaction and removed after each test.
 */
class DataIntegrityJobsRunningNotExecutingControllerTest
    extends AbstractDataIntegrityIntegrationTest {

  private static final String CHECK = "job_configurations_running_not_executing";

  private static final String DETAILS_ID_TYPE = "jobConfigurations";

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<String> inserted = new ArrayList<>();

  @AfterEach
  void removeJobs() {
    if (inserted.isEmpty()) return;
    inNewTransaction(
        () ->
            inserted.forEach(
                uid -> jdbcTemplate.update("delete from jobconfiguration where uid = ?", uid)));
    inserted.clear();
  }

  @Test
  void testCheckMetadata() {
    JsonList<JsonDataIntegrityCheck> checks =
        GET("/dataIntegrity?checks=" + CHECK).content().asList(JsonDataIntegrityCheck.class);
    assertEquals(1, checks.size());
    JsonDataIntegrityCheck check = checks.get(0);
    assertEquals(CHECK, check.getName());
    assertEquals("JCRNE", check.getCode());
    assertEquals("Jobs marked as running that this server does not run", check.getDisplayName());
    assertEquals("Job scheduler", check.getSection());
    assertEquals(DataIntegritySeverity.CRITICAL, check.getSeverity());
    assertTrue(check.getIsProgrammatic());
    assertFalse(check.getIsSlow(), "the check is cheap and should run by default");
  }

  @Test
  void testJobRunningWithoutSignOfLife() {
    String dead = insertJob("Analytics", "RUNNING", "2 hours");

    assertHasDataIntegrityIssues(
        DETAILS_ID_TYPE, CHECK, null, dead, "Analytics", "ANALYTICS_TABLE, started ", false);
  }

  @Test
  void testJobsRunningRecentlyOrNotRunning() {
    // just started: the scheduler may not have registered the run yet
    insertJob("Just started", "RUNNING", "10 seconds");
    insertJob("Finished", "SCHEDULED", "2 hours");

    assertHasNoDataIntegrityIssues(DETAILS_ID_TYPE, CHECK, false);
  }

  @Test
  void testNoJobs() {
    assertHasNoDataIntegrityIssues(DETAILS_ID_TYPE, CHECK, false);
  }

  /** Inserts an analytics job with the given status that was last alive the given time ago. */
  private String insertJob(String name, String status, String lastAlive) {
    String uid = CodeGenerator.generateUid();
    inNewTransaction(
        () ->
            jdbcTemplate.update(
                """
                insert into jobconfiguration (jobconfigurationid, uid, name, created, lastupdated,
                  jobtype, schedulingtype, enabled, jobstatus, lastexecutedstatus,
                  lastexecuted, lastalive)
                values (nextval('hibernate_sequence'), ?, ?, now(), now(),
                  'ANALYTICS_TABLE', 'ONCE_ASAP', true, ?, 'NOT_STARTED',
                  now() - ?::interval, now() - ?::interval)
                """,
                uid,
                name,
                status,
                lastAlive,
                lastAlive));
    inserted.add(uid);
    return uid;
  }

  private void inNewTransaction(Runnable action) {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.executeWithoutResult(status -> action.run());
  }
}
