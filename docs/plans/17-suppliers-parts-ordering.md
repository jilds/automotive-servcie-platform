# 17: Suppliers, parts ordering and stocktake

Status: **planned**. Commit `feat(stock): add suppliers, purchase order sending and stocktake`. Migration: number
claimed in build order, next free after V21. Source: [gap-analysis-carbob.md](gap-analysis-carbob.md) (Parts ordering =
Partial; stocktake for Inventory).

## Today

The `stock` module has purchase orders (draft → ordered → received) with a free-text supplier, and adjustments with a
reason. Nothing is sent to the supplier, supplier invoices are not recorded and there is no count of the whole stock.

## Decisions (proposed, to confirm)

| Point | Proposal |
|---|---|
| Module | Inside `stock` (stock still depends only on `identity`). Mail through `notification` on `stock.PurchaseOrderSent`. |
| Supplier master | Name, NIF (PT check when PT), e-mail, phone, address, payment terms (days), notes, active, version. Purchase orders reference a supplier; old free-text rows keep their text. |
| Send | "Send" on an ORDERED purchase order renders a PDF (OpenPDF, like plan 07's sandbox) and mails it to the supplier with the workshop as reply-to. Re-send allowed; each send logged. |
| Supplier invoice | Entered by hand against one or more received purchase orders: number, date, net / VAT / total cents, due date, lines optional. Updates the product cost **only if** the owner ticks it (**open:** last cost vs weighted average; proposal: last cost, shown). |
| Invoice import | **Open:** CSV or e-invoice (UBL / CIUS-PT) import. Proposal: hand entry now, import later. |
| Stocktake | A count session freezes nothing; staff count per bin on the phone; on close each difference becomes an ADJUSTMENT movement with reason `STOCKTAKE` (same lock rules as adjustments). Products moved during the count are flagged for a recount. |

## Data (RLS ENABLE + FORCE, `tenant_isolation`)

- `supplier`; `purchase_order.supplier_id` (nullable for old rows); `purchase_order_send` (sent at / by, to).
- `supplier_invoice`, `supplier_invoice_purchase_order` (link), `supplier_invoice_line` (optional).
- `stocktake` (status OPEN / CLOSED / CANCELLED, scope: all or bins), `stocktake_count` (product, counted, expected at
  close, counted by / at). One OPEN stocktake per workshop (partial unique index).
- Isolation tests for each table.

## Rules

- Supplier names unique per workshop, case ignored (`duplicate-supplier`).
- Owner writes suppliers; owner and front desk send purchase orders, enter supplier invoices and run stocktakes;
  mechanics may count (name, SKU, bin; no cost) but not close.
- Closing a stocktake locks products in `StockReservations.LOCK_ORDER`; never below 0.

## API

- `GET/POST/PUT /api/v1/suppliers`; `POST /api/v1/purchase-orders/{id}/send`; `GET .../{id}/pdf`.
- `GET/POST/PUT /api/v1/supplier-invoices`.
- `POST /api/v1/stocktakes`, `PUT /api/v1/stocktakes/{id}/counts/{productId}`, `POST .../{id}/close`, `.../cancel`.

## Frontend

- `/stock/suppliers`, supplier picker on purchase orders, send button; `/stock/supplier-invoices`;
  `/stock/stocktake` phone-first count screen (scan SKU later). Routes added to `SpaForwardingController`.

## Error codes

- `supplier-not-found`, `duplicate-supplier`, `supplier-email-missing`, `stocktake-open`, `stocktake-closed`,
  `supplier-invoice-duplicate` (same supplier + number). Three languages.

## Tests

- PDF content and cents; mail event only with an e-mail; stocktake close writes the right adjustments, recount flag on
  concurrent movement, one open stocktake; mechanics see no cost; isolation.

## Out of scope

- Supplier catalogues / price lists via TecDoc, EDI ordering, supplier payments (plan 07 area), barcode scanning.

## Done when

- CLAUDE.md §10; CLAUDE.md §8 "Stock" updated; dba review of the migration.
