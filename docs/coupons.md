# Coupons and offers

- Staff/admin: Workspace → Manage coupons & offers (`/staff/coupons`). Create a unique code, name, fixed amount or percentage off, minimum original subtotal, validity period, and membership-stacking policy. Existing terms are immutable; disable an offer or create a new code to change them. Existing order snapshots are retained.
- Customer: My account → Coupons & offers (`/account/coupons`). At checkout, choose an offer. The selection refreshes the price breakdown while preserving delivery/payment fields. Without JavaScript, use the Apply coupon button.
- Each account can use each coupon once, with one coupon per order. No separate claiming step is required; enabled offers are available to all accounts.
- Minimum spend is based on the original merchandise subtotal. For stackable offers, apply membership first, then the coupon. Non-stackable coupons replace membership discounts. A percentage value of 10 means 10% off (pay 90%). Discounts cannot exceed the remaining subtotal.
- Validity is start-inclusive and end-exclusive, using the server timezone displayed on offer pages. Checkout always revalidates status, dates, minimum and prior use.
- A successful checkout records coupon terms and discount alongside the membership snapshot and final payable total. Wallet deduction uses that total. Retrying the same checkout token does not consume a second coupon.
- Cancelling a pending order restores coupon eligibility transactionally, but does not extend its validity. Aftersales refunds use the actual discounted order total and do not restore coupon eligibility.
- A payment/stock failure rolls back the order, coupon use and any wallet entry. The original cart remains intact.

## Database

Apply `src/main/resources/db/017-coupons.sql` once on the database host before running the updated application. Existing migration commands (`python scripts/database.py init` or Host-Start/host-all) include this file automatically. It creates new tables without resetting existing orders.

## Checks

`./mvnw test` (Windows: `mvnw.cmd test`). Coupon tests cover membership stacking, minimum/date/disabled validation, discount caps, immutable historical totals, payment failure, cancellation/reuse, simultaneous checkouts, separate customer eligibility, refund totals, CSRF/role checks, rendered pages and preservation of checkout fields.
