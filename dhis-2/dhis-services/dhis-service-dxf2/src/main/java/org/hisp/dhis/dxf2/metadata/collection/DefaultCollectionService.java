/*
 * Copyright (c) 2004-2022, University of Oslo
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
package org.hisp.dhis.dxf2.metadata.collection;

import static java.util.stream.Collectors.toList;
import static org.hisp.dhis.dxf2.webmessage.WebMessageUtils.validateAndThrowErrors;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.common.IdentifiableObjects;
import org.hisp.dhis.feedback.BadRequestException;
import org.hisp.dhis.feedback.ConflictException;
import org.hisp.dhis.feedback.ErrorCode;
import org.hisp.dhis.feedback.ErrorReport;
import org.hisp.dhis.feedback.ForbiddenException;
import org.hisp.dhis.feedback.NotFoundException;
import org.hisp.dhis.feedback.ObjectReport;
import org.hisp.dhis.feedback.TypeReport;
import org.hisp.dhis.hibernate.HibernateProxyUtils;
import org.hisp.dhis.hibernate.LinkTableWriter;
import org.hisp.dhis.schema.Property;
import org.hisp.dhis.schema.Schema;
import org.hisp.dhis.schema.SchemaService;
import org.hisp.dhis.schema.validation.SchemaValidator;
import org.hisp.dhis.security.acl.AclService;
import org.hisp.dhis.user.CurrentUserUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
@Service
@RequiredArgsConstructor
public class DefaultCollectionService implements CollectionService {
  private final IdentifiableObjectManager manager;

  private final AclService aclService;

  private final SchemaService schemaService;

  private final SchemaValidator schemaValidator;

  private final EntityManager entityManager;

  private final LinkTableWriter linkTableWriter;

  @Override
  @Transactional
  public TypeReport addCollectionItems(
      IdentifiableObject object,
      String propertyName,
      Collection<? extends IdentifiableObject> objects)
      throws ForbiddenException, ConflictException, NotFoundException, BadRequestException {
    Property property =
        validateUpdate(
            object, propertyName, "Only identifiable object collections can be added to.");

    Collection<String> itemCodes = getItemCodes(objects);

    if (itemCodes.isEmpty()) {
      return TypeReport.empty(property.getItemKlass());
    }

    TypeReport report = new TypeReport(property.getItemKlass());

    if (property.isOwner()) {
      addOwnedCollectionItems(object, property, itemCodes, report);
    } else {
      addNonOwnedCollectionItems(object, property, itemCodes, report);
    }

    return report;
  }

  private void addOwnedCollectionItems(
      IdentifiableObject object, Property property, Collection<String> itemCodes, TypeReport report)
      throws BadRequestException {
    Collection<IdentifiableObject> collection = getCollection(object, property);
    boolean direct = canWriteDirectly(object, property);

    updateCollectionItems(
        property,
        itemCodes,
        report,
        ErrorCode.E1108,
        item -> {
          if (direct) {
            countUpdate(report, linkTableWriter.add(role(object, property), id(object), id(item)));
          } else if (!collection.contains(item)) {
            collection.add(item);
            report.updatedInc(1);
          } else {
            report.ignoredInc(1);
          }
        });
    validateCollectionProperty(property, object);
  }

  private void addNonOwnedCollectionItems(
      IdentifiableObject object,
      Property property,
      Collection<String> itemCodes,
      TypeReport report) {
    Schema owningSchema = schemaService.getSchema(property.getItemKlass());
    Property owningProperty = owningSchema.propertyByRole(property.getOwningRole());

    updateCollectionItems(
        property,
        itemCodes,
        report,
        ErrorCode.E1108,
        item -> {
          if (canWriteDirectly(item, owningProperty)) {
            validateAndThrowErrors(() -> schemaValidator.validateProperty(property, object));
            if (linkTableWriter.add(role(item, owningProperty), id(item), id(object))) {
              manager.update(item);
              report.updatedInc(1);
            } else {
              report.ignoredInc(1);
            }
            return;
          }
          Collection<IdentifiableObject> collection = getCollection(item, owningProperty);

          if (!collection.contains(object)) {
            validateAndThrowErrors(() -> schemaValidator.validateProperty(property, object));
            collection.add(object);
            manager.update(item);
            report.updatedInc(1);
          } else {
            report.ignoredInc(1);
          }
        });
    entityManager.refresh(object);
  }

  @Override
  @Transactional
  public TypeReport delCollectionItems(
      IdentifiableObject object,
      String propertyName,
      Collection<? extends IdentifiableObject> objects)
      throws ForbiddenException, ConflictException, NotFoundException, BadRequestException {
    Property property =
        validateUpdate(
            object, propertyName, "Only identifiable object collections can be removed from.");

    Collection<String> itemCodes = getItemCodes(objects);

    if (itemCodes.isEmpty()) {
      return TypeReport.empty(property.getItemKlass());
    }

    TypeReport report = new TypeReport(property.getItemKlass());

    if (property.isOwner()) {
      delOwnedCollectionItems(object, property, itemCodes, report);
    } else {
      delNonOwnedCollectionItems(object, property, itemCodes, report);
    }

    validateCollectionProperty(property, object);
    return report;
  }

  private void delOwnedCollectionItems(
      IdentifiableObject object,
      Property property,
      Collection<String> itemCodes,
      TypeReport report) {
    Collection<IdentifiableObject> collection = getCollection(object, property);
    boolean direct = canWriteDirectly(object, property);

    updateCollectionItems(
        property,
        itemCodes,
        report,
        ErrorCode.E1109,
        item -> {
          if (direct) {
            countDelete(
                report, linkTableWriter.remove(role(object, property), id(object), id(item)));
          } else if (collection.contains(item)) {
            collection.remove(item);
            report.deletedInc(1);
          } else {
            report.ignoredInc(1);
          }
        });
  }

  private void delNonOwnedCollectionItems(
      IdentifiableObject object,
      Property property,
      Collection<String> itemCodes,
      TypeReport report) {
    Schema owningSchema = schemaService.getSchema(property.getItemKlass());
    Property owningProperty = owningSchema.propertyByRole(property.getOwningRole());

    updateCollectionItems(
        property,
        itemCodes,
        report,
        ErrorCode.E1109,
        item -> {
          if (canWriteDirectly(item, owningProperty)) {
            if (linkTableWriter.remove(role(item, owningProperty), id(item), id(object))) {
              manager.update(item);
              report.deletedInc(1);
            } else {
              report.ignoredInc(1);
            }
            return;
          }
          Collection<IdentifiableObject> collection = getCollection(item, owningProperty);

          if (collection.contains(object)) {
            validateAndThrowErrors(() -> schemaValidator.validateProperty(owningProperty, item));
            collection.remove(object);
            manager.update(item);
            report.deletedInc(1);
          } else {
            report.ignoredInc(1);
          }
        });
    entityManager.refresh(object);
  }

  @Override
  @Transactional
  public TypeReport replaceCollectionItems(
      IdentifiableObject object,
      String propertyName,
      Collection<? extends IdentifiableObject> objects)
      throws ForbiddenException, ConflictException, NotFoundException, BadRequestException {
    Property property =
        validateUpdate(
            object, propertyName, "Only identifiable object collections can be replaced.");

    TypeReport deletions =
        delCollectionItems(object, propertyName, getCollection(object, property));
    TypeReport additions = addCollectionItems(object, propertyName, objects);
    return deletions.mergeAllowEmpty(additions);
  }

  @Override
  @Transactional
  public TypeReport mergeCollectionItems(
      IdentifiableObject object, String propertyName, IdentifiableObjects items)
      throws ForbiddenException, ConflictException, NotFoundException, BadRequestException {
    TypeReport delReport = delCollectionItems(object, propertyName, items.getDeletions());
    TypeReport addReport = addCollectionItems(object, propertyName, items.getAdditions());
    return delReport.mergeAllowEmpty(addReport);
  }

  private Property validateUpdate(IdentifiableObject object, String propertyName, String message)
      throws ForbiddenException, NotFoundException, ConflictException {
    Schema schema = schemaService.getSchema(HibernateProxyUtils.getRealClass(object));

    if (!aclService.canUpdate(CurrentUserUtil.getCurrentUserDetails(), object)) {
      throw new ForbiddenException("You don't have the proper permissions to update this object.");
    }

    if (!schema.hasProperty(propertyName)) {
      throw new NotFoundException(
          "Property " + propertyName + " does not exist on " + object.getClass().getName());
    }

    Property property = schema.getProperty(propertyName);

    if (!property.isCollection() || !property.isIdentifiableObject()) {
      throw new ConflictException(message);
    }
    return property;
  }

  /**
   * Single links of an owning collection can be written to its join table directly when the {@link
   * LinkTableWriter} supports the role and the collection is not loaded yet. Changing it through
   * Hibernate would load the whole collection first, which for large org unit sets means hydrating
   * hundreds of thousands of entities. A collection that is already loaded is changed in memory as
   * before, so the session stays consistent with the database.
   */
  private boolean canWriteDirectly(IdentifiableObject owner, Property owningProperty) {
    return owningProperty.isOwner()
        && isSizeUnconstrained(owningProperty)
        && linkTableWriter.supports(role(owner, owningProperty))
        && !Hibernate.isInitialized(getCollection(owner, owningProperty));
  }

  /**
   * Validates a collection property, unless the collection is not loaded and has no size limits:
   * the only validation that applies to a collection is its size, and checking it would load it.
   */
  private void validateCollectionProperty(Property property, IdentifiableObject object)
      throws BadRequestException {
    if (isSizeUnconstrained(property)
        && !Hibernate.isInitialized(getCollection(object, property))) {
      return;
    }
    validateAndThrowErrors(() -> schemaValidator.validateProperty(property, object));
  }

  private static boolean isSizeUnconstrained(Property property) {
    return (property.getMin() == null || property.getMin() <= 0)
        && (property.getMax() == null || property.getMax() >= Integer.MAX_VALUE);
  }

  private static String role(IdentifiableObject owner, Property owningProperty) {
    return HibernateProxyUtils.getRealClass(owner).getName() + "." + owningProperty.getFieldName();
  }

  private static long id(IdentifiableObject object) {
    return object.getId();
  }

  private static void countUpdate(TypeReport report, boolean changed) {
    if (changed) {
      report.updatedInc(1);
    } else {
      report.ignoredInc(1);
    }
  }

  private static void countDelete(TypeReport report, boolean changed) {
    if (changed) {
      report.deletedInc(1);
    } else {
      report.ignoredInc(1);
    }
  }

  private Collection<String> getItemCodes(Collection<? extends IdentifiableObject> objects) {
    return objects.stream().map(IdentifiableObject::getUid).collect(toList());
  }

  @SuppressWarnings("unchecked")
  private List<? extends IdentifiableObject> getItems(
      Property property, Collection<String> itemCodes) {
    return manager.getByUid(
        ((Class<? extends IdentifiableObject>) property.getItemKlass()), itemCodes);
  }

  @SuppressWarnings("unchecked")
  private Collection<IdentifiableObject> getCollection(
      IdentifiableObject object, Property property) {
    try {
      return (Collection<IdentifiableObject>) property.getGetterMethod().invoke(object);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @FunctionalInterface
  private interface CollectionUpdate {
    void applyToItem(IdentifiableObject item) throws Exception;
  }

  private void updateCollectionItems(
      Property property,
      Collection<String> itemCodes,
      TypeReport report,
      ErrorCode errorCode,
      CollectionUpdate update) {
    int index = 0;
    for (IdentifiableObject item : getItems(property, itemCodes)) {
      try {
        update.applyToItem(item);
      } catch (Exception ex) {
        Class<?> itemType = property.getItemKlass();
        ObjectReport objectReport = new ObjectReport(itemType, index, item.getUid());
        objectReport.addErrorReport(new ErrorReport(itemType, errorCode, ex.getMessage()));
        report.addObjectReport(objectReport);
        report.ignoredInc(1);
      }
      index++;
    }
  }
}
