create table if not exists validationruleviolation (
  validationruleid bigint not null references validationrule(validationruleid) on delete cascade,
  periodid bigint not null references period(periodid) on delete cascade,
  organisationunitid bigint not null references organisationunit(organisationunitid) on delete cascade,
  attributeoptioncomboid bigint not null references categoryoptioncombo(categoryoptioncomboid) on delete cascade,
  dayinperiod integer not null default 0,
  leftsidevalue double precision,
  rightsidevalue double precision,
  lastupdated timestamp not null default now(),
  primary key (validationruleid, periodid, organisationunitid, attributeoptioncomboid, dayinperiod)
);

create index if not exists in_validationruleviolation_periodid on validationruleviolation (periodid);
-- the cascading foreign keys need an index, otherwise deleting an org unit or category option combo
-- scans the whole table
create index if not exists in_validationruleviolation_organisationunitid on validationruleviolation (organisationunitid);
create index if not exists in_validationruleviolation_attributeoptioncomboid on validationruleviolation (attributeoptioncomboid);
