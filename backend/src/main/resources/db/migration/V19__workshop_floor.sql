-- Workshop floor (docs/api/release-4.md "Workshop floor", plan docs/plans/06-workshop-floor.md): mechanic time
-- entries, photos of a work order, checklist templates and the checklists a work order copies from them, plus the
-- workshop setting that makes the delivery checklist a gate for DELIVERED.
--
-- Sizes: work_time_entry grows with the work done (a few to tens of rows per work order); work_time_entry_change one
-- row per correction. work_order_photo holds at most 30 live rows per work order and work_order_photo_data the bytes,
-- at most 5 MB each (about 300 KB after the browser resizes to 1600 px), so photos are the one table that grows by
-- gigabytes. Estimate: 200 work orders a month x 10 photos x 300 KB is about 7 GB per workshop per year, so about
-- 700 GB a year for 100 workshops. The bytes live in their own table (listing the metadata never reads them, TOAST
-- storage EXTERNAL because JPEG/PNG/WebP do not compress), a per-workshop quota (app.photos.max-bytes-per-workshop,
-- 5 GiB) bounds the worst case, and a deleted photo loses its bytes after app.photos.deleted-retention-days. Move the
-- bytes to S3 behind the PhotoStore port (no API change) when the photo table passes about 100 GB in total or one
-- workshop uses its quota regularly. checklist_template* are a few dozen rows per workshop at most;
-- work_order_checklist* hold two checklists of about ten rows per work order.
--
-- Time entries: a mechanic has at most one running entry (partial unique index). No two entries of a mechanic overlap:
-- the application checks that under a per-mechanic advisory lock (an exclusion constraint would need the btree_gist
-- extension, which the migration role may not create); it stops the running one when another
-- starts, under the work order row lock. Rows are never deleted (the app role has no delete grant); every correction
-- keeps the previous values in work_time_entry_change (insert only) with who, when and why. line_id has no foreign key: a removed line must not block or erase the time
-- already booked on it.
--
-- Photos are never hard-deleted by the application: a delete records who, when and why (soft delete) and a job
-- drops the bytes later (purged_at), keeping the audit row.
--
-- Workshop data: tenant_id with the V2-style default, row-level security ENABLE + FORCE with the tenant_isolation
-- policy (V1) on every table. Only the tenant column is added to an existing table (constant default, metadata only
-- in PostgreSQL 11+).
-- Rollback: drop table work_time_entry_change, work_order_checklist_item, work_order_checklist, checklist_template_item, checklist_template,
-- work_order_photo_data, work_order_photo, work_time_entry; alter table tenant drop column
-- delivery_checklist_required; delete version '19' from flyway_schema_history.
set local lock_timeout = '5s';

alter table tenant add column delivery_checklist_required boolean not null default false;

-- Time tracking -----------------------------------------------------------------------------------------------

create table work_time_entry (
    id             uuid primary key,
    tenant_id      uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                       references tenant (id),
    work_order_id  uuid not null references work_order (id),
    line_id        uuid,
    mechanic_id    uuid not null references app_user (id),
    started_at     timestamptz not null,
    stopped_at     timestamptz,
    -- True when moving the order or changing its mechanic stopped the timer, not the mechanic.
    auto_stopped   boolean not null default false,
    edited_by      uuid references app_user (id),
    edited_at      timestamptz,
    edit_reason    text check (edit_reason is null or char_length(edit_reason) between 1 and 500),
    version        bigint not null default 0,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    check (stopped_at is null or stopped_at >= started_at),
    check (not auto_stopped or stopped_at is not null),
    check ((edited_by is null) = (edited_at is null) and (edited_by is null) = (edit_reason is null))
);
-- One running timer per mechanic. mechanic_id alone is enough: a user belongs to one workshop.
create unique index work_time_entry_one_running_idx on work_time_entry (mechanic_id) where stopped_at is null;
-- The entries of a work order (screen, totals, auto-stop of the running one).
create index work_time_entry_order_idx on work_time_entry (tenant_id, work_order_id, started_at desc);
-- A mechanic's time over a period (the productivity report of step 08).
create index work_time_entry_mechanic_idx on work_time_entry (tenant_id, mechanic_id, started_at desc);
create index work_time_entry_edited_by_idx on work_time_entry (edited_by);

-- What an entry was before each correction. Insert only.
create table work_time_entry_change (
    id             uuid primary key,
    tenant_id      uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                       references tenant (id),
    entry_id       uuid not null references work_time_entry (id),
    old_started_at timestamptz not null,
    old_stopped_at timestamptz,
    old_line_id    uuid,
    changed_by     uuid not null references app_user (id),
    changed_at     timestamptz not null,
    reason         text not null check (char_length(reason) between 1 and 500)
);
create index work_time_entry_change_entry_idx on work_time_entry_change (tenant_id, entry_id, changed_at);
create index work_time_entry_change_changed_by_idx on work_time_entry_change (changed_by);

-- Photos ------------------------------------------------------------------------------------------------------

create table work_order_photo (
    id             uuid primary key,
    tenant_id      uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                       references tenant (id),
    work_order_id  uuid not null references work_order (id),
    stage          text not null check (stage in ('CHECK_IN', 'WORK', 'DELIVERY', 'DOCUMENTS')),
    caption        text check (caption is null or char_length(caption) between 1 and 200),
    -- Sniffed from the bytes by the application, never the client's declaration.
    content_type   text not null check (content_type in ('image/jpeg', 'image/png', 'image/webp')),
    size_bytes     integer not null check (size_bytes between 1 and 5242880),
    -- SHA-256 of the bytes in hex: the ETag.
    sha256         text not null check (char_length(sha256) = 64),
    uploaded_by    uuid not null references app_user (id),
    -- Soft delete: who, when and why. The bytes are dropped later by a job (purged_at); this row stays as the audit.
    deleted_at     timestamptz,
    deleted_by     uuid references app_user (id),
    delete_reason  text check (delete_reason is null or char_length(delete_reason) between 1 and 500),
    purged_at      timestamptz,
    version        bigint not null default 0,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    check ((deleted_at is null) = (deleted_by is null) and (deleted_at is null) = (delete_reason is null)),
    check (purged_at is null or deleted_at is not null)
);
create index work_order_photo_order_idx on work_order_photo (tenant_id, work_order_id, created_at);
create index work_order_photo_uploaded_by_idx on work_order_photo (uploaded_by);
create index work_order_photo_deleted_by_idx on work_order_photo (deleted_by);
-- The purge job (deleted_at) and the storage quota (sum of size_bytes, index-only): photos whose bytes are still stored.
create index work_order_photo_stored_idx on work_order_photo (tenant_id, deleted_at) include (size_bytes)
    where purged_at is null;

create table work_order_photo_data (
    photo_id   uuid primary key references work_order_photo (id) on delete cascade,
    tenant_id  uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                   references tenant (id),
    data       bytea not null
);
create index work_order_photo_data_tenant_idx on work_order_photo_data (tenant_id);
-- Images are already compressed: store the bytes out of line without compressing them again.
alter table work_order_photo_data alter column data set storage external;

-- Checklists --------------------------------------------------------------------------------------------------

create table checklist_template (
    id          uuid primary key,
    tenant_id   uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                    references tenant (id),
    kind        text not null check (kind in ('CHECK_IN', 'DELIVERY')),
    version     bigint not null default 0,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    constraint checklist_template_tenant_kind_key unique (tenant_id, kind)
);

create table checklist_template_item (
    id           uuid primary key,
    tenant_id    uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                     references tenant (id),
    template_id  uuid not null references checklist_template (id) on delete cascade,
    position     integer not null check (position >= 1),
    label        text not null check (char_length(label) between 1 and 120),
    required     boolean not null
);
-- Saving a template deletes its items first and then inserts the new ones, so positions never clash.
alter table checklist_template_item add constraint checklist_template_item_position_key unique (template_id, position);
create index checklist_template_item_tenant_idx on checklist_template_item (tenant_id);

create table work_order_checklist (
    id             uuid primary key,
    tenant_id      uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                       references tenant (id),
    work_order_id  uuid not null references work_order (id),
    kind           text not null check (kind in ('CHECK_IN', 'DELIVERY')),
    started_at     timestamptz not null,
    started_by     uuid not null references app_user (id),
    constraint work_order_checklist_order_kind_key unique (work_order_id, kind)
);
create index work_order_checklist_tenant_idx on work_order_checklist (tenant_id);
create index work_order_checklist_started_by_idx on work_order_checklist (started_by);

create table work_order_checklist_item (
    id            uuid primary key,
    tenant_id     uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                      references tenant (id),
    checklist_id  uuid not null references work_order_checklist (id) on delete cascade,
    position      integer not null check (position >= 1),
    -- Copied from the template when the checklist starts.
    label         text not null check (char_length(label) between 1 and 120),
    required      boolean not null,
    result        text check (result in ('OK', 'ATTENTION', 'N_A')),
    note          text check (note is null or char_length(note) between 1 and 300),
    answered_by   uuid references app_user (id),
    answered_at   timestamptz,
    constraint work_order_checklist_item_position_key unique (checklist_id, position),
    check ((result is null) = (answered_by is null) and (result is null) = (answered_at is null)),
    check (note is null or result is not null)
);
create index work_order_checklist_item_tenant_idx on work_order_checklist_item (tenant_id);
create index work_order_checklist_item_answered_by_idx on work_order_checklist_item (answered_by);

do $$
declare
    t text;
begin
    foreach t in array array['work_time_entry', 'work_order_photo', 'work_order_photo_data', 'checklist_template',
                             'checklist_template_item', 'work_order_checklist', 'work_order_checklist_item', 'work_time_entry_change'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy tenant_isolation on %I
                           using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                           with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t);
    end loop;
end $$;

-- V1's default privileges grant select, insert, update and delete on every new table; narrow them to what the
-- application does (as V18 does).
revoke delete on work_time_entry from asp_app;
revoke update on work_time_entry from asp_app;
grant update (line_id, started_at, stopped_at, auto_stopped, edited_by, edited_at, edit_reason, version, updated_at)
    on work_time_entry to asp_app;
revoke update, delete on work_time_entry_change from asp_app;

revoke delete on work_order_photo from asp_app;
revoke update on work_order_photo from asp_app;
grant update (stage, caption, version, updated_at, deleted_at, deleted_by, delete_reason, purged_at)
    on work_order_photo to asp_app;
revoke update on work_order_photo_data from asp_app;

revoke delete on checklist_template from asp_app;
revoke update on checklist_template_item from asp_app;
revoke update on checklist_template from asp_app;
grant update (version, updated_at) on checklist_template to asp_app;
revoke update, delete on work_order_checklist from asp_app;
revoke delete on work_order_checklist_item from asp_app;
revoke update on work_order_checklist_item from asp_app;
grant update (result, note, answered_by, answered_at) on work_order_checklist_item to asp_app;
