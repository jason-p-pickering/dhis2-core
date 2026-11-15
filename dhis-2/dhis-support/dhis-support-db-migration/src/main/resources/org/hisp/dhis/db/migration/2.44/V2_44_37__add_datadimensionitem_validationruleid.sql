-- SPIKE: lets saved visualizations hold validation rules as data items
alter table datadimensionitem add column if not exists validationruleid bigint;

alter table datadimensionitem drop constraint if exists fk_datadimensionitem_validationruleid;
alter table datadimensionitem add constraint fk_datadimensionitem_validationruleid
    foreign key (validationruleid) references validationrule (validationruleid);

create index if not exists in_datadimensionitem_validationruleid on datadimensionitem (validationruleid);
