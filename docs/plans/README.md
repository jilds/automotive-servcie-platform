# Plans

Every plan for this project lives here, next to the code it changes.

Rules:
- One plan per step, written (or refreshed) **before** the code. Its status moves from planned to in progress, then to done.
- When a decision changes, change the plan in the same commit as the code.
- When the step is committed, set the status to done and add the commit hash.
- The order of the steps and the decisions behind it are in [roadmap.md](roadmap.md).

| Plan | Status | Commit |
|---|---|---|
| [jpa-adapter-upsert.md](jpa-adapter-upsert.md): one generic save for the Jpa adapters | done | `9c122a8` |
| [00-public-foundation.md](00-public-foundation.md): add-ons, public page scope, rate limits (ADR 0004) | done | `12974b5` |
| [01-booking.md](01-booking.md): public online booking with slots and check-in | done | `3890d6c` |
| [02-release-readiness.md](02-release-readiness.md): production profile, e-mail, leads, CI and deployment | done | `b36bd7e`..`3971ac7` |
| [03-landing-site.md](03-landing-site.md): static landing site `site/` | done | `ef0386d`..`fc0472a` |
| [04-shop.md](04-shop.md): online shop, pay at the counter | done | `f295a32`..`5a1dc8f` |
| [05-quotes.md](05-quotes.md): quotes with a customer approval link | done | `61bb563`..`f593bb8` |
| [06-workshop-floor.md](06-workshop-floor.md): mechanic time tracking, photos, checklists | done | |
| [07-invoicing-payments.md](07-invoicing-payments.md): certified invoicing, Multibanco and MB WAY behind provider adapters | done (sandbox providers; real providers pending) | |
| [08-reports.md](08-reports.md): cost vs quote, mechanic productivity | planned | |
| [09-mechanic-offline.md](09-mechanic-offline.md): offline mode for the mechanic app | planned | |
| [10-leftovers.md](10-leftovers.md): composite FKs, OnPush, GDPR tools, shared stores | planned | |
| [11-landing-site-v2.md](11-landing-site-v2.md): pricing, FAQ, migration, about, demo, blog, richer home | done | |
| [12-scheduling-bays-calendar.md](12-scheduling-bays-calendar.md): bays, mechanic calendar, overlap checks, reminders | planned | |
| [13-customer-comms.md](13-customer-comms.md): SMS and WhatsApp ports, consent, repair tracking page | planned | |
| [14-digital-inspection.md](14-digital-inspection.md): inspection items with photos, customer report link, e-signature later | planned | |
| [15-deferred-quotes-maintenance.md](15-deferred-quotes-maintenance.md): deferred work with follow-up, service intervals and due list | planned | |
| [16-data-import.md](16-data-import.md): CSV import of customers and vehicles with dry run | planned | |
| [17-suppliers-parts-ordering.md](17-suppliers-parts-ordering.md): suppliers, sending purchase orders, supplier invoices, stocktake | planned | |
| [18-reporting-extension.md](18-reporting-extension.md): revenue, quote conversion, parts and performance reports | planned | |
| [19-customer-satisfaction.md](19-customer-satisfaction.md): survey link after delivery, results and scores | planned | |
| [20-marketing-campaigns.md](20-marketing-campaigns.md): consent-based segments, sending, unsubscribe | planned | |
| [21-plan-limits-gating.md](21-plan-limits-gating.md): quotas, usage counters and feature gating per plan (blocked on tiers) | planned | |

Plans 12–21 come from the feature comparison in [gap-analysis-carbob.md](gap-analysis-carbob.md) (2026-11-09).
