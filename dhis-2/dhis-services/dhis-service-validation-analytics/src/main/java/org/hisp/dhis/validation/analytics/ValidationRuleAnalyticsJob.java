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
package org.hisp.dhis.validation.analytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.period.PeriodService;
import org.hisp.dhis.period.PeriodType;
import org.hisp.dhis.scheduling.Job;
import org.hisp.dhis.scheduling.JobEntry;
import org.hisp.dhis.scheduling.JobProgress;
import org.hisp.dhis.scheduling.JobType;
import org.hisp.dhis.scheduling.parameters.ValidationRuleAnalyticsJobParameters;
import org.hisp.dhis.validation.ValidationResult;
import org.hisp.dhis.validation.ValidationRule;
import org.hisp.dhis.validation.ValidationRuleGroup;
import org.hisp.dhis.validation.ValidationRuleService;
import org.hisp.dhis.validation.ValidationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Evaluates the validation rules of the chosen rule groups over a look-back window and keeps the
 * current violations in the violation table, from which the regular analytics run builds the
 * validation result analytics table. The counts are a periodic snapshot, not real time.
 *
 * <p>This job does not touch analytics tables or services.
 *
 * <p>Transactions: the job as a whole is not transactional. Loading the rules and evaluating a
 * period each run in their own read-only transaction (for lazy loading), while {@link
 * ValidationRuleViolationStore#replace} runs afterwards in its own read-write transaction, because
 * a JDBC write inside a read-only transaction would fail.
 */
@Slf4j
@Component
public class ValidationRuleAnalyticsJob implements Job {

  private final ValidationService validationService;
  private final ValidationRuleService validationRuleService;
  private final PeriodService periodService;
  private final ValidationRuleEligibility eligibility;
  private final ValidationRuleViolationStore store;
  private final TransactionTemplate readOnly;

  public ValidationRuleAnalyticsJob(
      ValidationService validationService,
      ValidationRuleService validationRuleService,
      PeriodService periodService,
      ValidationRuleEligibility eligibility,
      ValidationRuleViolationStore store,
      PlatformTransactionManager transactionManager) {
    this.validationService = validationService;
    this.validationRuleService = validationRuleService;
    this.periodService = periodService;
    this.eligibility = eligibility;
    this.store = store;
    this.readOnly = new TransactionTemplate(transactionManager);
    this.readOnly.setReadOnly(true);
  }

  /** The eligible rules (by UID) of one period type, plus the periods to evaluate for them. */
  private record Work(PeriodType periodType, List<String> ruleUids, List<Period> periods) {}

  @Override
  public JobType getJobType() {
    return JobType.VALIDATION_RULE_ANALYTICS;
  }

  @Override
  public void execute(JobEntry config, JobProgress progress) {
    progress.startingProcess("Validation rule analytics");
    try {
      ValidationRuleAnalyticsJobParameters params =
          (ValidationRuleAnalyticsJobParameters) config.parameters();
      Date today = new Date();
      Date start = Lookback.parse(params.getLookback()).startDate(today);

      List<Work> work =
          readOnly.execute(status -> plan(params.getValidationRuleGroups(), start, today));
      if (work == null || work.isEmpty()) {
        progress.completedProcess("No eligible validation rules, nothing evaluated");
        return;
      }

      Map<String, Integer> written = new LinkedHashMap<>();
      for (Work w : work) {
        progress.startingStage(
            "Evaluating " + w.periodType().getName() + " rules", w.periods().size());
        progress.runStage(
            w.periods().stream()
                .<Runnable>map(period -> () -> evaluate(w, period, written))
                .toList());
      }
      written.forEach((uid, rows) -> log.info("Validation rule {}: {} violation rows", uid, rows));
      progress.completedProcess("Validation rule analytics done");
    } catch (RuntimeException ex) {
      progress.failedProcess(ex);
      throw ex;
    }
  }

  private List<Work> plan(List<String> groupUids, Date start, Date today) {
    Set<ValidationRule> rules = new LinkedHashSet<>();
    for (String uid : groupUids) {
      ValidationRuleGroup group = validationRuleService.getValidationRuleGroup(uid);
      if (group != null && group.getMembers() != null) {
        rules.addAll(group.getMembers());
      }
    }
    Map<PeriodType, List<String>> eligibleByType = new LinkedHashMap<>();
    for (ValidationRule rule : rules) {
      Optional<String> reason = eligibility.ineligibilityReason(rule);
      if (reason.isPresent()) {
        log.info("Skipping validation rule {}: {}", rule.getUid(), reason.get());
      } else {
        eligibleByType
            .computeIfAbsent(rule.getPeriodType(), t -> new ArrayList<>())
            .add(rule.getUid());
      }
    }
    List<Work> work = new ArrayList<>();
    eligibleByType.forEach(
        (type, uids) -> {
          Date windowStart = type.createPeriod(start).getStartDate();
          // the period store only returns periods that end within the window, so extend the
          // end to the end of the period that contains today to include the current period
          Date windowEnd = type.createPeriod(today).getEndDate();
          work.add(
              new Work(
                  type, uids, periodService.getPeriodsBetweenDates(type, windowStart, windowEnd)));
        });
    return work;
  }

  private void evaluate(Work work, Period period, Map<String, Integer> written) {
    Collection<ValidationRule> rules = new ArrayList<>();
    List<ValidationResult> results =
        readOnly.execute(
            status -> {
              work.ruleUids().stream()
                  .map(validationRuleService::getValidationRule)
                  .filter(Objects::nonNull)
                  .forEach(rules::add);
              return validationService.validationAnalysis(
                  validationService
                      .newParamsBuilder(rules, null, List.of(period))
                      .withIncludeOrgUnitDescendants(true)
                      .withMaxResults(Integer.MAX_VALUE)
                      .withIncludePersistedResults(false)
                      .withApplyUserDimensionConstraints(false)
                      .withPersistResults(false)
                      .withSendNotifications(false)
                      .build(),
                  JobProgress.noop());
            });
    if (results == null) {
      return;
    }
    store.replace(rules, period, results);
    for (ValidationResult r : results) {
      written.merge(r.getValidationRule().getUid(), 1, Integer::sum);
    }
  }
}
