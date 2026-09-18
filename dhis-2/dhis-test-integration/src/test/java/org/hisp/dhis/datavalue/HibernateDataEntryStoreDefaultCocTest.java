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

import org.hisp.dhis.category.Category;
import org.hisp.dhis.category.CategoryCombo;
import org.hisp.dhis.category.CategoryOption;
import org.hisp.dhis.category.CategoryOptionCombo;
import org.hisp.dhis.category.CategoryService;
import org.hisp.dhis.common.UID;
import org.hisp.dhis.test.integration.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests the canonical-UID-first, cached resolution of the default category option combo in {@link
 * org.hisp.dhis.datavalue.hibernate.HibernateDataEntryStore}.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
class HibernateDataEntryStoreDefaultCocTest extends PostgresIntegrationTestBase {

  private static final String CANONICAL_DEFAULT_COC_UID = "HllvX50cXC0";

  @Autowired private DataEntryStore dataEntryStore;

  @Autowired private CategoryService categoryService;

  @Test
  void getDefaultCategoryOptionCombo_prefersCanonicalUid_whenPresent() {
    // the bootstrap-seeded default COC (HllvX50cXC0) already exists in every fresh test DB;
    // no setup needed for the "canonical present" case
    UID defaultCoc = dataEntryStore.getDefaultCategoryOptionCombo();

    assertEquals(CANONICAL_DEFAULT_COC_UID, defaultCoc.getValue());
  }

  @Test
  void getDefaultCategoryOptionCombo_prefersCanonicalUid_overADuplicateNamedDefault() {
    // simulate the known production issue: a second COC also named 'default'
    CategoryOptionCombo duplicate = addDuplicateNamedDefaultCoc('Z', "dupDefault1");

    UID defaultCoc = dataEntryStore.getDefaultCategoryOptionCombo();

    assertEquals(CANONICAL_DEFAULT_COC_UID, defaultCoc.getValue());
    assertEquals("dupDefault1", duplicate.getUid());
  }

  @Test
  void getDefaultCategoryOptionCombo_fallsBackToNameQuery_whenCanonicalUidAbsent() {
    // rename the canonical COC away, then make a *different* COC the sole 'default'-named one,
    // simulating a non-standard install where the canonical UID isn't present
    CategoryOptionCombo canonical =
        categoryService.getCategoryOptionCombo(CANONICAL_DEFAULT_COC_UID);
    canonical.setName("default-renamed-for-test");
    categoryService.updateCategoryOptionCombo(canonical);

    addDuplicateNamedDefaultCoc('Y', "nonCanonDf1");

    UID defaultCoc = dataEntryStore.getDefaultCategoryOptionCombo();

    assertEquals("nonCanonDf1", defaultCoc.getValue());
  }

  /**
   * Creates and persists a fully independent category/categoryCombo/categoryOptionCombo so a second
   * row named 'default' can exist alongside the canonical one without colliding with it.
   */
  private CategoryOptionCombo addDuplicateNamedDefaultCoc(char uniqueChar, String uid) {
    CategoryOption option = createCategoryOption(uniqueChar);
    categoryService.addCategoryOption(option);
    Category category = createCategory(uniqueChar, option);
    categoryService.addCategory(category);
    CategoryCombo combo = createCategoryCombo(uniqueChar, category);
    categoryService.addCategoryCombo(combo);
    CategoryOptionCombo coc = createCategoryOptionCombo(combo, option);
    coc.setUid(uid);
    coc.setName("default");
    categoryService.addCategoryOptionCombo(coc);
    return coc;
  }
}
