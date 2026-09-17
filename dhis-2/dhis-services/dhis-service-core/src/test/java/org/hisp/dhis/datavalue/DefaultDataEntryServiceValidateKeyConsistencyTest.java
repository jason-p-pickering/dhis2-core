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
package org.hisp.dhis.datavalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.hisp.dhis.common.IdCoder;
import org.hisp.dhis.common.UID;
import org.hisp.dhis.feedback.ConflictException;
import org.hisp.dhis.feedback.ErrorCode;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.scheduling.JobProgress;
import org.hisp.dhis.user.UserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tests that {@link DefaultDataEntryService}'s key-consistency validation checks the COC-in-CC
 * relationship once per distinct data element in the batch, not once per data value. A trace of a
 * 132-value {@code /api/dataValueSets} import showed the underlying store query executed 132 times,
 * matching the row count rather than the number of distinct data elements.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class DefaultDataEntryServiceValidateKeyConsistencyTest {

  private static final UID DATA_SET = UID.of("ds123456789");
  private static final UID DATA_ELEMENT_A = UID.of("deAAAAAAAAA");
  private static final UID DATA_ELEMENT_B = UID.of("deBBBBBBBBB");
  private static final UID ORG_UNIT_1 = UID.of("ou1AAAAAAAA");
  private static final UID ORG_UNIT_2 = UID.of("ou2AAAAAAAA");
  private static final UID DEFAULT_COC = UID.of("cocDefault1");
  private static final Period PERIOD = Period.of("2025");

  @Mock private DataEntryStore store;
  @Mock private IdCoder idCoder;
  @Mock private DataEntryAuditService audit;
  @Mock private UserDetails superUser;

  private DefaultDataEntryService service;

  @BeforeEach
  void setUp() {
    service = new DefaultDataEntryService(store, idCoder, audit);

    when(superUser.isSuper()).thenReturn(true);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(superUser, null, List.of()));

    when(store.getDataElementsNotInDataSet(any(), any())).thenReturn(List.of());
    when(store.getOrgUnitsNotInDataSet(any(), any())).thenReturn(List.of());
    when(store.getIsoPeriodsNotUsableInDataSet(any(), any())).thenReturn(List.of());
    when(store.getAocNotInDataSet(any(), any(), any())).thenReturn(List.of());
    when(store.getCocNotInDataSet(any(), any(), any(), any())).thenReturn(List.of());
    when(store.getAocWithOrgUnitHierarchy(any())).thenReturn(List.of());
    when(store.getEntrySpanByOrgUnit(any(), any())).thenReturn(Map.of());
    when(store.getDefaultCategoryOptionCombo()).thenReturn(DEFAULT_COC);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static DataEntryValue deletedValue(int index, UID de, UID ou) {
    return new DataEntryValue(index, de, ou, null, null, PERIOD, null, null, null, true);
  }

  /** {@code force=true} + a super user skips ACL and timeliness checks not under test here. */
  private static DataEntryGroup.Options options() {
    return new DataEntryGroup.Options(true, false, true);
  }

  @Test
  void checksCocOncePerDistinctDataElement_notOncePerValue() throws ConflictException {
    // 2 values share DATA_ELEMENT_A, 1 uses DATA_ELEMENT_B => 2 distinct DEs, 3 rows
    DataEntryGroup group =
        new DataEntryGroup(
            DATA_SET,
            null,
            null,
            List.of(
                deletedValue(0, DATA_ELEMENT_A, ORG_UNIT_1),
                deletedValue(1, DATA_ELEMENT_A, ORG_UNIT_2),
                deletedValue(2, DATA_ELEMENT_B, ORG_UNIT_1)));

    service.upsertGroup(options(), group, JobProgress.noop());

    verify(store, times(1)).getCocNotInDataSet(eq(DATA_SET), eq(DATA_ELEMENT_A), any(), any());
    verify(store, times(1)).getCocNotInDataSet(eq(DATA_SET), eq(DATA_ELEMENT_B), any(), any());
  }

  @Test
  void checksCocForEveryDistinctDataElement_evenWhenOnlyOneRowPerDe() throws ConflictException {
    // proves the dedup does not skip a DE's own COC-in-CC check
    DataEntryGroup group =
        new DataEntryGroup(
            DATA_SET,
            null,
            null,
            List.of(
                deletedValue(0, DATA_ELEMENT_A, ORG_UNIT_1),
                deletedValue(1, DATA_ELEMENT_B, ORG_UNIT_1)));
    when(store.getCocNotInDataSet(eq(DATA_SET), eq(DATA_ELEMENT_B), any(), any()))
        .thenReturn(List.of("cocInvalid1"));

    ConflictException ex =
        assertThrows(
            ConflictException.class,
            () -> service.upsertGroup(options(), group, JobProgress.noop()));

    assertEquals(ErrorCode.E8024, ex.getCode());
    verify(store, times(1)).getCocNotInDataSet(eq(DATA_SET), eq(DATA_ELEMENT_A), any(), any());
    verify(store, times(1)).getCocNotInDataSet(eq(DATA_SET), eq(DATA_ELEMENT_B), any(), any());
  }

  @Test
  void resolvesDefaultCategoryOptionComboOnlyOncePerValidationPass() throws ConflictException {
    DataEntryGroup group =
        new DataEntryGroup(
            DATA_SET,
            null,
            null,
            List.of(
                deletedValue(0, DATA_ELEMENT_A, ORG_UNIT_1),
                deletedValue(1, DATA_ELEMENT_A, ORG_UNIT_2),
                deletedValue(2, DATA_ELEMENT_B, ORG_UNIT_1)));

    service.upsertGroup(options(), group, JobProgress.noop());

    verify(store, times(1)).getDefaultCategoryOptionCombo();
  }
}
