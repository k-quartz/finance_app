# Testing Strategy

This document defines the target testing approach for a production-grade personal finance application. The project will not treat testing as optional or as a cleanup task after implementation. It is part of the implementation itself.

## 1. Testing principles

- All financial logic must be deterministic and testable.
- Tests must validate exact amounts, rounding behavior, and historical data semantics.
- Business rules must be tested independently from UI implementation details.
- Database schema and migration behavior must be verified before release.
- UI tests should validate user flows and state transitions, not implementation details.
- Performance and regression tests should be part of the release gate for large data sets.

## 2. Test categories

### 2.1 Unit tests

Unit tests will validate pure domain logic, validation, and calculations in isolation.

Examples:

- exact monetary arithmetic
- rounding rules
- transaction validation
- category validation
- account balance composition rules
- percentage and return calculations
- duplicate detection heuristics
- transfer semantics

The domain layer is where most financial-correctness tests belong.

### 2.2 Repository tests

Repository tests cover local data access behavior and correctness of the repository abstraction.

Topics:

- create, read, update, delete workflows
- filtering by date and category
- account summaries and totals
- product and price history lookups
- mutual fund holding reconciliation
- invalid or partial data handling

Repository tests must verify the data contract, not only the DAO implementation details.

### 2.3 Database tests

Database tests will validate the local data model and ensure data integrity under realistic data patterns.

Topics:

- relationships across accounts, transactions, and categories
- soft-delete behavior
- query correctness for monthly, quarterly, and yearly analytics
- handling historical data after category archiving
- data consistency across duplicate or corrected transactions

### 2.4 Migration tests

Migration tests are mandatory because financial history is sensitive and schema changes are high-risk.

They should validate:

- upgrading from previous schema versions
- preserving historical balances, transactions, and prices
- avoiding data loss or silent corruption during migration
- migration failure handling and rollback behavior
- compatibility with archived and soft-deleted records

### 2.5 ViewModel tests

ViewModel tests validate state transitions, user actions, and derived UI state.

Examples:

- loading states
- validation errors
- success and failure states
- analytics summary computation for different date ranges
- empty state and error messaging

### 2.6 UI tests

UI tests verify that the user can complete core flows with realistic interaction. They should remain focused on user-visible behavior.

Examples:

- add account
- create expense transaction
- create transfer between accounts
- enter purchase with quantities and category
- filter analytics by period

UI tests should never assert on mocked implementation internals or test-only IDs.

### 2.7 Integration tests

Integration tests confirm that the app’s components work together under realistic conditions.

Examples:

- transaction creation → repository write → analytics readback
- account update → balance recalculation → UI state refresh
- historical price entry → product price lookup → trend calculation
- mutual fund transaction → holding update → value summary

### 2.8 Financial calculation tests

Financial correctness tests must be treated as a first-class test suite. They must explicitly cover:

- ₹100 × 3 = ₹300
- quantity decimals and rounding
- unit price × quantity = total amount
- zero values and negative values where allowed
- very large amounts and long history sets
- month and year boundaries
- leap years and date transitions
- refunds and partial refunds
- duplicate transactions
- transfer transactions not being counted as expenses
- historical price lookups at boundary dates
- investment return and NAV computations

All tests must use deterministic values and clear expected outputs.

### 2.9 Edge-case tests

The project must test non-trivial real-world edge conditions:

- missing category history after deletion
- archived account still appearing in historical analytics when appropriate
- duplicate product entries with minor variations in name
- price updates at the same date or near boundaries
- negative balances and credit card limits
- zero-quantity or zero-unit-price purchases
- historical products with no current record
- partial redemption or split transactions

### 2.10 Regression tests

Regression tests must be added when defects are found or when important logic is modified.

Examples:

- spend totals incorrectly including transfers
- product price lookups returning the wrong historical date
- recurring detection incorrectly labeling a one-off expense as recurring
- NAV calculations with incorrect rounding or sign handling

### 2.11 Performance tests

Performance tests are required for analytical queries and large datasets.

Examples:

- large transaction histories by month and category
- price history search across many products
- long-running summary calculations
- database query performance for dashboards
- memory efficiency in analytics pipelines

These tests should track stable baseline behavior and help detect slowdowns before release. Phase 15 adds a 50,000-transaction analytics guardrail, Room `EXPLAIN QUERY PLAN` checks for date/filter indexes, and Gradle output reporting through the `PERFORMANCE_BASELINE.md` baseline contract.

## 3. Financial correctness test examples

The test suite must validate the following kinds of rules explicitly:

- exact money arithmetic does not rely on binary floating-point behavior
- rounding is defined and consistently applied
- quantities support decimal precision and do not truncate incorrectly
- unit prices and totals remain coherent for the same item and date
- transfers are excluded or represented according to reporting rules
- refunds are classified and totaled correctly
- net worth and investment value calculations use historical data and current holdings correctly

## 4. Test data strategy

- Use deterministic fixtures with explicit amounts, dates, and categories.
- Prefer small but meaningful datasets that capture boundary conditions.
- Create golden datasets for recurring patterns, investment value calculations, and spend summaries.
- Ensure tests cover both normal and invalid scenarios.

## 5. CI and release gates

The project should eventually run the following in CI:

- unit tests
- database tests
- migration tests
- UI smoke tests
- lint and static checks
- financial calculation regression checks

A release should not proceed unless the financial and migration suites pass.

## 6. Testing status

The strategy is implemented incrementally. Phases 3 through 6 include Room migration/schema tests, domain tests, repository tests, and deterministic financial regression tests. Phase 6 specifically covers insert-only transaction creation, explicit date ordering and boundaries, exact income/expense summaries, transfer exclusion, duplicate detection, inactive-reference rejection, and atomic correction history.

Phase 7 adds deterministic coverage for scaled decimal quantities, invalid quantities, `HALF_UP` rounding, arithmetic overflow, product/category reference validation, purchase total coherence, insert-only persistence, and exact spend/quantity summaries over explicit date ranges.

Phase 8 adds deterministic monthly/yearly analytics coverage for inclusive month/year boundaries, empty buckets, partial reporting ranges, transfer exclusion, category consumption comparisons, account filtering, exact totals, and a 20,000-transaction history performance regression path.

Phase 9 adds deterministic recurring-expense coverage for normalized merchants, weekly cadence, month-end billing across short months, account/date filtering, transfer and income exclusion, irregular intervals, amount outliers, invalid ranges, and confidence thresholds.

Phase 10 adds deterministic investment coverage for scaled unit reconciliation, exact NAV multiplication and rounding, partial redemption, oversell rejection, linked cash direction, historical NAV lookup, valuation, return percentages, and the version 7-to-8 schema contract. UI, performance-baseline, and import/export suites remain scheduled for their respective future phases.

Phase 16 adds production-readiness regression coverage for complete backup round trips, malformed and duplicate-key backup rejection, atomic restore rollback, encrypted backup payloads, and rejection of backdated investment oversells. The release gate also verifies the complete migration chain, exported Room schema, unit tests, lint, debug assembly, and focused debug unit tests.

Phase 17 adds instrumentation navigation regressions verifying that Dashboard reaches Manage, that account/category lifecycle, transaction correction, purchase, investment/NAV, product-price history, and recurring-analysis sections are user-accessible, and that Android back navigation returns from Manage to Dashboard. The ViewModel preserves loaded records across loading, success, and error transitions; direct ViewModel state-transition tests and realistic end-to-end financial workflow tests remain Phase 18 coverage.

Phase 18 adds a realistic repository integration scenario covering account balance reconciliation, transfers, correction history, refunds, category deletion, purchase totals, historical prices, mutual-fund valuation and returns, monthly/quarterly analytics, recurring negative cases, dashboard reconciliation, and complete backup restoration. Quarterly boundary behavior is covered by deterministic unit tests, and Manage exposes monthly, quarterly, and yearly analysis choices.
