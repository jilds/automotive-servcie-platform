-- Where and when a vehicle was first registered. The country decides which plate format applies
-- (Portugal: AA-00-AA, 00-AA-00, 00-00-AA, AA-00-00); the month is the date strip on Portuguese plates.
--
-- Existing vehicles take their workshop's country. Plates are not checked here: plates saved before the
-- format check stay as they are and are only checked when a plate or country is written again.
--
-- vehicle has FORCE ROW LEVEL SECURITY, which also applies to its owner, and app.tenant_id is not set
-- for migrations, so the backfill would see no rows. FORCE is lifted for the backfill only; Flyway runs this
-- script in one transaction, so no other session ever sees the table without it.
--
-- The backfill rewrites every vehicle row in one statement. That is fine at Release 2 sizes (thousands of
-- rows); a large table would need batched backfills and NOT VALID constraints instead.
set local lock_timeout = '5s';

alter table vehicle
    add column registration_country varchar(2),
    add column registration_date date;

alter table vehicle no force row level security;

update vehicle v
   set registration_country = t.country
  from tenant t
 where t.id = v.tenant_id;

alter table vehicle force row level security;

alter table vehicle
    alter column registration_country set not null,
    add constraint vehicle_registration_country_iso_alpha2 check (registration_country ~ '^[A-Z]{2}$'),
    add constraint vehicle_registration_date_first_of_month check (extract(day from registration_date) = 1),
    add constraint vehicle_registration_date_from_1937 check (registration_date >= date '1937-01-01');
