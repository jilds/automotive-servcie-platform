# 10: Leftovers (CLAUDE.md §12 and reviews)

Status: **planned**. Several small commits.

| Item | Change | Commit |
|---|---|---|
| Tenant-scoped composite foreign keys (review L5) | One migration adds `(tenant_id, id)` unique keys and composite FKs on child tables. A test checks that a cross-tenant reference fails. | `fix(db)` |
| OnPush on the remaining older components (L8) | `ChangeDetectionStrategy.OnPush` with signals; the specs stay green. | `refactor(ui)` |
| `identity/login.ts` on `shared/public-page.ts` | One header for every public page. | `refactor(identity)` |
| GDPR tools | Owner: JSON export of a customer's data, and anonymising a customer. Work orders and invoices are kept; name, phone, e-mail and address are removed where the law allows. Linked from the privacy page. | `feat(customer)` |
| Shared stores for more than one instance | A Postgres-backed option for the rate-limit counters and the idempotency keys (`app.public.rate-limit.store: memory\|jdbc`). | `feat(config)` |
| Booking PII retention (security review I3) | A scheduled job per workshop clears name, phone, e-mail and plate on DECLINED / CANCELLED / NO_SHOW bookings after N days (owner setting). The status link expires some days after the slot. | `feat(booking)` |
| Coded errors in the booking domain | `BookingRules` and `BookingContact` throw `InvalidInputException` instead of `IllegalArgumentException`. | `refactor(booking)` |
| ArchUnit with class file 71 | Watch upstream; no code. | — |

Out of scope until a product decision:
- Brazilian fiscal features.
- Billing of ASP's own subscriptions.
- GTMotive and TecDoc.
- An estimate chatbot.
