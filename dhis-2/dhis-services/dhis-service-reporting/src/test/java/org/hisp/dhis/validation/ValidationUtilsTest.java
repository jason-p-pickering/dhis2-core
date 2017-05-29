package org.hisp.dhis.validation;
/*
 * Copyright (c) 2004-2016, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
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

import org.hisp.dhis.expression.Expression;
import org.hisp.dhis.expression.MissingValueStrategy;
import org.hisp.dhis.expression.Operator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Stian Sandvold
 */
@RunWith( JUnit4.class )
public class ValidationUtilsTest
{

    @Test
    public void testEvaluateValidationRuleCompulsoryPair()
    {
        ValidationRule rule = new ValidationRule();
        rule.setOperator( Operator.compulsory_pair );

        assertFalse( ValidationUtils.evaluateValidationRule( null, 1d, rule ) );
        assertFalse( ValidationUtils.evaluateValidationRule( 1d, null, rule ) );

        assertTrue( ValidationUtils.evaluateValidationRule( null, null, rule ) );
        assertTrue( ValidationUtils.evaluateValidationRule( 1d, 1d, rule ) );
        assertTrue( ValidationUtils.evaluateValidationRule( 1d, 0d, rule ) );
    }

    @Test
    public void testEvaluateValidationRuleExclusivePair()
    {
        ValidationRule rule = new ValidationRule();
        rule.setOperator( Operator.exclusive_pair );

        assertTrue( ValidationUtils.evaluateValidationRule( null, 1d, rule ) );
        assertTrue( ValidationUtils.evaluateValidationRule( 1d, null, rule ) );

        assertTrue( ValidationUtils.evaluateValidationRule( null, null, rule ) );
        assertFalse( ValidationUtils.evaluateValidationRule( 1d, 1d, rule ) );
        assertFalse( ValidationUtils.evaluateValidationRule( 1d, 0d, rule ) );
    }

    @Test
    public void testEvaluateValidationRuleMissingValueStrategy()
    {
        Expression never_skip = new Expression();
        never_skip.setMissingValueStrategy( MissingValueStrategy.NEVER_SKIP );

        Expression skip = new Expression();
        skip.setMissingValueStrategy( MissingValueStrategy.SKIP_IF_ANY_VALUE_MISSING );

        ValidationRule rule = new ValidationRule();
        rule.setLeftSide( never_skip );
        rule.setRightSide( skip );
        rule.setOperator( Operator.equal_to );

        assertTrue( ValidationUtils.evaluateValidationRule( null, 0d, rule ) );
        assertTrue( ValidationUtils.evaluateValidationRule( 0d, null, rule ) );
        assertTrue( ValidationUtils.evaluateValidationRule( null, null, rule ) );
        assertTrue( ValidationUtils.evaluateValidationRule( 0d, 0d, rule ) );
    }

}
