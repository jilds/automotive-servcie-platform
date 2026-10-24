-- Release 3: optimistic locking on the records people edit in dialogs and forms (review L4), and look-up by VIN.
--
-- version: Hibernate's @Version counter. An update sends the version it read; a different stored version means
-- someone else saved in between and the API answers 409 stale-data instead of overwriting their change.
-- Adding a column with a constant default is metadata-only in PostgreSQL, so no table is rewritten.
--
-- vehicle_tenant_vin_idx: check-in and the vehicle search find a car by its VIN (exact, upper case). Not unique,
-- because VINs entered before this release were never checked for duplicates. Built inside the migration
-- transaction (not CONCURRENTLY): vehicle has a few thousand rows per workshop at Release 3, so the write lock
-- lasts well under a second. Rollback: drop the index and the four columns, delete version '8' from
-- flyway_schema_history.
set local lock_timeout = '5s';

alter table customer add column version bigint not null default 0;
alter table vehicle  add column version bigint not null default 0;
alter table tenant   add column version bigint not null default 0;
alter table app_user add column version bigint not null default 0;

create index vehicle_tenant_vin_idx on vehicle (tenant_id, vin) where vin is not null;
