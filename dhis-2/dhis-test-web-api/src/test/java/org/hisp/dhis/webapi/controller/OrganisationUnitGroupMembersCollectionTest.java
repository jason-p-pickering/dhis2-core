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
package org.hisp.dhis.webapi.controller;

import java.util.Set;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitGroup;

/**
 * Org unit group members, changed from the group side ({@code /organisationUnitGroups/{uid}/
 * organisationUnits}) or the org unit side ({@code
 * /organisationUnits/{uid}/organisationUnitGroups}).
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
class OrganisationUnitGroupMembersCollectionTest extends AbstractOrgUnitLinkCollectionTest {

  @Override
  protected Class<? extends IdentifiableObject> ownerClass() {
    return OrganisationUnitGroup.class;
  }

  @Override
  protected IdentifiableObject newOwner() {
    return createOrganisationUnitGroup('A');
  }

  @Override
  protected void addOrgUnit(IdentifiableObject owner, OrganisationUnit unit) {
    ((OrganisationUnitGroup) owner).addOrganisationUnit(unit);
  }

  @Override
  protected Set<OrganisationUnit> orgUnits(IdentifiableObject owner) {
    return ((OrganisationUnitGroup) owner).getMembers();
  }

  @Override
  protected String ownerEndpoint() {
    return "organisationUnitGroups";
  }

  @Override
  protected String orgUnitProperty() {
    return "organisationUnitGroups";
  }

  @Override
  protected String ownerField() {
    return "members";
  }

  @Override
  protected String inverseField() {
    return "groups";
  }

  @Override
  protected String ownerTable() {
    return "orgunitgroup";
  }

  @Override
  protected String ownerIdColumn() {
    return "orgunitgroupid";
  }

  @Override
  protected String joinTable() {
    return "orgunitgroupmembers";
  }
}
