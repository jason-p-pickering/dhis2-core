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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.hisp.dhis.expression.Expression;
import org.hisp.dhis.test.integration.PostgresIntegrationTestBase;
import org.hisp.dhis.validation.ValidationRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ValidationRuleEligibilityTest extends PostgresIntegrationTestBase {

  @Autowired private ValidationRuleEligibility eligibility;

  private static ValidationRule rule(String left, String right) {
    ValidationRule rule = new ValidationRule();
    rule.setLeftSide(new Expression(left, "left"));
    rule.setRightSide(new Expression(right, "right"));
    return rule;
  }

  private void assertEligible(String left, String right) {
    assertEquals(Optional.empty(), eligibility.ineligibilityReason(rule(left, right)));
  }

  private void assertIneligible(ValidationRule rule) {
    Optional<String> reason = eligibility.ineligibilityReason(rule);
    assertTrue(reason.isPresent());
    assertTrue(!reason.get().isBlank());
  }

  @Test
  void dataElementIsEligible() {
    assertEligible("#{fbfJHSPpUQD}", "#{cYeuwXTCPkU}");
  }

  @Test
  void dataElementOperandIsEligible() {
    assertEligible("#{fbfJHSPpUQD.pq2XI5kz2BY}", "#{cYeuwXTCPkU.PT59n8BQbqM}");
  }

  @Test
  void constantIsEligible() {
    assertEligible("#{fbfJHSPpUQD} + C{Gfd3ppDfq8E}", "#{cYeuwXTCPkU}");
  }

  @Test
  void orgUnitGroupIsEligible() {
    assertEligible("OUG{CXw2yu5fodb}", "#{cYeuwXTCPkU}");
  }

  @Test
  void programIndicatorIsIneligible() {
    assertIneligible(rule("I{GxdhnY5wmHq}", "#{cYeuwXTCPkU}"));
  }

  @Test
  void programDataElementIsIneligible() {
    assertIneligible(rule("#{fbfJHSPpUQD}", "D{IpHINAT79UW.A03MvHHogjR}"));
  }

  @Test
  void programAttributeIsIneligible() {
    assertIneligible(rule("A{IpHINAT79UW.w75KJ2mc4zz}", "#{cYeuwXTCPkU}"));
  }

  @Test
  void slidingWindowLeftIsIneligible() {
    ValidationRule rule = rule("#{fbfJHSPpUQD}", "#{cYeuwXTCPkU}");
    rule.getLeftSide().setSlidingWindow(true);
    assertIneligible(rule);
  }

  @Test
  void slidingWindowRightIsIneligible() {
    ValidationRule rule = rule("#{fbfJHSPpUQD}", "#{cYeuwXTCPkU}");
    rule.getRightSide().setSlidingWindow(true);
    assertIneligible(rule);
  }
}
