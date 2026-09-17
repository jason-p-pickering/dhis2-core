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

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.hisp.dhis.datavalue.DataEntryStore;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.stereotype.Component;

/**
 * Registers {@link DataEntryCacheInvalidationListener} against Hibernate's post-commit insert/
 * update/delete events, the same way {@code DeletedObjectListenerConfigurer} registers its
 * listeners.
 *
 * <p>Builds the listener itself, rather than having Spring autowire it as a {@code @Component},
 * because {@code hibernateDataEntryStore} is exposed to Spring behind a JDK dynamic proxy (see
 * {@link org.hisp.dhis.config.HibernateConfig}'s transaction/exception-translation AOP, neither of
 * which sets {@code proxyTargetClass = true}): autowiring the listener's concrete-class {@code
 * HibernateDataEntryStore} constructor parameter directly fails with {@code
 * BeanNotOfRequiredTypeException} at context startup, since a JDK proxy only implements the {@link
 * DataEntryStore} interface, not the concrete class. Resolving the interface-typed bean here and
 * unwrapping it via {@link AopProxyUtils#getSingletonTarget} works around that.
 *
 * <p>An alternative was considered: {@code DefaultCacheProvider.handleCacheInvalidationEvent}
 * already provides a {@code Region}-keyed cache-invalidation channel via a plain Spring {@code
 * ApplicationEvent} ({@code CacheInvalidationEvent(source, Region)}), used elsewhere by {@code
 * SharingController}, which would have avoided needing the proxy-unwrap above entirely. Direct
 * method invalidation was kept instead: it is more directly traceable from write to invalidation
 * than an extra event hop, and this listener is already same-node-only, so there is no cross-node
 * dispatch need that the event channel would otherwise help with.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
@Component
public class DataEntryCacheInvalidationListenerConfigurer {
  @PersistenceUnit private EntityManagerFactory emf;

  private final DataEntryCacheInvalidationListener listener;

  public DataEntryCacheInvalidationListenerConfigurer(DataEntryStore dataEntryStore) {
    this.listener = new DataEntryCacheInvalidationListener(unwrap(dataEntryStore));
  }

  private static HibernateDataEntryStore unwrap(DataEntryStore dataEntryStore) {
    if (dataEntryStore instanceof HibernateDataEntryStore store) {
      return store;
    }
    Object target = AopProxyUtils.getSingletonTarget(dataEntryStore);
    if (target instanceof HibernateDataEntryStore store) {
      return store;
    }
    throw new IllegalStateException(
        "Expected the DataEntryStore bean to be (or proxy) a HibernateDataEntryStore, got: "
            + dataEntryStore.getClass());
  }

  @PostConstruct
  protected void init() {
    SessionFactoryImpl sessionFactory = emf.unwrap(SessionFactoryImpl.class);

    EventListenerRegistry registry =
        sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);

    registry.getEventListenerGroup(EventType.POST_COMMIT_INSERT).appendListener(listener);
    registry.getEventListenerGroup(EventType.POST_COMMIT_UPDATE).appendListener(listener);
    registry.getEventListenerGroup(EventType.POST_COMMIT_DELETE).appendListener(listener);
  }
}
