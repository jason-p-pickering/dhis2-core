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
package org.hisp.dhis.datavalue.hibernate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.hisp.dhis.category.CategoryOptionCombo;

/**
 * Invalidates {@link HibernateDataEntryStore}'s default-COC and COC-by-category-combo caches
 * whenever a {@link CategoryOptionCombo} is inserted, updated, or deleted. Registered globally
 * against Hibernate's post-commit events by {@link DataEntryCacheInvalidationListenerConfigurer},
 * mirroring {@code DeletedObjectListenerConfigurer}'s registration pattern.
 *
 * <p>Invalidates both caches unconditionally rather than computing which is affected, to avoid
 * reading {@code CategoryOptionCombo.categoryCombo} (a secondary-table-mapped field) from inside a
 * post-commit callback. Both caches are small; over-invalidating on an unrelated COC write (e.g. a
 * rename) costs nothing.
 *
 * <p>Same-node only, a write committed on another node in a cluster is not seen by this listener,
 * so cross-node staleness still falls back to each cache's TTL.
 *
 * <p><b>Design assumption, audited but not permanently guaranteed:</b> this listener only sees
 * writes that go through Hibernate's per-entity save/update/delete lifecycle. A bulk native-SQL
 * write to {@code categorycombos_optioncombos} or {@code categoryoptioncombo}, or a bulk HQL {@code
 * UPDATE}, would bypass it entirely and silently stale both caches until their TTL expires. As of
 * this class's introduction, no such path exists for this specific relationship — verified by
 * auditing every category-combo/COC merge, metadata-import, and Liquibase code path in this
 * codebase. However, the general pattern of bypassing Hibernate events via bulk native SQL with
 * manual L2-cache sync does already exist elsewhere in this codebase for adjacent relationships
 * (see {@code HibernateCategoryComboStore.updateCatComboCategoryRefs} and {@code
 * HibernateCategoryStore.removeCatOptionCategoryRefs}, both used by Category-merge on different
 * join tables). If a similar bulk-reassignment path is ever added for {@code
 * CategoryOptionCombo.categoryCombo} specifically, this listener will not catch it and will need a
 * matching invalidation call added at that new call site.
 *
 * <p>Not a Spring-managed bean itself: {@link DataEntryCacheInvalidationListenerConfigurer}
 * constructs it directly, because the {@code hibernateDataEntryStore} bean is exposed to Spring
 * behind a JDK dynamic proxy (see {@link org.hisp.dhis.config.HibernateConfig}'s
 * {@code @EnableTransactionManagement}/{@code PersistenceExceptionTranslationPostProcessor},
 * neither of which sets {@code proxyTargetClass = true}); autowiring this constructor's
 * concrete-class parameter directly via {@code @Component} fails with {@code
 * BeanNotOfRequiredTypeException} at context startup, so the configurer resolves and unwraps the
 * proxy first.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
@Slf4j
@RequiredArgsConstructor
public class DataEntryCacheInvalidationListener
    implements PostCommitInsertEventListener,
        PostCommitUpdateEventListener,
        PostCommitDeleteEventListener {

  // Hibernate's PostInsert/PostUpdate/PostDeleteEventListener all extend Serializable, making
  // this class transitively Serializable even though it's just a Spring singleton registered
  // with the EventListenerRegistry and never actually serialized. transient satisfies that
  // contract without changing runtime behaviour.
  private final transient HibernateDataEntryStore store;

  @Override
  public void onPostInsert(PostInsertEvent event) {
    invalidate(event.getEntity());
  }

  @Override
  public void onPostUpdate(PostUpdateEvent event) {
    invalidate(event.getEntity());
  }

  @Override
  public void onPostDelete(PostDeleteEvent event) {
    invalidate(event.getEntity());
  }

  private void invalidate(Object entity) {
    if (entity instanceof CategoryOptionCombo) {
      store.invalidateDefaultCocCache();
      store.invalidateCocsByCategoryComboCache();
    }
  }

  @Override
  public boolean requiresPostCommitHanding(EntityPersister persister) {
    return true;
  }

  @Override
  public boolean requiresPostCommitHandling(EntityPersister persister) {
    return PostCommitUpdateEventListener.super.requiresPostCommitHandling(persister);
  }

  @Override
  public void onPostInsertCommitFailed(PostInsertEvent event) {
    log.debug("onPostInsertCommitFailed: " + event);
  }

  @Override
  public void onPostUpdateCommitFailed(PostUpdateEvent event) {
    log.debug("onPostUpdateCommitFailed: " + event);
  }

  @Override
  public void onPostDeleteCommitFailed(PostDeleteEvent event) {
    log.debug("onPostDeleteCommitFailed: " + event);
  }
}
