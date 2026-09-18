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

import java.util.List;
import java.util.stream.Stream;
import org.hisp.dhis.category.CategoryCombo;
import org.hisp.dhis.category.CategoryOptionCombo;
import org.hisp.dhis.category.CategoryService;
import org.hisp.dhis.common.UID;
import org.hisp.dhis.dataelement.DataElement;
import org.hisp.dhis.dataelement.DataElementService;
import org.hisp.dhis.dataset.DataSet;
import org.hisp.dhis.dataset.DataSetService;
import org.hisp.dhis.test.integration.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the actual caching behavior of {@link
 * org.hisp.dhis.datavalue.hibernate.HibernateDataEntryStore#getCocNotInDataSet(UID, UID, Stream)}'s
 * category-combo -> valid-COC-uid-set cache. Needs real caching to be observable, so it opts into
 * the {@code cache-test} profile (same pattern as {@code TrackedEntityCacheTest}).
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
// Do not use the annotation @ActiveProfiles, this is an exception as for now it's the only way to
// use a cache in our integration tests
@ActiveProfiles("cache-test")
class HibernateDataEntryStoreCocCachingTest extends PostgresIntegrationTestBase {

  @Autowired private DataEntryStore dataEntryStore;

  @Autowired private CategoryService categoryService;

  @Autowired private DataElementService dataElementService;

  @Autowired private DataSetService dataSetService;

  @Test
  void getCocNotInDataSet_reusesCachedMembership_forSecondDistinctDataElement_sameCc() {
    CategoryCombo cc = createCategoryCombo('A');
    categoryService.addCategoryCombo(cc);
    CategoryOptionCombo coc = createCategoryOptionCombo(cc);
    categoryService.addCategoryOptionCombo(coc);
    cc.getOptionCombos().add(coc);
    categoryService.updateCategoryCombo(cc);

    DataElement de1 = createDataElement('A');
    de1.setCategoryCombo(cc);
    dataElementService.addDataElement(de1);
    DataElement de2 = createDataElement('B');
    de2.setCategoryCombo(cc);
    dataElementService.addDataElement(de2);

    DataSet ds = createDataSet('A');
    ds.addDataSetElement(de1);
    ds.addDataSetElement(de2);
    dataSetService.addDataSet(ds);

    UID dsUid = UID.of(ds.getUid());
    UID cocUid = UID.of(coc.getUid());

    // first call populates the cache for cc's id
    dataEntryStore.getCocNotInDataSet(dsUid, UID.of(de1.getUid()), Stream.of(cocUid));

    // second call, different DE but SAME cc: must still correctly validate without re-querying
    // membership (can't assert "no query ran" at this level without a profiler/spy on the
    // Session, so this test proves *correctness under caching*, not query count - query-count
    // proof is what mutation-testing this task's invalidation logic in Task 4 is for)
    List<String> result =
        dataEntryStore.getCocNotInDataSet(dsUid, UID.of(de2.getUid()), Stream.of(cocUid));

    assertEquals(List.of(), result); // coc is valid for both DEs sharing the same cc
  }
}
