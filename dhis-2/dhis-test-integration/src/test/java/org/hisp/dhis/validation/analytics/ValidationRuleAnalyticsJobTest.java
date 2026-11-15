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

import static org.hisp.dhis.expression.Operator.equal_to;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.hisp.dhis.category.CategoryService;
import org.hisp.dhis.common.UID;
import org.hisp.dhis.dataelement.DataElement;
import org.hisp.dhis.dataelement.DataElementService;
import org.hisp.dhis.dataset.DataSet;
import org.hisp.dhis.dataset.DataSetService;
import org.hisp.dhis.datavalue.DataDumpService;
import org.hisp.dhis.datavalue.DataValue;
import org.hisp.dhis.expression.Expression;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitService;
import org.hisp.dhis.period.MonthlyPeriodType;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.period.PeriodService;
import org.hisp.dhis.period.PeriodType;
import org.hisp.dhis.scheduling.JobEntry;
import org.hisp.dhis.scheduling.JobProgress;
import org.hisp.dhis.scheduling.JobType;
import org.hisp.dhis.scheduling.parameters.ValidationRuleAnalyticsJobParameters;
import org.hisp.dhis.test.integration.PostgresIntegrationTestBase;
import org.hisp.dhis.user.User;
import org.hisp.dhis.validation.ValidationResult;
import org.hisp.dhis.validation.ValidationRule;
import org.hisp.dhis.validation.ValidationRuleGroup;
import org.hisp.dhis.validation.ValidationRuleService;
import org.hisp.dhis.validation.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ValidationRuleAnalyticsJobTest extends PostgresIntegrationTestBase {

  @Autowired private ValidationRuleAnalyticsJob job;
  @Autowired private ValidationRuleViolationStore store;
  @Autowired private ValidationRuleService validationRuleService;
  @Autowired private DataElementService dataElementService;
  @Autowired private DataSetService dataSetService;
  @Autowired private DataDumpService dataDumpService;
  @Autowired private PeriodService periodService;
  @Autowired private OrganisationUnitService organisationUnitService;
  @Autowired private CategoryService categoryService;
  @Autowired private ValidationService validationService;
  @Autowired private ValidationRuleEligibility eligibility;
  @Autowired private PlatformTransactionManager transactionManager;

  private final PeriodType monthly = new MonthlyPeriodType();
  private DataElement deA;
  private DataElement deB;
  private OrganisationUnit unit;
  private ValidationRule rule;
  private ValidationRuleGroup groupA;
  private ValidationRuleGroup groupB;

  @BeforeEach
  void setUp() {
    User user =
        createAndAddUser(true, "SUPERUSER", new java.util.HashSet<OrganisationUnit>(), null);
    injectSecurityContextUser(user);
    deA = createDataElement('A');
    deB = createDataElement('B');
    dataElementService.addDataElement(deA);
    dataElementService.addDataElement(deB);
    unit = createOrganisationUnit('A');
    organisationUnitService.addOrganisationUnit(unit);
    DataSet dataSet = createDataSet('M', monthly);
    dataSet.addOrganisationUnit(unit);
    dataSet.addDataSetElement(deA);
    dataSet.addDataSetElement(deB);
    dataSetService.addDataSet(dataSet);
    dataElementService.updateDataElement(deA);
    dataElementService.updateDataElement(deB);

    String suffix = "." + categoryService.getDefaultCategoryOptionCombo().getUid();
    rule =
        createValidationRule(
            'A',
            equal_to,
            new Expression("#{" + deA.getUid() + suffix + "}", "left"),
            new Expression("#{" + deB.getUid() + suffix + "}", "right"),
            monthly);
    validationRuleService.saveValidationRule(rule);
    groupA = createValidationRuleGroup('A');
    groupA.getMembers().add(rule);
    validationRuleService.addValidationRuleGroup(groupA);
    groupB = createValidationRuleGroup('B');
    groupB.getMembers().add(rule);
    validationRuleService.addValidationRuleGroup(groupB);
  }

  private Period monthsAgo(int months) {
    LocalDate date = LocalDate.now().minusMonths(months);
    Period period =
        monthly.createPeriod(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    periodService.addPeriod(period);
    entityManager.flush();
    return period;
  }

  private void value(DataElement de, Period period, String value) {
    var coc = categoryService.getDefaultCategoryOptionCombo();
    DataValue dv = createDataValue(de, period, unit, coc, coc, value);
    if (dataDumpService.upsertValues(dv) < 1) {
      throw new IllegalStateException("Failed to upsert test data");
    }
  }

  private void run(int lastMonths, ValidationRuleGroup... groups) {
    entityManager.flush();
    ValidationRuleAnalyticsJobParameters params = new ValidationRuleAnalyticsJobParameters();
    params.setLastMonths(lastMonths);
    params.setValidationRuleGroups(
        java.util.Arrays.stream(groups).map(ValidationRuleGroup::getUid).toList());
    job.execute(
        new JobEntry(UID.generate(), JobType.VALIDATION_RULE_ANALYTICS, params),
        JobProgress.noop());
  }

  @Test
  void violationCountedThenDisappearsWhenFixed() {
    Period period = monthsAgo(0);
    value(deA, period, "1");
    value(deB, period, "2");

    run(12, groupA);
    assertEquals(1, store.count(rule));

    value(deB, period, "1");
    run(12, groupA);
    assertEquals(0, store.count(rule));
  }

  @Test
  void periodOutsideWindowKeepsItsCount() {
    Period period = monthsAgo(6);
    value(deA, period, "1");
    value(deB, period, "2");

    run(12, groupA);
    assertEquals(1, store.count(rule));

    value(deB, period, "1");
    run(1, groupA);
    assertEquals(1, store.count(rule));
  }

  @Test
  void ruleInTwoGroupsEvaluatedOnce() {
    Period period = monthsAgo(0);
    value(deA, period, "1");
    value(deB, period, "2");

    run(12, groupA, groupB);

    assertEquals(1, store.count(rule));
  }

  @Test
  void allIneligibleRulesDeleteNothing() {
    ValidationRule ineligible =
        createValidationRule(
            'I',
            equal_to,
            new Expression("I{GxdhnY5wmHq}", "left"),
            new Expression("#{" + deB.getUid() + "}", "right"),
            monthly);
    validationRuleService.saveValidationRule(ineligible);
    ValidationRuleGroup group = createValidationRuleGroup('I');
    group.getMembers().add(ineligible);
    validationRuleService.addValidationRuleGroup(group);
    Period period = monthsAgo(0);
    entityManager.flush();
    store.replace(
        List.of(ineligible),
        period,
        List.of(
            new ValidationResult(
                ineligible,
                period,
                unit,
                categoryService.getDefaultCategoryOptionCombo(),
                1.0,
                2.0,
                3)));

    run(12, group);

    assertEquals(1, store.count(ineligible));
  }

  @Test
  void jobSucceedsWithoutAnalyticsTables() {
    Period period = monthsAgo(0);
    value(deA, period, "1");
    value(deB, period, "2");

    assertDoesNotThrow(() -> run(12, groupA));

    assertEquals(1, store.count(rule));
  }

  @Test
  void failedEvaluationKeepsExistingViolationsAndFailsJob() {
    Period period = monthsAgo(0);
    value(deA, period, "1");
    value(deB, period, "2");
    run(12, groupA);
    assertEquals(1, store.count(rule));

    // the real analysis skips failing chunks and only reports them to the progress
    ValidationService failing =
        mock(ValidationService.class, AdditionalAnswers.delegatesTo(validationService));
    doAnswer(
            inv -> {
              ((JobProgress) inv.getArgument(1)).failedWorkItem("chunk failed");
              return List.<ValidationResult>of();
            })
        .when(failing)
        .validationAnalysis(any(), any());
    ValidationRuleAnalyticsJob failingJob =
        new ValidationRuleAnalyticsJob(
            failing, validationRuleService, periodService, eligibility, store, transactionManager);
    ValidationRuleAnalyticsJobParameters params = new ValidationRuleAnalyticsJobParameters();
    params.setLastMonths(12);
    params.setValidationRuleGroups(List.of(groupA.getUid()));
    JobProgress progress = mock(JobProgress.class, CALLS_REAL_METHODS);

    failingJob.execute(
        new JobEntry(UID.generate(), JobType.VALIDATION_RULE_ANALYTICS, params), progress);

    assertEquals(1, store.count(rule), "partial results must not replace the existing rows");
    verify(progress).failedProcess(contains("failed period"), any());
  }
}
