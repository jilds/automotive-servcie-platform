# 06: Workshop floor (time tracking, photos, checklists)

Status: **planned**. Commit `feat(workshop): add time tracking, photos and checklists`. Migration V19.

## Time tracking

- Table `work_time_entry`: work order, optional line, mechanic, `started_at`, `stopped_at`, edited-by plus reason.
  It has RLS.
- **One running timer per mechanic.** Starting another job stops the first one.
- Who can start or stop: the assigned mechanic, or the owner.
- Moving the order to READY or later stops a running timer.
- **Mechanic screen:** big start and stop buttons, plus the running time.
- **Totals** per order and per line.
- **Corrections:** the owner or front desk can correct an entry, with a reason.

## Photos

- Table `work_order_photo`: bytea, with RLS (unlike `tenant_logo`).
- Accepted files:
  - JPEG, PNG and WebP, with the type sniffed from the bytes
  - at most 5 MB each, after the client resizes the image to 1600 px
  - at most 30 photos per order
- Each photo has a caption and a stage: CHECK_IN, WORK or DELIVERY.
- Photos are served only to signed-in users of the workshop, with an ETag.
- Storage goes behind a `PhotoStore` interface, so S3 can replace it later.
- Multipart limits are set in `application.yml`.

## Checklists

- **Templates:** the owner keeps them per kind, CHECK_IN or DELIVERY. Each template has ordered items, and each item can
  be marked required.
- **Copy per order:** a work order copies the template when its checklist starts. Results per item are OK, ATTENTION or
  N/A, with an optional note.
- **Owner setting** "delivery checklist required": when it is on, DELIVERED is blocked until every required item is
  answered (409 `checklist-incomplete`).

## Frontend

- On the work order: a timer, a photo grid (camera capture on the phone, resized in the browser) and the checklist.
- In the backoffice: checklist templates.
- Mechanics see all of this, without prices.

## Error codes

- `timer-not-running`
- `photo-too-large`
- `photo-type-not-allowed`
- `photo-limit-reached`
- `checklist-incomplete`

## Tests

- **Timer:** single running timer, auto-stop, permissions.
- **Photos:** type sniffing, size limit, count limit.
- **Checklists:** the DELIVERED gate.
- **Isolation:** for each table.
