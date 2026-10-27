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
| [04-shop.md](04-shop.md): online shop, pay at the counter | in progress | |
| [05-quotes.md](05-quotes.md): quotes with a customer approval link | planned | |
| [06-workshop-floor.md](06-workshop-floor.md): mechanic time tracking, photos, checklists | planned | |
| [07-invoicing-payments.md](07-invoicing-payments.md): certified invoicing, Multibanco and MB WAY behind provider adapters | planned | |
| [08-reports.md](08-reports.md): cost vs quote, mechanic productivity | planned | |
| [09-mechanic-offline.md](09-mechanic-offline.md): offline mode for the mechanic app | planned | |
| [10-leftovers.md](10-leftovers.md): composite FKs, OnPush, GDPR tools, shared stores | planned | |
