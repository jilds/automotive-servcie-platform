# Gap analysis: Carbob feature list vs ASP

Date: 2026-11-09. Compares the feature names listed on Carbob's pricing page with what ASP has today. Only features
are compared; no Carbob text is copied. Plans are listed in [README.md](README.md), the order in
[roadmap.md](roadmap.md).

Status: **Covered** = built; **Partial** = part of it built; **Missing** = not built.

| Feature | ASP status | Plan |
|---|---|---|
| Unlimited users | Covered | — |
| Quotes with digital approval | Covered | [05](05-quotes.md) |
| Repair orders | Covered | — |
| Work board | Covered | — |
| Scheduling | Partial (slots and cars per slot; no bays or mechanic calendar) | [12](12-scheduling-bays-calendar.md) |
| Parts ordering | Partial (purchase orders; no supplier master or sending) | [17](17-suppliers-parts-ordering.md) |
| Inventory and low stock | Covered (stocktake in 17) | [17](17-suppliers-parts-ordering.md) |
| Reports and dashboard | Partial (owner dashboard) | [08](08-reports.md), [18](18-reporting-extension.md) |
| Plate / VIN lookup | Partial (local only; external lookup deferred) | — |
| Payments | Partial (shop records a payment label) | [07](07-invoicing-payments.md) |
| Invoicing integration | Missing | [07](07-invoicing-payments.md) |
| Customer communication (SMS, WhatsApp, tracking) | Partial (e-mail and status links) | [13](13-customer-comms.md) |
| Time logging and mechanic app | Missing | [06](06-workshop-floor.md), [09](09-mechanic-offline.md) |
| Digital diagnosis with photos / video | Missing | [06](06-workshop-floor.md), [14](14-digital-inspection.md) |
| Service guide | Partial (own catalogue with standard hours; manufacturer data deferred) | — |
| Scheduled maintenance | Missing | [15](15-deferred-quotes-maintenance.md) |
| Deferred quotes | Missing | [15](15-deferred-quotes-maintenance.md) |
| Parts / inventory reports | Missing | [18](18-reporting-extension.md) |
| Performance reports | Missing | [08](08-reports.md), [18](18-reporting-extension.md) |
| Customer satisfaction | Missing | [19](19-customer-satisfaction.md) |
| Marketing campaigns | Missing | [20](20-marketing-campaigns.md) |
| Diagrams and procedures | Missing, deferred | — |
| Data migration | Missing | [16](16-data-import.md) |
| Plan quotas (lookups, SMS) | Missing | [21](21-plan-limits-gating.md) |

## Not planned now

- External plate / VIN lookup (a paid data provider; would count against plan 21 quotas).
- Manufacturer labour-time guide.
- Diagrams and repair procedures through TecDoc, TecRMI or GTMotive (CLAUDE.md §1: deferred).
- Multi-location (one company with several workshops under one account).
- Brazilian fiscal features (NFe, NFS-e, Pix).

## Open decisions raised by the new plans

- 12: reminder lead time; customer picks a mechanic; bay types.
- 13: SMS and WhatsApp provider, sender ID, message cost; separate WhatsApp opt-in; totals on the tracking page.
- 14: video (size, storage, cost); legal value of the e-signature; re-publishing an inspection.
- 15: per-line choices on the public quote page; interval templates.
- 16: async import for big files; "update existing" on re-run; other sources; paid migration service.
- 17: product cost rule (last vs weighted average); supplier invoice import format.
- 18: revenue source (work orders vs invoices); comeback window.
- 19: survey delay; consent for surveys; review link; front desk access to results by mechanic.
- 20: per-channel consent; retention period; add-on or tier feature; privacy notice text.
- 21: tiers and limits (blocking); hard vs soft limits; add-ons vs tiers; admin read of usage under RLS.
