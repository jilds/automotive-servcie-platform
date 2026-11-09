# 14: Digital vehicle inspection

Status: **planned**. Commit `feat(workshop): add digital inspections with a customer report`. Migration: number claimed
in build order, next free after V21. Depends on [06-workshop-floor.md](06-workshop-floor.md) (photos, checklists).
Source: [gap-analysis-carbob.md](gap-analysis-carbob.md) (Digital diagnosis photos/video = Missing).

## Today

Plan 06 brings checklists (OK / ATTENTION / N/A) and order photos behind `PhotoStore`. There is no rating by urgency,
no photo on a single item and nothing the customer can see.

## Owner decisions (2026-11-09)
- Video is undecided: no storage or provider chosen so far. Photos and the report can proceed without it; do not build video until the owner decides.

## Decisions (proposed, to confirm)

| Point | Proposal |
|---|---|
| Module | Inside `workshop`, next to the checklists of plan 06 (an inspection is a checklist kind `INSPECTION`). No new module. |
| Rating | Per item: OK (green), ATTENTION (yellow), URGENT (red), N/A; optional note and up to 5 photos per item. |
| Photos | Reuse `work_order_photo` and `PhotoStore` from plan 06 (same type sniffing, size and count limits); an item links photos by id. |
| Quote link | An ATTENTION / URGENT item can carry suggested lines; "add to quote" copies them onto the work order (plan 05 supersede rules apply). |
| Customer report | The front desk publishes the inspection: public `/w/{slug}/inspection/{token}` (`StatusTokens`, hash only), shows items by colour, notes, photos (served through the token, not the staff endpoint), and the quote link if SENT. No prices beyond the quote, no mechanic names. |
| Video | **Open:** size, storage (bytea is not fit; needs S3 behind `PhotoStore`), transcoding, cost. Proposal: not in this step. |
| E-signature | **Later sub-step 14b:** a drawn signature at check-in and delivery (PNG via `PhotoStore`, name, timestamp, stage), shown on the order. **Open:** legal value needed (simple vs qualified signature). |

## Data (RLS ENABLE + FORCE, `tenant_isolation`)

- `inspection`: work order, template copy, status (DRAFT / PUBLISHED), `published_at`, `published_by`,
  `report_token_hash` (unique), version.
- `inspection_item`: position, label, rating, note, required.
- `inspection_item_photo`: item → `work_order_photo` id.
- 14b: `work_order_signature`: stage (CHECK_IN / DELIVERY), signer name, image ref, `signed_at`.
- Isolation tests for each table.

## Rules

- Mechanics fill the inspection (they see no prices); owner / front desk publish.
- Published inspections are frozen (edits make a new version? **open**; proposal: publish again replaces the token).
- Public photos only for photos linked to a published inspection item; rate limits of ADR 0004.
- Publishing sends the link through `notification` (e-mail now, SMS / WhatsApp after plan 13) via
  `workshop.InspectionPublished` (raw token, token-free `toString`).

## API

- Staff: `POST /work-orders/{id}/inspection`, `PUT .../inspection/items/{itemId}`, `POST .../inspection/publish`,
  `POST .../inspection/items/{itemId}/to-quote`.
- Public: `GET /api/v1/public/{slug}/inspections/{token}`, `GET .../inspections/{token}/photos/{photoId}`.

## Frontend

- Mechanic phone screen: item list with three big colour buttons, camera per item.
- Public report on `public-page.ts`; summary counts by colour. New routes in `SpaForwardingController`.

## Error codes

- `inspection-not-found`, `inspection-published`, `inspection-incomplete` (required items unanswered at publish).

## Tests

- Rating and photo link rules, publish freezes, public view has no customer data or prices, unlinked photo 404 through
  the token, to-quote supersedes a SENT quote, isolation, mechanic cannot publish.

## Out of scope

- Video (open), qualified e-signature, manufacturer inspection schedules (TecRMI etc.).

## Done when

- CLAUDE.md §10; security-reviewer for the public photo access.
