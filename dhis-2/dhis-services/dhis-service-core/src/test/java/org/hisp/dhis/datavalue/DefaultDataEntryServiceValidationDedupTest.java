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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.hisp.dhis.common.UID;
import org.hisp.dhis.common.ValueType;
import org.hisp.dhis.feedback.ConflictException;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.scheduling.JobProgress;
import org.hisp.dhis.user.CurrentUserUtil;
import org.hisp.dhis.user.UserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Confirms {@link DefaultDataEntryService#upsertGroup} de-duplicates data elements (and, in the
 * AOC/org-unit-hierarchy check, attribute option combos) before issuing per-item validation
 * queries, so a bulk import with many rows sharing the same data element issues one validation
 * query per distinct data element, not one per row.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class DefaultDataEntryServiceValidationDedupTest {

  private static final UID DATA_SET = UID.of("ds123456789");
  private static final UID ORG_UNIT = UID.of("ou123456789");
  private static final UID DE_A = UID.of("deAAAAAAAAA");
  private static final UID DE_B = UID.of("deBBBBBBBBB");
  private static final UID COC_1 = UID.of("coc1111111a");
  private static final UID COC_2 = UID.of("coc2222222a");
  private static final UID COC_3 = UID.of("coc3333333a");
  private static final Period PERIOD = Period.of("2025");

  @Mock private DataEntryStore store;
  @Mock private org.hisp.dhis.common.IdCoder idCoder;
  @Mock private DataEntryAuditService audit;

  private DefaultDataEntryService service;

  @BeforeEach
  void setUp() {
    service = new DefaultDataEntryService(store, idCoder, audit);
    CurrentUserUtil.injectUserInSecurityContext(
        UserDetails.empty().username("tester").isSuper(true).build());
    when(store.getValueTypeByDataElements(any()))
        .thenReturn(
            Map.of(
                DE_A.getValue(), ValueType.TEXT,
                DE_B.getValue(), ValueType.TEXT));
  }

  @AfterEach
  void tearDown() {
    CurrentUserUtil.clearSecurityContext();
  }

  @Test
  void upsertGroup_queriesCocNotInDataSetOncePerDistinctDataElement_notOncePerRow()
      throws ConflictException {
    // Two rows share DE_A (different COC keeps the row keys distinct), one row uses DE_B.
    List<DataEntryValue> values = List.of(row(DE_A, COC_1), row(DE_A, COC_2), row(DE_B, COC_3));
    DataEntryGroup group = new DataEntryGroup(DATA_SET, null, null, values);

    // JobProgress.noop()'s default runStageAndRethrow swallows any exception that isn't the
    // declared type, which would hide a real failure as a confusing later NPE. This mock instead
    // lets the actual work run and any exception propagate directly.
    JobProgress progress = mock(JobProgress.class);
    when(progress.runStageAndRethrow(any(), any(Callable.class)))
        .thenAnswer(inv -> inv.<Callable<?>>getArgument(1).call());
    when(progress.runStage(any(), any(Callable.class)))
        .thenAnswer(inv -> inv.<Callable<?>>getArgument(1).call());

    service.upsertGroup(new DataEntryGroup.Options(false, false, true), group, progress);

    // Without de-duplication this would be called twice for DE_A (once per row).
    verify(store, times(1)).getCocNotInDataSet(eq(DATA_SET), eq(DE_A), any());
    verify(store, times(1)).getCocNotInDataSet(eq(DATA_SET), eq(DE_B), any());
  }

  private static DataEntryValue row(UID dataElement, UID coc) {
    return new DataEntryValue(0, dataElement, ORG_UNIT, coc, null, PERIOD, "1", null, null, null);
  }
}
