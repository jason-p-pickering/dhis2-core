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
package org.hisp.dhis.scheduling.parameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.hisp.dhis.feedback.ErrorCode;
import org.hisp.dhis.feedback.ErrorReport;
import org.junit.jupiter.api.Test;

class ValidationRuleAnalyticsJobParametersTest {

  @Test
  void testDefaultLookback() {
    assertEquals("12 months", new ValidationRuleAnalyticsJobParameters().getLookback());
  }

  @Test
  void testValid() {
    ValidationRuleAnalyticsJobParameters p = new ValidationRuleAnalyticsJobParameters();
    p.setValidationRuleGroups(List.of("abcdefghij1"));
    assertEquals(Optional.empty(), p.validate());
  }

  @Test
  void testEmptyGroups() {
    ValidationRuleAnalyticsJobParameters p = new ValidationRuleAnalyticsJobParameters();
    Optional<ErrorReport> error = p.validate();
    assertTrue(error.isPresent());
    assertEquals(ErrorCode.E4000, error.get().getErrorCode());
    assertTrue(error.get().getErrorProperties().contains("validationRuleGroups"));
  }

  @Test
  void testInvalidUid() {
    ValidationRuleAnalyticsJobParameters p = new ValidationRuleAnalyticsJobParameters();
    p.setValidationRuleGroups(List.of("abcdefghij1", "bad"));
    Optional<ErrorReport> error = p.validate();
    assertTrue(error.isPresent());
    assertEquals(ErrorCode.E4014, error.get().getErrorCode());
    assertEquals(List.of("bad", "validationRuleGroups"), error.get().getErrorProperties());
  }

  @Test
  void testInvalidLookback() {
    ValidationRuleAnalyticsJobParameters p = new ValidationRuleAnalyticsJobParameters();
    p.setValidationRuleGroups(List.of("abcdefghij1"));
    p.setLookback("3 fortnights");
    Optional<ErrorReport> error = p.validate();
    assertTrue(error.isPresent());
    assertTrue(error.get().getMessage().contains("lookback"));
  }
}
