# Roadmap: every open point (agreed 2026-10-25)

## Why

The goal is a first public release, followed by the rest of the release 1 list in CLAUDE.md §2 and the known gaps in
CLAUDE.md §12. The step plans are listed in [README.md](README.md).

## Decisions

| Date | Decision |
|---|---|
| 2026-10-24 | Booking uses open slots from the opening hours (slot length plus cars per slot). The customer follows a private status link. A confirmed booking becomes a prefilled check-in. |
| 2026-10-24 | The landing page is a separate static site on the main domain, with the app on `app.<domain>`. Product name "ASP – Automotive Service Platform" everywhere. No prices; the CTA is "Pedir proposta". Leads are stored by the app and listed in the platform admin console. |
| 2026-10-25 | Order: **release first, then features**. The shop comes after the release. |
| 2026-10-25 | Certified invoicing and Multibanco / MB WAY sit behind provider interfaces with a **sandbox implementation**. The real providers are picked later from a shortlist (`docs/release/checklist.md`). |
| 2026-10-25 | E-mail goes through **generic SMTP** (Spring Mail), with Mailpit in dev, and is off when not configured. |
| 2026-10-25 | Initial bundle budget: 520 kB. |
| 2026-10-25 | Domain, hosting and legal entity are not decided yet: use placeholders (`asp.example`, `<Empresa, Lda.>`), all listed in the checklist. |
| 2026-11-09 | A feature comparison with a competitor's pricing page ([gap-analysis-carbob.md](gap-analysis-carbob.md)) adds plans 12–21 after step 10, in the order below. External plate / VIN lookup, manufacturer labour times, diagrams and procedures, multi-location and Brazil fiscal stay out. Product decisions are open in each plan; plan 21 is blocked on the pricing tiers. |

## Order and migrations

| # | Step | Commit(s) | Migration |
|---|---|---|---|
| 1 | Booking | `feat(booking)` | V15 |
| 2 | Release readiness | `feat(config)!`, `feat(notification)`, `feat(identity)` (leads), `ci` | V16 lead |
| 3 | Landing site | `feat(site)` | — |
| 4 | Shop | `feat(shop)` | V17 |
| 5 | Quotes | `feat(workshop)` | V18 |
| 6 | Workshop floor | `feat(workshop)` | V19 |
| 7 | Invoicing and payments | `feat(billing)` | V20, V21 |
| 8 | Reports | `feat(reporting)` | view only, if needed V22 |
| 9 | Mechanic offline | `feat(pwa)` | idempotency keys, next free number |
| 10 | Leftovers | several small commits | composite FKs, next free number |
| 11 | Scheduling: bays, mechanic calendar ([12](12-scheduling-bays-calendar.md)) | `feat(booking)` | next free number |
| 12 | Customer communication ([13](13-customer-comms.md)) | `feat(notification)` | next free number |
| 13 | Data import ([16](16-data-import.md)) | `feat(customer)` | next free number |
| 14 | Digital inspection ([14](14-digital-inspection.md)) | `feat(workshop)` | next free number |
| 15 | Deferred work and maintenance ([15](15-deferred-quotes-maintenance.md)) | `feat(workshop)` | next free number |
| 16 | Suppliers and parts ordering ([17](17-suppliers-parts-ordering.md)) | `feat(stock)` | next free number |
| 17 | Reporting extension ([18](18-reporting-extension.md)) | `feat(reporting)` | next free number, only if needed |
| 18 | Customer satisfaction ([19](19-customer-satisfaction.md)) | `feat(workshop)` | next free number |
| 19 | Marketing campaigns ([20](20-marketing-campaigns.md)) | `feat(campaign)` | next free number |
| 20 | Plan limits and gating ([21](21-plan-limits-gating.md)), blocked on tiers | `feat(identity)` | next free number |

Migration numbers are claimed in this order. If a step moves, it takes the next free number. An applied migration
is never edited.

## How each step runs

- The step's plan here is written or refreshed first and set to in progress.
- Orca workers, at most two at a time, each in its own worktree. For a full-stack step, backend and frontend work in
  parallel against the contract written first in `docs/api/release-<n>.md` (release 4 starts with the shop).
- Every RANDOM_PORT or DEFINED_PORT test starts its own database container and never touches localhost:5432.
- The gate for each step:
  - `./mvnw clean verify`, with the real counts reported
  - a browser walk on a throwaway database (pt-PT, then EN, at 390 px)
  - code-reviewer, plus security-reviewer for public, auth, payment and file changes, plus dba for migrations
  - at most two fix loops
- Definition of Done: CLAUDE.md §10.
- One Conventional Commit per step (2a–2d separately). Each commit is proposed and made only on the user's OK, and
  nothing is pushed without asking.

## Out of scope until a product decision

- Brazilian fiscal features (NFe, NFS-e, Pix).
- Billing of ASP's own subscriptions.
- GTMotive and TecDoc catalogues.
- An estimate chatbot.
