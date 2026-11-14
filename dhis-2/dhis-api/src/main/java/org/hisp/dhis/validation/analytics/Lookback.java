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

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A look-back window such as "3 months" or "1 year", in the format used by the DQ Workbench: {@code
 * <positive int> <unit>} where unit is day(s), week(s), month(s) or year(s), case insensitive.
 */
public record Lookback(int amount, ChronoUnit unit) {

  private static final Pattern FORMAT =
      Pattern.compile("(\\d+)\\s+(day|week|month|year)s?", Pattern.CASE_INSENSITIVE);

  public static Lookback parse(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Lookback must not be null");
    }
    Matcher m = FORMAT.matcher(value.trim());
    if (!m.matches()) {
      throw new IllegalArgumentException(
          "Invalid lookback '%s', expected '<n> days|weeks|months|years'".formatted(value));
    }
    int amount;
    try {
      amount = Integer.parseInt(m.group(1));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Invalid lookback amount in '%s'".formatted(value), ex);
    }
    if (amount < 1) {
      throw new IllegalArgumentException("Lookback amount must be positive: '%s'".formatted(value));
    }
    ChronoUnit unit =
        switch (m.group(2).toLowerCase(Locale.ROOT)) {
          case "day" -> ChronoUnit.DAYS;
          case "week" -> ChronoUnit.WEEKS;
          case "month" -> ChronoUnit.MONTHS;
          default -> ChronoUnit.YEARS;
        };
    return new Lookback(amount, unit);
  }

  /** The start of the window: {@code today} minus this look-back (system time zone). */
  public Date startDate(Date today) {
    return Date.from(
        new Date(today.getTime())
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .minus(amount, unit)
            .toInstant());
  }
}
