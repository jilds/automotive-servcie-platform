-- Parts inside service packages: a package item is a catalogue service or a stock product, never both.
--
-- service_package_item gains a nullable product_id; service_item_id becomes nullable; a check keeps exactly one of
-- the two set (existing rows all hold a service, so they pass), and one product appears at most once per package.
-- The table is small at Release 3: the check and the unique index scan it once inside the migration, blocking writes
-- to it for well under a second. tenant_id, row-level security and grants are unchanged (V10).
-- Rollback: delete the items with a product_id, drop the constraint service_package_item_one_kind, the unique
-- constraint service_package_item_package_product_key, the index service_package_item_product_idx and the column
-- product_id, set service_item_id not null again; delete version '13' from flyway_schema_history.
set local lock_timeout = '5s';

alter table service_package_item alter column service_item_id drop not null;
alter table service_package_item add column product_id uuid references product (id);
alter table service_package_item add constraint service_package_item_one_kind
    check (num_nonnulls(service_item_id, product_id) = 1);
alter table service_package_item add constraint service_package_item_package_product_key
    unique (package_id, product_id);
-- Finds the packages that hold a product (and serves the foreign key).
create index service_package_item_product_idx on service_package_item (product_id) where product_id is not null;
