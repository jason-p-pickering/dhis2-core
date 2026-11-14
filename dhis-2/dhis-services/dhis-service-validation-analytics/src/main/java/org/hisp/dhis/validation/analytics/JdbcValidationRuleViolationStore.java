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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.validation.ValidationResult;
import org.hisp.dhis.validation.ValidationRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcValidationRuleViolationStore implements ValidationRuleViolationStore {

  private static final String DELETE_SQL =
      "delete from validationruleviolation where validationruleid = ? and periodid = ?";

  private static final String INSERT_SQL =
      """
      insert into validationruleviolation
        (validationruleid, periodid, organisationunitid, attributeoptioncomboid, dayinperiod,
         leftsidevalue, rightsidevalue, lastupdated)
      values (?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  @Transactional
  public void replace(
      Collection<ValidationRule> rules, Period period, Collection<ValidationResult> results) {
    List<Object[]> deletes = new ArrayList<>();
    for (ValidationRule rule : rules) {
      deletes.add(new Object[] {rule.getId(), period.getId()});
    }
    jdbcTemplate.batchUpdate(DELETE_SQL, deletes);

    Timestamp now = new Timestamp(System.currentTimeMillis());
    List<Object[]> inserts = new ArrayList<>();
    for (ValidationResult r : results) {
      inserts.add(
          new Object[] {
            r.getValidationRule().getId(),
            r.getPeriod().getId(),
            r.getOrganisationUnit().getId(),
            r.getAttributeOptionCombo().getId(),
            r.getDayInPeriod(),
            r.getLeftsideValue(),
            r.getRightsideValue(),
            now
          });
    }
    jdbcTemplate.batchUpdate(INSERT_SQL, inserts);
  }

  @Override
  @Transactional(readOnly = true)
  public long count(ValidationRule rule) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from validationruleviolation where validationruleid = ?",
            Long.class,
            rule.getId());
    return count == null ? 0 : count;
  }
}
