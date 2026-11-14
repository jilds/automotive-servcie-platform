# 19 Board customer name, Delivered column, observations

Status: done (code in the working tree, not committed).

Request (2026-11-11): customer name below plate and car on the board card; a Delivered column with a way to remove finished
work later; observations on the vehicle history and on the customer.

Decisions:
- Customer name: batch `CustomerManagement.customerNames` (one query), looked up only for non-mechanics; null for mechanics.
- Delivered: stays DELIVERED (billing, reports, history); `work_order.board_hidden_at/by` (V25) hides it from the board.
  Hide one or all (owner, front desk). Delivered cards older than 30 days leave the board by themselves.
- Observations: dated list with author, insert only (`vehicle_observation`, `customer_note`, V25, RLS). Vehicle observations
  for every role, customer notes for owner and front desk.

Contract: `docs/api/release-4.md` ("Work order and board", "Observations and notes").
