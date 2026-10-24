# Step 5 — Release 2 and the gap fixes

> Where this file lives: steps 01–04 are in the synced project folder next to this repository (`../docs`). This
> step was written here (`docs/steps/`) as requested; move it next to the others if you keep them together.

## What was built

**Release 2** (commit `4566cc3`, on request):
- Bootstrap 5.3.8 UI, light and dark mode.
- Edit dialogs: customer, vehicle, vehicle owner (history goes with the car), check-in data while the order is open.
- Owner backoffice: dashboard, team (role, active, at least one active owner), workshop settings.
- Vehicle registration country and month; Portuguese plate rules (PT vehicles only); era-styled plates.
- Role and active state read from the database on every request (30 s cache).

**Gap fixes** (after `docs/reviews/gap-audit.md`, on top of the Release 3 onboarding/theming and drag-and-drop work):
- Optimistic locking: `version` on customer, vehicle, tenant and app_user (migration V8); required on every update;
  409 `stale-data`; edit forms reload the record and say why. Settings and theme share the workshop's version.
- VIN: `GET /vehicles?vin=` (exact, 17 characters); check-in takes a plate or a VIN and says which matched.
- NIF check digit for Portuguese NIFs: domain, API (`@ValidNif`) and forms; customer, settings, admin console.
- Mechanics see every job of a vehicle in its history, without prices, and open other mechanics' jobs read-only.
- PT postal code checked in the API too (`validation`, `errors['address.postalCode']`); domain rules now raise coded
  exceptions (`invalid-postal-code`, `invalid-nif`, `invalid-vin`) instead of a generic `bad-request`.
- `RowLevelSecurityCatalogueTests`; cross-workshop tests for work order read and line add/delete; `PUT /customers`
  tests; `vehicle-not-found` code asserted; class-level `@Transactional` layer rule.
- Login title "ASP - Automotive Service Platform" restored (also on `/w/{slug}`); compact PT / EN / BR switcher in
  the phone top bar; sidebar band runs the full page height.
- README, CLAUDE.md and ADR 0001/0002 brought in line with the code and the new decisions; API contract in
  `docs/api/release-3.md`.

## How it was tested

| Command | Result |
|---|---|
| `./mvnw -q -pl backend verify` | 228 tests, 0 failures, 0 errors |
| `cd frontend && npm test` | 18 files, 197 tests passed |
| `cd frontend && npx ng build` | OK, initial total 535.61 kB, no warnings |
| `./mvnw -q clean verify` (copy of the working tree, so the IDE's `target/` was not wiped) | EXIT 0; 228 backend tests; jar has the UI |
| Audit `error-codes.sh`, reading the feature dictionaries too | 0 codes missing in en, pt-PT, pt-BR |

The new class-level layer rule was checked against a deliberately annotated class (it failed as expected; the probe
was removed).

**Browser walk:** the packaged jar ran on :8082 against a fresh PostgreSQL, seeded through the API, and headless
Chrome (driven over the DevTools protocol) walked it in pt-PT and en at 1280 and 390 px: login and branded login
(title), board, customer and its edit dialog, a stale-data save (another desk saved first: the dialog reloads and
explains), vehicle history and edit dialog, check-in by VIN, backoffice dashboard, team and settings (full-page capture:
the sidebar band runs to the bottom), the NIF check-digit hint, a mechanic's vehicle history (other mechanic's job
marked) and read-only job, and the admin console. 68 screenshots; every expected text found; no horizontal scroll at
390 px. The walk found three styling gaps, now fixed: the active language on the phone switcher and two new badges
used Bootstrap classes the trimmed build does not compile.

## What remains

- Tenant-scoped composite foreign keys (review L5), bundle budget back to 500 kB (L9), OnPush on `app`, `login`,
  `shell` and `board` (L8), ArchUnit with class file 71 (I1).
- The audit's `error-codes.sh` reads only `core/i18n/messages.*.ts`; it should also read the feature dictionaries
  (or become a test).
