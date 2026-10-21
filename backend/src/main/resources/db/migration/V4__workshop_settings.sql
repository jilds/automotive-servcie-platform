-- Workshop settings used by the owner backoffice: default VAT for new lines and the hourly labour rate.
-- Adding a NOT NULL column with a constant default is a metadata-only change in PostgreSQL 11+, so this
-- does not rewrite the tenant table. The tenant table has no row-level security; the application always
-- filters it by the signed-in user's tenant.
alter table tenant
    add column default_vat_rate numeric(5, 2) not null default 23.00,
    add column labour_rate_cents bigint not null default 0,
    add constraint tenant_default_vat_rate_range check (default_vat_rate between 0 and 100),
    add constraint tenant_labour_rate_non_negative check (labour_rate_cents >= 0),
    add constraint tenant_country_iso_alpha2 check (country ~ '^[A-Z]{2}$');
