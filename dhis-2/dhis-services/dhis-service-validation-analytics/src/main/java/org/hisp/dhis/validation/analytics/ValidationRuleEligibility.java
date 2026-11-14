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

import static org.hisp.dhis.expression.ParseType.VALIDATION_RULE_EXPRESSION;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hisp.dhis.common.DimensionItemType;
import org.hisp.dhis.common.DimensionalItemId;
import org.hisp.dhis.expression.Expression;
import org.hisp.dhis.expression.ExpressionInfo;
import org.hisp.dhis.expression.ExpressionParams;
import org.hisp.dhis.expression.ExpressionService;
import org.hisp.dhis.expression.ExpressionValidationOutcome;
import org.hisp.dhis.validation.ValidationRule;
import org.springframework.stereotype.Component;

/**
 * Decides which validation rules can be evaluated for validation rule analytics. Rules whose
 * expressions read values from analytics tables (program items) or that use sliding windows are not
 * eligible, since those values may be stale or aggregated over descendants.
 */
@Component
@RequiredArgsConstructor
public class ValidationRuleEligibility {

  private static final Set<DimensionItemType> ALLOWED_ITEM_TYPES =
      EnumSet.of(DimensionItemType.DATA_ELEMENT, DimensionItemType.DATA_ELEMENT_OPERAND);

  private final ExpressionService expressionService;

  /**
   * Never throws for unparseable or invalid expressions: they make the rule ineligible. Program
   * items are reported first, since they can be detected even when the expression is otherwise
   * invalid.
   *
   * @return empty if the rule is eligible, otherwise a short reason for the job log.
   */
  public Optional<String> ineligibilityReason(ValidationRule rule) {
    Optional<String> left = sideReason("left side", rule.getLeftSide());
    return left.isPresent() ? left : sideReason("right side", rule.getRightSide());
  }

  private Optional<String> sideReason(String side, Expression expression) {
    if (expression == null) {
      return Optional.empty();
    }
    if (Boolean.TRUE.equals(expression.getSlidingWindow())) {
      return Optional.of(side + " uses a sliding window");
    }
    ExpressionInfo info =
        expressionService.getExpressionInfo(
            ExpressionParams.builder()
                .expression(expression.getExpression())
                .parseType(VALIDATION_RULE_EXPRESSION)
                .build());
    for (DimensionalItemId id : info.getAllItemIds()) {
      if (!ALLOWED_ITEM_TYPES.contains(id.getDimensionItemType())) {
        return Optional.of(
            side + " uses an item of type " + id.getDimensionItemType() + " from analytics tables");
      }
    }
    // getExpressionInfo only logs parse errors and returns partial info, so validate explicitly.
    if (expressionService.expressionIsValid(expression.getExpression(), VALIDATION_RULE_EXPRESSION)
        != ExpressionValidationOutcome.VALID) {
      return Optional.of(side + " has an invalid expression");
    }
    return Optional.empty();
  }
}
