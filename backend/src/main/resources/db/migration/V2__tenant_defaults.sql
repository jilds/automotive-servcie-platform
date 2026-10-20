-- New rows take their tenant from the connection's app.tenant_id, so application code never sets it.
-- Row-level security (V1) still checks it on every insert and update.
do $$
declare
    t text;
begin
    foreach t in array array['customer', 'vehicle', 'work_order', 'work_order_line'] loop
        execute format($d$alter table %I alter column tenant_id
                          set default nullif(current_setting('app.tenant_id', true), '')::uuid$d$, t);
    end loop;
end $$;
