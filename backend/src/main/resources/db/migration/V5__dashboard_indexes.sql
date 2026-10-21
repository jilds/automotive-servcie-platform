-- Owner dashboard: "delivered this month" filters on status and a status_changed_at range.
-- The new index leads with the same (tenant_id, status) columns, so the old two-column index is redundant.
-- Work orders are still a small table at Release 2, so a plain (locking) create inside the migration
-- transaction is acceptable; switch to CREATE INDEX CONCURRENTLY in its own migration once it is large.
create index work_order_tenant_status_changed_idx on work_order (tenant_id, status, status_changed_at);
drop index work_order_tenant_status_idx;

-- Foreign key from V1 without an index; used by the mechanic's board and the mechanic workload count.
create index work_order_assigned_mechanic_idx on work_order (assigned_mechanic_id)
    where assigned_mechanic_id is not null;
