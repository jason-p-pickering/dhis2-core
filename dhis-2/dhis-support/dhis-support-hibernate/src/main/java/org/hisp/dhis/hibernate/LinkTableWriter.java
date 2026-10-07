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
package org.hisp.dhis.hibernate;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.action.spi.AfterTransactionCompletionProcess;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.collection.QueryableCollection;
import org.hibernate.type.SetType;
import org.springframework.stereotype.Component;

/**
 * Adds or removes a single row of an owning many-to-many join table without initializing the
 * collection. Changing a Hibernate owning collection always loads it in full first, which for large
 * org unit sets means hydrating hundreds of thousands of entities to write one row.
 *
 * <p>Only roles on an explicit allowlist that have no second-level cache are supported, so the
 * owning side has no cached copy that a direct write could leave stale. If a cache is configured
 * for such a role later, {@link #supports(String)} returns false and callers keep using the
 * collection. The inverse collection entry of the changed element is evicted the same way Hibernate
 * evicts a {@code nonstrict-read-write} collection it updates itself: once when the statement runs
 * and once after the transaction completes.
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
@Component
@RequiredArgsConstructor
public class LinkTableWriter {

  /** Owning collection roles that may be written directly. */
  private static final Set<String> ROLES =
      Set.of("org.hisp.dhis.organisationunit.OrganisationUnitGroup.members");

  private final EntityManager entityManager;

  /**
   * @param role the owning collection role, e.g. {@code
   *     org.hisp.dhis.organisationunit.OrganisationUnitGroup.members}
   * @return true if rows of this role can be added and removed with this writer
   */
  public boolean supports(String role) {
    return ROLES.contains(role) && queryable(role).isPresent();
  }

  /**
   * Adds the link between owner and element, if it does not exist yet.
   *
   * @return true if a row was inserted, false if the link already existed
   */
  public boolean add(String role, long ownerId, long elementId) {
    QueryableCollection collection = requireQueryable(role);
    String sql =
        "insert into %s (%s, %s) values (:owner, :element) on conflict do nothing"
            .formatted(collection.getTableName(), keyColumn(collection), elementColumn(collection));
    return execute(collection, sql, ownerId, elementId) > 0;
  }

  /**
   * Removes the link between owner and element, if it exists.
   *
   * @return true if a row was deleted, false if there was no such link
   */
  public boolean remove(String role, long ownerId, long elementId) {
    QueryableCollection collection = requireQueryable(role);
    String sql =
        "delete from %s where %s = :owner and %s = :element"
            .formatted(collection.getTableName(), keyColumn(collection), elementColumn(collection));
    return execute(collection, sql, ownerId, elementId) > 0;
  }

  private int execute(QueryableCollection collection, String sql, long ownerId, long elementId) {
    int rows =
        entityManager
            .unwrap(Session.class)
            .createNativeQuery(sql)
            // flushes pending changes to this table first, and bumps query cache timestamps
            .addSynchronizedQuerySpace(collection.getTableName())
            .setParameter("owner", ownerId)
            .setParameter("element", elementId)
            .executeUpdate();
    if (rows > 0) {
      evictInverse(collection, elementId);
    }
    return rows;
  }

  private void evictInverse(QueryableCollection collection, long elementId) {
    Optional<String> inverseRole = inverseRole(collection);
    if (inverseRole.isEmpty()) {
      return;
    }
    String role = inverseRole.get();
    SessionFactoryImplementor factory = sessionFactory();
    factory.getCache().evictCollectionData(role, elementId);
    entityManager
        .unwrap(SessionImplementor.class)
        .getActionQueue()
        .registerProcess(
            (AfterTransactionCompletionProcess)
                (success, session) -> factory.getCache().evictCollectionData(role, elementId));
  }

  /** The cached inverse role mapped to the same join table, e.g. OrganisationUnit.groups. */
  private Optional<String> inverseRole(QueryableCollection owning) {
    return sessionFactory().getMetamodel().collectionPersisters().values().stream()
        .filter(CollectionPersister::isInverse)
        .filter(CollectionPersister::hasCache)
        .filter(QueryableCollection.class::isInstance)
        .map(QueryableCollection.class::cast)
        .filter(inverse -> inverse.getTableName().equals(owning.getTableName()))
        .filter(inverse -> keyColumn(inverse).equals(elementColumn(owning)))
        .map(CollectionPersister::getRole)
        .findFirst();
  }

  private QueryableCollection requireQueryable(String role) {
    if (!ROLES.contains(role)) {
      throw new IllegalArgumentException("Collection role not supported: " + role);
    }
    return queryable(role)
        .orElseThrow(() -> new IllegalStateException("Collection role not eligible: " + role));
  }

  private Optional<QueryableCollection> queryable(String role) {
    CollectionPersister persister = sessionFactory().getMetamodel().collectionPersister(role);
    if (persister.hasCache()
        || persister.isInverse()
        || !persister.isManyToMany()
        || persister.hasIndex()
        || !(persister.getCollectionType() instanceof SetType)
        || !(persister instanceof QueryableCollection collection)
        || collection.hasWhere()
        || collection.getKeyColumnNames().length != 1
        || collection.getElementColumnNames().length != 1) {
      return Optional.empty();
    }
    return Optional.of(collection);
  }

  private static String keyColumn(QueryableCollection collection) {
    return collection.getKeyColumnNames()[0];
  }

  private static String elementColumn(QueryableCollection collection) {
    return collection.getElementColumnNames()[0];
  }

  private SessionFactoryImplementor sessionFactory() {
    return entityManager.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class);
  }
}
