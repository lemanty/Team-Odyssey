# Operations analytics

Open Management → Operations analytics as an administrator or staff member.

## Database upgrade

Before deploying this version, apply the existing numbered SQL migrations through
026-stock-adjustments.sql. The host launcher applies migrations automatically.
For an existing local database, run:

```sh
python3 scripts/database.py init
```

Migration 026 creates the inventory workspace adjustment history table. Existing
stock quantities are preserved. Earlier adjustments cannot be reconstructed.
History covers changes submitted from the inventory workspace and records actor,
reason, option ID, before/after quantities and time.

## Report definitions

- Sales include orders currently completed, selected by their placement date.
  Previous-period comparisons use the immediately preceding equal-length period.
- Daily totals use discounted order amounts. Product and category amounts are
  before order-level discounts. Category shares use current catalogue categories.
- Product return rates divide completed returned units by sold units for the same
  order cohort, including returns completed after the selected order period.
- Return reports select requests by request date. Closed requests show processing
  hours. Open requests show waiting hours. Reasons group identical free text.
- Inventory is a current snapshot, independent of report dates. Suggested top-up
  brings available stock one unit above the configured threshold.
- CSV exports include all matching rows. Browser tables paginate at 50 rows.
  Exported text is quoted and formula-leading values are escaped for spreadsheets.

## Validation

Run `./mvnw test`. The automated tests cover totals, date validation, exports,
staff permissions, stock threshold boundaries and adjustment transaction history.
Browser verification uses disposable local accounts and data. Production MySQL
verification remains necessary before release.
