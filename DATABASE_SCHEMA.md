# Database Schema Contract

This document is the source of truth for the implemented Room/SQLite database contract.

It describes:

- implemented schema
- entity relationships
- persistence invariants
- migration requirements
- indexing and uniqueness rules
- historical-data guarantees
- rules that AI agents must preserve when modifying the database

This is not a future design document. If the implementation differs from this document, the discrepancy must be resolved before continuing development.

---

## 1. Core design principles

The database must preserve financial correctness and historical integrity.

Non-negotiable principles:

- Never use floating-point storage for financial money values.
- Preserve financial history rather than destructively rewriting it.
- Use soft deletion for reference data where historical records may still depend on it.
- Preserve foreign-key references required for historical reporting.
- Financial dates must be explicit and must not be derived from the current timezone.
- Schema changes must use explicit versioned migrations.
- Existing data must be preserved unless a destructive migration has been explicitly approved.
- Database constraints should enforce invariants where SQLite can enforce them.
- Domain/repository validation must enforce invariants that cannot reasonably be represented as database constraints.
- Every schema change requires migration tests.
- Exported Room schemas are part of the database contract.

The current Room database is version 9. Phase 6 required no schema change because the transactions table already contains explicit epoch-day dates, transfer identity, restrictive account/category foreign keys, and soft-delete columns. Correction workflows preserve the original row and insert a replacement in one database transaction; active reads exclude the soft-deleted original. Version 9 adds composite indexes for active date-range scans and latest NAV selection; the 8-to-9 migration is additive and preserves all rows.

---

## 2. Money, quantities, and precision

### 2.1 Money

The application currently supports INR only.

Money is represented as integer minor units:

```text
100 minor units = ₹1.00
1 minor unit    = ₹0.01

### 2.2 Quantities and purchases

Purchase quantity is stored as positive integer `quantity_minor` with explicit decimal `quantity_scale` and unit. It is interpreted as `quantity_minor / 10^quantity_scale`; floating-point arithmetic is not used. Unit prices and purchase totals are integer INR minor units. The domain calculates `unit price x quantity` with decimal arithmetic and rounds once to minor units using `HALF_UP`; mismatched totals are rejected.

Product and purchase references use restrictive foreign keys. Active reads exclude soft-deleted rows while historical references remain preserved. Purchase analytics use inclusive epoch-day ranges and exact aggregation. The existing version 7 schema already contains these fields and constraints, so Phase 7 adds no migration.

Phase 8 analytics requires no schema migration. It reads the existing transaction epoch-day, account, type, amount, category, and soft-delete columns through the repository date-range queries.

Phase 9 recurring expense detection requires no schema migration. It uses the existing transaction date, account, type, amount, category, merchant, and soft-delete fields through the repository date-range queries. Detected patterns are derived analysis results and are not persisted, so historical transaction data remains unchanged.

Phase 18 quarterly analytics also requires no schema migration. Quarter buckets are derived from the existing transaction epoch-day and use the same exact minor-unit aggregation and transfer-exclusion rules as monthly and yearly analytics.

### 2.3 Mutual funds and investment precision

Version 8 adds `mutual_funds`, `mutual_fund_holdings`, `mutual_fund_transactions`, and `mutual_fund_nav_history`. Fund codes and `(fund_id, as_of_date_epoch_day)` NAV entries are unique. Holdings are unique per fund and investment account and reference both the fund and investment account restrictively. Investment events are insert-only and reference the fund, investment account, optional cash account, and optional linked cash transaction restrictively; the linked cash reference is set null if its transaction is removed. NAV entries are dated INR minor-unit values and are insert-only to prevent silent historical replacement.

Unit values use positive scaled integers. Purchases and redemptions store units, NAV, and exact rounded total amount. Holding updates occur atomically with the investment event. A redemption cannot reduce a holding below zero. Valuation uses the latest NAV on or before an explicit local date, and return uses exact minor-unit arithmetic.

### 2.4 Commodity price history

The existing `product_price_history` table stores historical product and commodity price points as insert-only records. Each row is tied to a product with a restrictive foreign key, uses an explicit `as_of_date_epoch_day`, and stores the price in INR minor units. The unique `(product_id, as_of_date_epoch_day)` index prevents duplicate price points for the same product and date.

Active reads exclude deleted history rows. Repository lookups return exact-date matches or the latest available price before or on a requested date, and trend analysis operates over stored points without rewriting history. Cross-product comparisons use the latest available point on or before a requested date.

### 2.5 Performance indexes

Transaction date-range reads use `(account_id, is_deleted, occurred_date_epoch_day)` for account-filtered analytics and `(is_deleted, occurred_date_epoch_day)` for all-account analytics. Price-history reads use `(product_id, is_deleted, as_of_date_epoch_day)` and `(is_deleted, as_of_date_epoch_day)`. Portfolio latest-NAV aggregation uses `(as_of_date_epoch_day, fund_id)`. These indexes are created by `MIGRATION_8_9` and are represented in the exported Room schema.