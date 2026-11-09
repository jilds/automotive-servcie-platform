# 06: Workshop floor (time tracking, photos, checklists)

Status: **done** (backend, contract and Angular; commit hash to be added with the commit). Commit
`feat(workshop): add time tracking,
photos and checklists`. Migration V19. Contract: section "Workshop floor" in [release-4.md](../api/release-4.md),
written before the code.

## Decisions (2026-11-09)

| Point | Decision |
|---|---|
| Module | Inside `workshop` (no new module). `identity.WorkshopDirectory` gets `deliveryChecklistRequired(tenantId)`. |
| Who sees what | Everything here is open to every role for reading, mechanics included (no prices, no customer data in any of it). |
| Timer owner | A timer belongs to the order's **assigned mechanic**. The mechanic starts and stops their own; the OWNER may start and stop it for the assigned mechanic. The front desk cannot start or stop (it corrects). |
| One timer per mechanic | Partial unique index on `work_time_entry (mechanic_id) where stopped_at is null`. Starting on another order, or on another line of the same order, stops the running entry first (same transaction). Starting the same order and line again changes nothing (200 with the running entry). |
| Start allowed | While the order is not READY or later (DIAGNOSTICS, AWAITING_APPROVAL, AWAITING_PARTS, IN_PROGRESS), else 409 `time-tracking-closed`. |
| Auto-stop | Moving the order to READY, DELIVERED or CANCELLED, or giving it to another mechanic, stops its running timer at that moment (`autoStopped` = true). |
| Corrections | OWNER or FRONT_DESK edit an entry (start, stop, line) or add a missed one, always with a reason (1–500, `reason-required`); the entry keeps who, when and why of the last edit. Allowed in any order status. Times may not be in the future and stop is not before start (`invalid-time-range`). Overlaps with another entry of the same mechanic are rejected (409 `time-entry-overlap`, see Time corrections below). No delete: a wrong entry is set to zero length. |
| Totals | Per order and per line, in whole seconds; a running entry counts up to the time of the response. |
| Photos storage | Two tables: `work_order_photo` (metadata) and `work_order_photo_data` (bytea) behind the `PhotoStore` port; `DbPhotoStore` is the only implementation. Both have RLS. |
| Photo rules | JPEG, PNG, WebP by magic bytes (the declared type is ignored and the sniffed one is stored and served), 1 byte to 5 MB, 30 live photos per order (counted under the work order lock; a mechanic's own count excludes the DOCUMENTS photos he cannot see, so he may get a translated 409 `photo-limit-reached` with fewer visible photos: accepted), caption ≤ 200, stage CHECK_IN / WORK / DELIVERY / DOCUMENTS. |
| Photo access | Read: every role. Upload: OWNER, FRONT_DESK, and the assigned mechanic. Change caption/stage or delete: OWNER, FRONT_DESK, and the mechanic who uploaded it while still assigned. Delete is a soft delete with a reason (see Photo deletion below). Allowed in every order status. Served only with a bearer token (no public URL), with `ETag` (sha-256) and `If-None-Match` → 304. The Angular side loads the bytes through `HttpClient` as a blob. |
| Checklist templates | **One template per kind per workshop** (CHECK_IN, DELIVERY), ordered items with `required`. Until the owner saves one, the built-in default (in the workshop country's language) is used and shown with `custom: false`. Saving replaces all items (1–30). |
| Checklist per order | Started on demand (`POST …/checklists/{kind}`), copying the template items at that moment (label and required are frozen). Items are answered OK, ATTENTION or N/A with an optional note; last write wins (no per-item version; the order lock serialises writes). Starting twice returns the existing one. |
| Who answers | Assigned mechanic, OWNER, FRONT_DESK; not on DELIVERED / CANCELLED orders (409 `work-order-locked`). |
| Delivery gate | Setting `deliveryChecklistRequired` (workshop settings, default off). When on, READY → DELIVERED answers 409 `checklist-incomplete` until every required item of the order's DELIVERY checklist has a result (N/A counts). A DELIVERY checklist not yet started counts as all required items open (from the current template); if the template has no required item the gate passes. The owner has no override: switch the setting off. |
| Photo deletion (round 1, 2026-11-09) | Soft delete with a required reason: `deleted_at`, `deleted_by`, `delete_reason` on `work_order_photo`; the photo vanishes for everyone at once (404). Once the order is DELIVERED or CANCELLED only the OWNER may delete. A nightly job (`PhotoPurge`, ShedLock, every workshop whatever its subscription via `WorkshopDirectory.allWorkshops()`, `app.photos.deleted-retention-days`, default 30, per workshop inside `TenantScope`, one transaction per photo) drops the bytes and sets `purged_at`; the audit row stays. |
| Documents stage | Photos of stage DOCUMENTS are visible and manageable by OWNER and FRONT_DESK only; mechanics get 403 on upload and 404 elsewhere. |
| Privacy of images | The server removes EXIF and XMP (JPEG APP1, PNG eXIf, WebP EXIF and XMP) by byte-level rewriting before storing, with `size_bytes` and the ETag of the stored bytes. It never fails open: when the structure breaks off, the part read so far is kept without metadata and the unread rest is kept only if it holds no EXIF or XMP signature, otherwise the file is refused (415 `photo-type-not-allowed`). |
| Storage quota | `app.photos.max-bytes-per-workshop` (5 GiB), checked under a per-workshop advisory lock (taken after the order lock) against the sum of `size_bytes` of photos whose bytes are not purged: 409 `photo-storage-full`. Tiers will set it per plan later (plan 21). |
| Time corrections | Every correction writes the previous values to the insert-only `work_time_entry_change`. No two entries of a mechanic overlap (409 `time-entry-overlap`): checked in the repository under a per-mechanic advisory lock, because an exclusion constraint needs `btree_gist`, which the migration role (`asp_owner`) may not create. Moving to a constraint means creating the extension once as a superuser (for example in `init.sql`). |
| Execution | Stage 1 backend + contract (this stage), stage 2 Angular. |

## Time tracking

- Table `work_time_entry`: work order, optional line (no foreign key; a removed line reads as null), mechanic,
  `started_at`, `stopped_at`, `auto_stopped`, edited by / at / reason, version. RLS. Rows are never deleted.
- **Mechanic screen:** big start and stop buttons, plus the running time (`GET /time/running` for the banner).
- **Totals** per order and per line.

## Photos

**Growth and when to move to S3.** 200 work orders a month x 10 photos x 300 KB is about 7 GB per workshop per year, about 700 GB a year for 100 workshops. The quota bounds one workshop at 5 GiB, deleted photos lose their bytes after 30 days, and the bytes sit in their own table with TOAST storage EXTERNAL. Implement an S3 `PhotoStore` (no API change) when the photo table passes about 100 GB in total, or when a workshop uses its quota regularly.

- Tables `work_order_photo` and `work_order_photo_data`, both with RLS (unlike `tenant_logo`).
- Limits and types as in the decisions. Multipart limits are set in `application.yml`
  (`spring.servlet.multipart`, 5 MB file, 6 MB request); the container limit answers 413 `upload-too-large` (neutral, global); a file that reaches the service answers `photo-too-large`.
- Storage goes behind the `PhotoStore` interface, so S3 can replace it later.

## Checklists

- Tables `checklist_template`, `checklist_template_item`, `work_order_checklist`, `work_order_checklist_item`, all RLS.
- Owner edits the templates in the backoffice; the work order shows the started checklists.

## Frontend (stage 2)

- On the work order: a timer, a photo grid (camera capture on the phone, resized in the browser to 1600 px) and the
  checklists.
- In the backoffice: checklist templates and the "delivery checklist required" switch.
- Mechanics see all of this, without prices.

## Error codes

`timer-not-running`, `time-tracking-closed`, `time-entry-not-found`, `no-mechanic-assigned`, `invalid-time-range`,
`photo-not-found`, `photo-too-large`, `photo-type-not-allowed`, `photo-limit-reached`, `photo-storage-full`, `time-entry-overlap`, `upload-too-large` (neutral, global), `checklist-incomplete`,
`checklist-not-found`, `checklist-item-not-found`. Reused: `reason-required`, `work-order-locked`,
`work-order-not-found`, `invalid-mechanic`, `stale-data`, `try-again`, `validation`.

## Tests

- **Timer:** single running timer, auto-stop (READY, DELIVERED, CANCELLED, reassign), permissions, corrections,
  totals.
- **Photos:** type sniffing, size limit, count limit, ETag / 304, other workshop gets 404, permissions.
- **Checklists:** templates (defaults, save, version), copy on start, results, the DELIVERED gate.
- **Isolation:** for each table; `RowLevelSecurityCatalogueTests` knows the new tables.
