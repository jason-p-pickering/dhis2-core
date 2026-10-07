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
import org.hisp.dhis.program.Program;

/**
 * Program org units, changed from the program side ({@code /programs/{uid}/organisationUnits}) or
 * the org unit side ({@code /organisationUnits/{uid}/programs}).
 *
 * @author Jason P. Pickering <jason@dhis2.org>
 */
class ProgramOrganisationUnitsCollectionTest extends AbstractOrgUnitLinkCollectionTest {

  @Override
  protected Class<? extends IdentifiableObject> ownerClass() {
    return Program.class;
  }

  @Override
  protected IdentifiableObject newOwner() {
    return createProgram('A');
  }

  @Override
  protected void addOrgUnit(IdentifiableObject owner, OrganisationUnit unit) {
    ((Program) owner).addOrganisationUnit(unit);
  }

  @Override
  protected Set<OrganisationUnit> orgUnits(IdentifiableObject owner) {
    return ((Program) owner).getOrganisationUnits();
  }

  @Override
  protected String ownerEndpoint() {
    return "programs";
  }

  @Override
  protected String orgUnitProperty() {
    return "programs";
  }

  @Override
  protected String ownerField() {
    return "organisationUnits";
  }

  @Override
  protected String inverseField() {
    return "programs";
  }

  @Override
  protected String ownerTable() {
    return "program";
  }

  @Override
  protected String ownerIdColumn() {
    return "programid";
  }

  @Override
  protected String joinTable() {
    return "program_organisationunits";
  }
}
