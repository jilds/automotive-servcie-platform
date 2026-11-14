-- Instalment sales approved on the card terminal (Parcela Já com UNICRE on the REDUNIQ terminal): staff record the
-- payment after the approval, so it is PAID at once like cash or card, with the financier, the number of monthly
-- instalments (2 to 6) and the terminal receipt number. ASP does not verify the approval.
--
-- Immutability: the three new columns are written once with the row; the app role's update grant (V21) is unchanged.
-- Sizes: three small columns on payment, no new index (they are only read with the payment).
-- Workshop data: payment already has tenant_id and row-level security (V21); nothing new to enable.
-- Rollback (run as asp_owner, only before an INSTALMENTS payment exists; V22 first):
-- alter table payment drop constraint payment_instalment_data, drop column financier, drop column instalment_count,
--     drop column instalment_reference, drop constraint payment_method_check, drop constraint payment_staff_method_check;
-- re-add the V21 checks, then: delete from flyway_schema_history where version = '22';
set local lock_timeout = '5s';

alter table payment
    add column financier text check (financier in ('PARCELA_JA', 'OTHER')),
    add column instalment_count integer,
    add column instalment_reference text check (char_length(instalment_reference) between 1 and 60);

-- The unnamed checks of V21 have PostgreSQL's generated names: find the two that list the methods by their definition.
do $$
declare
    c record;
begin
    for c in select conname from pg_constraint
             where conrelid = 'payment'::regclass and contype = 'c'
               and (pg_get_constraintdef(oid) like '%''CASH''%'
                    or pg_get_constraintdef(oid) like '%provider_payment_id IS NULL%') loop
        execute format('alter table payment drop constraint %I', c.conname);
    end loop;
end $$;

alter table payment add constraint payment_method_check
    check (method in ('CASH', 'CARD', 'MULTIBANCO', 'MBWAY', 'TRANSFER', 'INSTALMENTS'));

-- Staff methods (cash, card, transfer, instalments) have no provider and are PAID at once.
alter table payment add constraint payment_staff_method_check
    check (method in ('MULTIBANCO', 'MBWAY') or (provider is null and provider_payment_id is null
        and expires_at is null and status = 'PAID'));

-- Instalment data exists exactly for INSTALMENTS.
alter table payment add constraint payment_instalment_data
    check ((method = 'INSTALMENTS') = (financier is not null and instalment_count is not null)
        and (method = 'INSTALMENTS' or instalment_reference is null)
        and (instalment_count is null or instalment_count between 2 and 6));
