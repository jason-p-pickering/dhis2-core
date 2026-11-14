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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.hisp.dhis.category.CategoryService;
import org.hisp.dhis.expression.Expression;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitService;
import org.hisp.dhis.period.MonthlyPeriodType;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.period.PeriodService;
import org.hisp.dhis.period.PeriodType;
import org.hisp.dhis.test.integration.PostgresIntegrationTestBase;
import org.hisp.dhis.validation.ValidationResult;
import org.hisp.dhis.validation.ValidationRule;
import org.hisp.dhis.validation.ValidationRuleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Not transactional on purpose: the store's own transaction is only exercised when no outer test
 * transaction wraps the call. Data is committed, so it is cleaned up explicitly.
 */
class ValidationRuleViolationStoreAtomicityTest extends PostgresIntegrationTestBase {

  @Autowired private ValidationRuleViolationStore store;
  @Autowired private ValidationRuleService validationRuleService;
  @Autowired private PeriodService periodService;
  @Autowired private OrganisationUnitService organisationUnitService;
  @Autowired private CategoryService categoryService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transactionTemplate;

  private ValidationRule rule;
  private Period period;
  private OrganisationUnit unit;

  @AfterEach
  void cleanUp() {
    transactionTemplate.executeWithoutResult(
        status -> {
          if (rule != null) {
            validationRuleService.deleteValidationRule(
                validationRuleService.getValidationRule(rule.getId()));
          }
          if (unit != null) {
            organisationUnitService.deleteOrganisationUnit(
                organisationUnitService.getOrganisationUnit(unit.getId()));
          }
        });
    if (period != null) {
      jdbcTemplate.update("delete from period where periodid = ?", period.getId());
    }
  }

  @Test
  void replaceRollsBackDeleteWhenInsertFails() {
    PeriodType monthly = PeriodType.getPeriodTypeByName("Monthly");
    transactionTemplate.executeWithoutResult(
        status -> {
          period = createPeriod(new MonthlyPeriodType(), getDate(2017, 1, 1), getDate(2017, 1, 31));
          periodService.addPeriod(period);
          unit = createOrganisationUnit('A');
          organisationUnitService.addOrganisationUnit(unit);
          rule =
              createValidationRule(
                  'A',
                  equal_to,
                  new Expression("expressionA", "descriptionA"),
                  new Expression("expressionB", "descriptionB"),
                  monthly);
          validationRuleService.saveValidationRule(rule);
        });
    var defaultCoc = categoryService.getDefaultCategoryOptionCombo();
    store.replace(
        List.of(rule),
        period,
        List.of(new ValidationResult(rule, period, unit, defaultCoc, 1.0, 2.0, 3)));
    assertEquals(1, store.count(rule));

    // an organisation unit that was never saved has no row, so the insert violates the FK
    OrganisationUnit unsaved = createOrganisationUnit('Z');
    List<ValidationResult> failing =
        List.of(
            new ValidationResult(rule, period, unit, defaultCoc, 5.0, 6.0, 4),
            new ValidationResult(rule, period, unsaved, defaultCoc, 1.0, 2.0, 3));

    assertThrows(DataAccessException.class, () -> store.replace(List.of(rule), period, failing));

    assertEquals(1, store.count(rule), "the delete must be rolled back with the failed insert");
  }
}
