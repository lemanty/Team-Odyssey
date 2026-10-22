# Order workspace and aftersales

## Entry points

- Staff/admin: `/staff/orders` (status counts, order/customer/date search, 50 rows per page). Pending, preparing, ready and shipping orders precede completed/cancelled orders. Active orders are oldest first within each status.
- Staff/admin: `/staff/returns`, also linked from the staff workspace and order workspace.
- Customer: Account → Returns & aftersales, or Orders → My returns. Open a completed order and choose Request return / aftersales.
- Customer/staff support composer can link a return request belonging to the conversation's customer. If both an order and a return are selected, they must match. Administrators still cannot access customer support.

## Workflow

1. Customer confirms receipt (or staff completes collection), then selects one purchased product/variant, a quantity, reason and optional JPEG/PNG evidence up to 5 MB.
2. `REQUESTED`: staff approves or rejects with a required note. Rejection releases the reserved return quantity.
3. `APPROVED`: arrange the physical return through support. After actual receipt and inspection, staff records a note and marks `RECEIVED`.
4. `RECEIVED`: staff independently chooses whether to restore stock and whether to refund. A final note is required. Completion is terminal and repeated submissions have no additional effect.
5. Wallet payments refund to the original customer's wallet. Offline/legacy orders require explicit confirmation that the refund was completed outside the website; the website records the result and does not perform an external payment or credit a wallet.

Refunds allocate the order's original discounted total to its purchased line snapshots and quantities. Cumulative cent rounding prevents over-refunds across partial returns. Current catalogue price changes do not affect refunds. Completed refunds reduce membership qualifying spend. No expiry window or shipping-label provider is imposed by this implementation.

Quantity reservation, status change, wallet ledger, restocking and audit events are transactional. Owner/order locks serialize competing requests. Original orders and item snapshots remain intact. Images and request details require ownership or staff/admin access; every decision records its actor, time and note.

## Database update

Apply `src/main/resources/db/015_returns.sql`, then `016_support_returns.sql` to the existing database. Both create new tables with `IF NOT EXISTS`; they do not reset existing data. The existing `python scripts/database.py init` migration loop includes both files (see the existing database setup instructions before initializing a different environment). The database host performs migrations; team members connecting to that host do not need separate copies.

## Validation

Run `./mvnw test` (Windows: `mvnw.cmd test`). Return tests cover ownership, CSRF, rendering all processing stages, quantity reservations, duplicate/concurrent completion, discounted variant refunds, transaction rollback, offline refund separation, support links and workspace filters. A real HTTP multipart test covers the customer return form, in addition to MockMvc image tests.
