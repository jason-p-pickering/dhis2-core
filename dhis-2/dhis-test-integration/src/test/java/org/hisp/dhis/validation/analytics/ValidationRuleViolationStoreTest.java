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

import java.util.List;
import org.hisp.dhis.category.CategoryOptionCombo;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ValidationRuleViolationStoreTest extends PostgresIntegrationTestBase {

  @Autowired private ValidationRuleViolationStore store;
  @Autowired private ValidationRuleService validationRuleService;
  @Autowired private PeriodService periodService;
  @Autowired private OrganisationUnitService organisationUnitService;
  @Autowired private CategoryService categoryService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ValidationRule ruleA;
  private ValidationRule ruleB;
  private Period periodP;
  private Period periodP2;
  private OrganisationUnit unitA;
  private OrganisationUnit unitB;
  private CategoryOptionCombo defaultCoc;

  @BeforeEach
  void setUp() {
    PeriodType monthly = PeriodType.getPeriodTypeByName("Monthly");
    periodP = createPeriod(new MonthlyPeriodType(), getDate(2017, 1, 1), getDate(2017, 1, 31));
    periodP2 = createPeriod(new MonthlyPeriodType(), getDate(2017, 2, 1), getDate(2017, 2, 28));
    periodService.addPeriod(periodP);
    periodService.addPeriod(periodP2);
    unitA = createOrganisationUnit('A');
    unitB = createOrganisationUnit('B');
    organisationUnitService.addOrganisationUnit(unitA);
    organisationUnitService.addOrganisationUnit(unitB);
    defaultCoc = categoryService.getDefaultCategoryOptionCombo();
    ruleA =
        createValidationRule(
            'A',
            equal_to,
            new Expression("expressionA", "descriptionA"),
            new Expression("expressionB", "descriptionB"),
            monthly);
    ruleB =
        createValidationRule(
            'B',
            equal_to,
            new Expression("expressionB", "descriptionB"),
            new Expression("expressionA", "descriptionA"),
            monthly);
    validationRuleService.saveValidationRule(ruleA);
    validationRuleService.saveValidationRule(ruleB);
    // the store writes with JDBC, so the rows it references must be in the database
    entityManager.flush();
  }

  private ValidationResult result(ValidationRule rule, Period period, OrganisationUnit unit) {
    return new ValidationResult(rule, period, unit, defaultCoc, 1.0, 2.0, 3);
  }

  @Test
  void replaceInsertsResults() {
    store.replace(
        List.of(ruleA),
        periodP,
        List.of(result(ruleA, periodP, unitA), result(ruleA, periodP, unitB)));

    assertEquals(2, store.count(ruleA));
  }

  @Test
  void replaceRemovesRowsNoLongerViolating() {
    store.replace(
        List.of(ruleA),
        periodP,
        List.of(result(ruleA, periodP, unitA), result(ruleA, periodP, unitB)));

    store.replace(List.of(ruleA), periodP, List.of(result(ruleA, periodP, unitA)));

    assertEquals(1, store.count(ruleA));
  }

  @Test
  void replaceLeavesOtherPeriodsAndRulesAlone() {
    store.replace(List.of(ruleA), periodP2, List.of(result(ruleA, periodP2, unitA)));
    store.replace(List.of(ruleB), periodP, List.of(result(ruleB, periodP, unitA)));

    store.replace(List.of(ruleA), periodP, List.of());

    assertEquals(1, store.count(ruleA));
    assertEquals(1, store.count(ruleB));
  }

  @Test
  void deletingRuleWithViolationsSucceeds() {
    store.replace(List.of(ruleA), periodP, List.of(result(ruleA, periodP, unitA)));
    long ruleId = ruleA.getId();

    assertDoesNotThrow(() -> validationRuleService.deleteValidationRule(ruleA));
    entityManager.flush();

    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "select count(*) from validationruleviolation where validationruleid = ?",
            Long.class,
            ruleId));
  }

  @Test
  void deletingOrganisationUnitWithViolationsSucceeds() {
    store.replace(List.of(ruleA), periodP, List.of(result(ruleA, periodP, unitB)));

    assertDoesNotThrow(() -> organisationUnitService.deleteOrganisationUnit(unitB));
    entityManager.flush();

    assertEquals(0, store.count(ruleA));
  }
}
