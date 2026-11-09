# 16: Data import (customers and vehicles from CSV)

Status: **planned**. Commit `feat(customer): add CSV import of customers and vehicles`. Migration: number claimed in
build order, next free after V21. Source: [gap-analysis-carbob.md](gap-analysis-carbob.md) (Data migration = Missing).
Backs the migration promise in `site/src/offer.ts` (`migrationIncluded`, still null).

## Today

A new workshop types its customers and vehicles by hand. The landing site may only promise migration once
`offer.migrationIncluded` is set.

## Decisions (proposed, to confirm)

| Point | Proposal |
|---|---|
| Module | Inside `customer` (it owns customers and vehicles and their rules: NIF, address, plate normalisation, VIN). No new module. |
| Format | One CSV, UTF-8 (BOM accepted), `;` or `,` detected, a header row with fixed column names (template downloadable in three languages); one row = customer + optional vehicle. |
| Two steps | Upload → **dry run** (parse and validate every row, nothing stored) → owner confirms → apply. The dry run result is kept 24 h. |
| Errors | Per-row report: row number, column, error code (the same codes as the API: `invalid-nif`, `invalid-postal-code`, `duplicate-plate`, …). Downloadable as CSV. Valid rows can be applied while bad ones are skipped. |
| Idempotent | Match existing customers by NIF (else e-mail, else phone) and vehicles by normalised plate; re-running the same file creates nothing new. A row whose match differs is reported as `import-conflict`, never overwritten. **Open:** offer "update existing" (proposal: no). |
| Limits | 5 MB, 10 000 rows per file; Multipart limit per endpoint. Quotas per plan in plan 21. |
| Execution | Apply runs in batches of 500, each its own transaction, inside the request's tenant (no `TenantScope` needed, it is a signed-in call). **Open:** async job for big files (proposal: sync up to the limit, measured first). |
| Consent | `marketing_consent` is imported only from an explicit column; default false. |
| **Open** | Other sources (work order history, stock, OfficeGest export format) and whether ASP staff do migrations for a fee. |

## Data (RLS ENABLE + FORCE, `tenant_isolation`)

- `import_run`: file name, row count, status (DRY_RUN / APPLIED / EXPIRED), counts (created, matched, skipped),
  created by / at, `applied_at`, `expires_at`, version.
- `import_row_result`: run, row number, outcome, error code, column. No raw row stored after apply (personal data);
  the dry-run payload is deleted at expiry by a daily job (ShedLock, `TenantScope`).
- Isolation test for both.

## API (owner only; front desk and mechanic 403)

- `POST /api/v1/imports/customers` (multipart) → dry run summary + row results.
- `GET /api/v1/imports/{id}`, `GET /api/v1/imports/{id}/errors.csv`, `POST /api/v1/imports/{id}/apply` (version).
- `GET /api/v1/imports/template.csv?lang=`.

## Frontend

- `/backoffice/import`: upload, dry-run table (filter errors), apply button, history. 390 px: cards.

## Error codes

- `import-file-too-large`, `import-too-many-rows`, `import-bad-header`, `import-expired`, `import-already-applied`,
  `import-conflict`. Three languages.

## Tests

- Parser: BOM, both separators, quoted fields, accents; each validation code per row.
- Dry run stores nothing; apply twice = same data; matching rules; batch failure keeps earlier batches.
- Formula-injection safe error CSV (`=`, `+`, `-`, `@` prefixed); isolation; non-owner 403.

## Out of scope

- Work order history, stock and invoices import; Excel files; API import.

## Done when

- CLAUDE.md §10; security-reviewer (file upload); the owner can then set `offer.migrationIncluded`.
