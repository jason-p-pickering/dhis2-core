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
 * Proves the open technical question this task was scoped to answer: does a Hibernate post-commit
 * event actually fire for a {@link CategoryOptionCombo} write that only touches the
 * secondary-table-mapped {@code categoryCombo} property (mapped via {@code @SecondaryTable
 * categorycombos_optioncombos}, see {@code CategoryOptionCombo.categoryCombo}), and does that event
 * reach {@link org.hisp.dhis.datavalue.hibernate.DataEntryCacheInvalidationListener} and invalidate
 * {@code HibernateDataEntryStore}'s COC-by-category-combo cache in time for the next read to see
 * the change.
 *
 * <p>{@code CategoryOptionCombo.categoryCombo} is {@code nullable = false}, so a COC can't be
 * "detached" to no category combo - the only way to change its category-combo membership is to
 * reassign it to a *different* {@link CategoryCombo} via {@link
 * CategoryOptionCombo#setCategoryCombo} + {@link
 * CategoryService#updateCategoryOptionCombo(CategoryOptionCombo)}. That update touches only the
 * secondary-table column (no primary-table field on the COC itself changes), which is exactly the
 * scenario in question. Needs {@code @ActiveProfiles("cache-test")} (same as Task 3's {@code
 * HibernateDataEntryStoreCocCachingTest}) so the caches are real, not size-0.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
// Do not use the annotation @ActiveProfiles, this is an exception as for now it's the only way to
// use a cache in our integration tests
@ActiveProfiles("cache-test")
class DataEntryCacheInvalidationIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private DataEntryStore dataEntryStore;

  @Autowired private CategoryService categoryService;

  @Autowired private DataElementService dataElementService;

  @Autowired private DataSetService dataSetService;

  @Test
  void
      categoryOptionComboReassignedToDifferentCc_invalidatesCache_membershipReflectsChangeImmediately() {
    CategoryCombo ccA = createCategoryCombo('A');
    categoryService.addCategoryCombo(ccA);
    CategoryCombo ccB = createCategoryCombo('B');
    categoryService.addCategoryCombo(ccB);

    CategoryOptionCombo coc = createCategoryOptionCombo(ccA);
    categoryService.addCategoryOptionCombo(coc);
    ccA.getOptionCombos().add(coc);
    categoryService.updateCategoryCombo(ccA);

    DataElement de = createDataElement('A');
    de.setCategoryCombo(ccA);
    dataElementService.addDataElement(de);
    DataSet ds = createDataSet('A');
    ds.addDataSetElement(de);
    dataSetService.addDataSet(ds);

    UID dsUid = UID.of(ds.getUid());
    UID deUid = UID.of(de.getUid());
    UID cocUid = UID.of(coc.getUid());

    // populate the cache: coc is currently a valid member of ccA, so "not in data set" is empty
    List<String> before = dataEntryStore.getCocNotInDataSet(dsUid, deUid, Stream.of(cocUid));
    assertEquals(List.of(), before);

    // reassign coc from ccA to ccB - a CategoryOptionCombo update that touches only the
    // secondary-table categoryCombo mapping (categorycombos_optioncombos), not any primary-table
    // column on categoryoptioncombo itself
    coc.setCategoryCombo(ccB);
    categoryService.updateCategoryOptionCombo(coc);

    // if the listener's PostUpdateEvent fired and invalidated the cache, this now sees coc as no
    // longer a member of ccA
    List<String> after = dataEntryStore.getCocNotInDataSet(dsUid, deUid, Stream.of(cocUid));
    assertEquals(List.of(coc.getUid()), after);
  }
}
