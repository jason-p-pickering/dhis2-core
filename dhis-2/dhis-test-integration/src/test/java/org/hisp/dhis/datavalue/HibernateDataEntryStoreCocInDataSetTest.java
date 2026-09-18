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

/**
 * Regression test for {@link
 * org.hisp.dhis.datavalue.hibernate.HibernateDataEntryStore#getCocNotInDataSet(UID, UID, Stream)},
 * written before splitting the query into an uncached "resolve effective category combo" lookup and
 * a cached "COC uids belonging to that category combo" lookup. This test exercises existing,
 * correct behavior and is expected to pass both before and after that refactor -- it exists as a
 * regression-safety net, not to drive new behavior.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
class HibernateDataEntryStoreCocInDataSetTest extends PostgresIntegrationTestBase {

  @Autowired private DataEntryStore dataEntryStore;

  @Autowired private CategoryService categoryService;

  @Autowired private DataElementService dataElementService;

  @Autowired private DataSetService dataSetService;

  @Test
  void getCocNotInDataSet_flagsCocNotBelongingToEffectiveCategoryCombo() {
    CategoryCombo cc = createCategoryCombo('A');
    categoryService.addCategoryCombo(cc);
    CategoryOptionCombo validCoc = createCategoryOptionCombo(cc);
    categoryService.addCategoryOptionCombo(validCoc);
    cc.getOptionCombos().add(validCoc);
    categoryService.updateCategoryCombo(cc);

    DataElement de = createDataElement('A');
    de.setCategoryCombo(cc);
    dataElementService.addDataElement(de);
    DataSet ds = createDataSet('A');
    ds.addDataSetElement(de);
    dataSetService.addDataSet(ds);

    UID dsUid = UID.of(ds.getUid());
    UID deUid = UID.of(de.getUid());
    UID validCocUid = UID.of(validCoc.getUid());
    UID bogusCocUid = UID.of("bogusCoc011"); // not in cc's membership at all

    List<String> notInDs =
        dataEntryStore.getCocNotInDataSet(dsUid, deUid, Stream.of(validCocUid, bogusCocUid));

    assertEquals(List.of("bogusCoc011"), notInDs);
  }
}
