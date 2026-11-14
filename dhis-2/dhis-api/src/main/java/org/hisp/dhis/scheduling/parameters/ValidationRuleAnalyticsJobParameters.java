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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.OpenApi;
import org.hisp.dhis.common.UID;
import org.hisp.dhis.feedback.ErrorCode;
import org.hisp.dhis.feedback.ErrorReport;
import org.hisp.dhis.scheduling.JobParameters;
import org.hisp.dhis.validation.ValidationRuleGroup;
import org.hisp.dhis.validation.analytics.Lookback;

/** Parameters of the validation rule analytics job. */
@Getter
@Setter
@NoArgsConstructor
public class ValidationRuleAnalyticsJobParameters implements JobParameters {

  @JsonProperty
  @OpenApi.Property({UID[].class, ValidationRuleGroup.class})
  private List<String> validationRuleGroups = new ArrayList<>();

  /** Look-back window, e.g. "3 months" (see {@link Lookback}). */
  @JsonProperty private String lookback = "12 months";

  @Override
  public Optional<ErrorReport> validate() {
    if (validationRuleGroups == null || validationRuleGroups.isEmpty()) {
      return Optional.of(new ErrorReport(this.getClass(), ErrorCode.E4000, "validationRuleGroups"));
    }
    for (String group : validationRuleGroups) {
      if (!CodeGenerator.isValidUid(group)) {
        return Optional.of(
            new ErrorReport(this.getClass(), ErrorCode.E4014, group, "validationRuleGroups"));
      }
    }
    try {
      Lookback.parse(lookback);
    } catch (IllegalArgumentException ex) {
      return Optional.of(new ErrorReport(this.getClass(), ErrorCode.E4027, lookback, "lookback"));
    }
    return Optional.empty();
  }
}
