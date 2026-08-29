# Implementation Plan

This plan tracks the engineering work in phases. A phase is complete only when implementation, tests, internal audit, remediation, verification, and documentation are all consistent.

## Phase 1 — Foundation [x]

Acceptance criteria:

- [x] Repository state is understood and protected.
- [x] Product specification is documented.
- [x] Architecture is documented.
- [x] Conceptual database model is documented.
- [x] Testing strategy is defined.
- [x] AI engineering contract is defined.
- [x] Copilot instructions exist and align with project rules.
- [x] Open architectural questions are explicitly identified.
- [x] No implementation of financial application features has started.

### Phase 1 tasks

- [x] Confirm workspace and repo state.
- [x] Confirm Phase 1 scope restrictions.
- [x] Define project purpose, target user, and V1 boundaries.
- [x] Document architecture and dependency direction.
- [x] Document conceptual schema and financial correctness decisions.
- [x] Define AI governance and operating guardrails.
- [x] Create Copilot-specific instructions.
- [x] Record open questions and unresolved decisions.
- [x] Create changelog format and first entry for Phase 1.

## Phase 2 — Android project setup

- [ ] Create Android project structure and Gradle configuration.
- [ ] Configure Kotlin, Jetpack Compose, and required Android SDK settings.
- [ ] Set up version catalogs and dependency management conventions.
- [ ] Add baseline build/test tasks and CI-ready configuration.
- [ ] Verify project builds in a clean environment.
- [ ] Document local developer setup and environment assumptions.

## Phase 3 — Database foundation

- [x] Define Room database structure for V1 entities.
- [x] Create schema versioning and migration strategy.
- [x] Implement initial database setup and configuration.
- [x] Add database tests for schema integrity and migration behavior.
- [x] Validate local persistence behavior for integrity rules.

### Phase 3 status

The database foundation is implemented as a versioned Room schema using explicit migrations. The app exports the schema to the repository under `app/schemas`, keeps the database versioned instead of relying on destructive resets, and exposes repository objects that represent meaningful local data operations without leaking DAO-only implementation details into the application layer.

## Phase 4 — Account and transaction domain layer [x]

- [x] Define account, category, transaction, transfer, money, and validation models.
- [x] Implement local repository operations with validation before persistence.
- [x] Implement exact minor-unit balance calculation and transfer semantics.
- [x] Add category hierarchy and inactive-reference validation.
- [x] Add deterministic domain and migration regression tests.
- [x] Document account, credit-card, transaction, transfer, category, and money semantics.

Phase 4 deliberately excludes UI, dashboards, reporting screens, cloud synchronization, and mutual fund implementation. Later account/category/transaction phases now represent presentation and workflow work built on this domain foundation.

## Phase 5 — Categories [x]

- [x] Define category taxonomy and hierarchy rules.
- [x] Implement category repository and local persistence.
- [x] Add support for archived and deleted categories without breaking history.
- [x] Add repository and domain tests for category edge cases.

### Phase 5 acceptance contract

A complete category implementation must additionally satisfy:

- inactive or deleted parents cannot be assigned during category creation
- inactive or deleted parents cannot be assigned during re-parenting
- parent and child category types must remain compatible
- self-parenting is rejected
- arbitrary ancestor cycles are rejected
- descendant re-parenting is rejected
- valid multi-level category trees are preserved
- malformed/disconnected hierarchy data cannot cause infinite traversal
- sibling ordering is deterministic
- reorder operations are atomic
- duplicate root categories are rejected
- category deletion is soft deletion
- categories with children cannot be deleted
- historical transaction and purchase references remain valid
- deleted categories cannot be used for new transactions
- creation paths cannot accidentally perform an update/upsert
- public repositories do not expose persistence entities or DAO-shaped mutation APIs
- database constraints and application validation agree
- exported Room schema matches the final migrated database
- migration tests verify data preservation and final constraints

### Phase 5 status

Category management is implemented without changing Phase 4 transaction semantics. Categories support validated domain CRUD, parent/child hierarchy traversal, deterministic sibling ordering and atomic reorder operations, activation/deactivation, and soft deletion.

Deletion rejects categories with children but permits historically referenced leaves through soft deletion. Restrictive foreign keys and retained rows preserve historical references.

Root category uniqueness is enforced at the database level.

All category mutation paths must pass through the category domain/repository rules rather than bypassing them through generic persistence operations.

A phase is not considered complete merely because the tests pass. The implementation must also pass the mandatory AI engineering audit and final verification workflow.

## Phase 6 — Expense/income transactions [x]

- [x] Define transaction domain model and validation rules.
- [x] Implement transaction creation and retrieval flows.
- [x] Add analytics rules for income and expense summarization.
- [x] Add duplicate detection and correction workflows.
- [x] Add tests for transaction ordering, dates, and transfer exclusion rules.

### Phase 6 status

Expense and income transactions use insert-only creation, explicit local dates, active category/account validation, deterministic retrieval ordering, exact minor-unit summaries, transfer exclusion, normalized duplicate detection, and atomic soft-delete correction. Corrections preserve the original row for historical auditability and insert the replacement in the same Room transaction.

No schema change was required: the existing version 7 transaction table already contains the required transfer identity, historical date, and soft-delete columns. The entity, exported Room schema, and migration chain remain at database version 7.

## Phase 7 — Purchase/product tracking

- [x] Define product and purchase domain model.
- [x] Implement purchase persistence and validation.
- [x] Add product price and quantity handling rules.
- [x] Add purchase analytics for total spend and commodity trends.
- [x] Add tests for quantity, unit conversion, and rounding rules.

### Phase 7 status

Products and purchases use domain-facing repositories rather than exposing persistence entities. Creation validates active references, matching units, INR currency, positive scaled-integer quantities, positive unit prices, and exact totals calculated with decimal arithmetic and `HALF_UP` minor-unit rounding. Purchase summaries aggregate exact spend and quantities over inclusive `LocalDate` ranges. Existing version 7 persistence already contains the required fields, so no schema migration was required.

Verification status: implementation and focused regression coverage are present, but the required unit-test and lint tasks are currently blocked by the workstation's Gradle/Kotlin daemon process terminating before completion. Phase 7 must not be reported complete until those tasks produce final results.

## Phase 8 — Monthly/yearly analytics

- [x] Define reporting period model and date buckets.
- [x] Implement monthly and yearly expense aggregation.
- [x] Add category consumption comparisons and trend views.
- [x] Validate analytics against known test datasets.
- [x] Add performance tests for larger history sets.

### Phase 8 status

Monthly and yearly reporting is implemented through a domain analytics calculator and the financial repository. Reporting periods are explicit inclusive `LocalDate` ranges with contiguous month or year buckets, including empty periods and correctly clamped partial boundaries. Analytics preserves exact INR minor units, excludes transfers, retains category totals for historical references, and exposes the comparison between the two latest buckets for category consumption changes.

The calculator aggregates each transaction once and includes a deterministic 20,000-transaction history regression test to guard the larger-history path. No Room schema change was required because Phase 8 uses the existing transaction date, type, amount, category, account, and soft-delete fields.

Implementation, audit remediation, and the complete verification gate are complete. The workstation uses the valid Java 17 installation at `C:\Program Files\Java\jdk-17`; Java/Kotlin 17 compatibility remains unchanged. The gate passed with `git diff --check`, `clean`, `assembleDebug`, `test`, `lint`, and `:app:testDebugUnitTest --no-daemon` (using the valid colon-separated Gradle task path on Windows).

## Phase 9 — Recurring expense detection

- [x] Define recurring-pattern detection rules.
- [x] Implement pattern matching on historical transaction data.
- [x] Add confidence or similarity rules for detection.
- [x] Add tests for recurring edge cases and false positives.

### Phase 9 status

Recurring expense detection is implemented as a pure domain calculation exposed through the financial repository. It filters to positive expenses with a merchant, groups by account/category/normalized merchant, requires three or more observations, recognizes weekly and month-end-safe monthly cadence, rejects irregular intervals and amount changes above 10 percent, and returns deterministic confidence and average exact INR amounts. Detection is inclusive of the requested date range, excludes transfers and income, and requires no database migration because patterns are not persisted.

Implementation, audit remediation, and regression coverage are complete pending the final verification gate.

## Phase 10 — Mutual funds [x]

- [x] Define mutual fund holdings and investment domain model.
- [x] Implement local persistence for fund records and holdings.
- [x] Add investment transaction logic for purchases and redemptions.
- [x] Add valuation and return calculations with deterministic tests.
- [x] Add edge-case coverage for NAV and unit handling.

### Phase 10 status

Mutual fund metadata, per-account holdings, immutable purchase/redemption events, and dated NAV history are implemented through a domain-facing repository and Room version 8 migration. Unit quantities use scaled integers, monetary values use INR minor units, transaction totals use exact decimal multiplication with `HALF_UP` rounding, and holding updates are atomic. Purchases and redemptions can optionally link to direction-checked cash transactions. Valuation uses the latest NAV on or before an explicit date; returns include invested amount, redeemed proceeds, current value, net gain, and an exact percentage.

Focused domain, repository, and migration regression tests are included. The exported Room schema (`app/schemas/com.financialapp.data.local.database.AppDatabase/8.json`) matches `AppDatabase` version 8, and `DATABASE_SCHEMA.md` documents the version 8 mutual fund tables.

Final verification gate passed against the current code state on 2026-08-20 (Windows, JDK 17, `--no-daemon`): `git diff --check` (clean), `.\gradlew.bat clean` (BUILD SUCCESSFUL), `.\gradlew.bat assembleDebug` (BUILD SUCCESSFUL in 1m 2s), `.\gradlew.bat test` (BUILD SUCCESSFUL in 1m 32s, debug and release unit tests), `.\gradlew.bat lint` (BUILD SUCCESSFUL in 1m 17s, no reported issues), and `.\gradlew.bat :app:testDebugUnitTest --no-daemon` (BUILD SUCCESSFUL). A final `git diff --check` after verification remained clean.

## Phase 11 — Commodity/price history

- [x] Define historical price and commodity trend rules.
- [x] Implement price history storage and retrieval.
- [x] Add trend analysis for product and commodity comparisons.
- [x] Add tests for historical date lookups and boundary cases.

### Phase 11 status

Commodity price history is implemented through an insert-only product price history repository backed by the existing Room table. Price points are stored as exact INR minor units with explicit `LocalDate` dates, active product validation, and deleted-row filtering on reads. The repository exposes exact date lookups, latest-price lookups, trend analysis for a bounded date range, and latest-price comparisons across products.

The domain analytics calculator derives single-product trends and cross-product comparisons from stored history without mutating historical records. Trend calculations use the boundary price points inside the requested range, preserve exact minor-unit change values, and compute percentage change with `HALF_UP` rounding. Comparisons use the latest available price on or before the requested date and keep historical rows immutable.

Implementation, audit remediation, regression coverage, and the complete verification gate are complete. The gate passed with `git diff --check`, `clean`, `assembleDebug`, `test`, `lint`, and `:app:testDebugUnitTest --no-daemon` on Windows using Java 17.

## Phase 12 — Dashboard

- [x] Define dashboard metrics and state model.
- [x] Implement summary calculations for net worth and key indicators.
- [x] Add performance-safe aggregation patterns.
- [x] Add dashboard layout and interaction tests.

### Phase 12 status

The dashboard is implemented as a local-first Compose entry screen backed by a dedicated dashboard repository and view model. It summarizes net worth, current and prior period income/expense, savings rate, top expense categories, active account balances, mutual fund portfolio value, and recent commodity price movement.

The repository uses batched reads for active account balances, category trees, active products, mutual fund portfolio valuation, and price comparisons so the dashboard does not rely on per-item N+1 loading. Dashboard state switches between monthly and yearly views and cancels stale refreshes so older responses cannot overwrite a newer selection.

Implementation, audit remediation, regression coverage, and the verification gate are complete.

## Phase 13 — Export/import and backup

- [x] Define export and backup formats.
- [x] Implement secure local backup and restore flows.
- [x] Add import validation rules for CSV or JSON data.
- [x] Add tests for malformed import and partial restore scenarios.

### Phase 13 status

Export and backup are implemented as a local JSON snapshot flow over the full Room database. The backup captures all persisted tables, including inactive and soft-deleted rows, and restore runs in a single Room transaction so partial writes roll back on failure. Backup versioning is explicit and the restore path rejects malformed payloads and schema mismatches with a deterministic domain-level error.

The app shell keeps Dashboard as the start destination and exposes Backup through persistent bottom navigation. The Backup screen uses Android's system document provider to save and select JSON files, then delegates export and restore operations to the existing repository. Domain features without presentation screens remain intentionally unexposed; this phase does not invent unrelated future UI.

Implementation, audit remediation, navigation integration, regression coverage, and the complete verification gate are required before this phase can be reported complete.

## Phase 14 — Security hardening

- [x] Review local data protection and secure storage requirements.
- [x] Implement required encryption or secure storage measures.
- [x] Review logs and error handling for privacy risks.
- [x] Add security-focused regression tests.

### Phase 14 status

Phase 14 hardens the local backup path by encrypting exported backup payloads with a per-device AES-GCM key stored in AndroidX `EncryptedSharedPreferences`. This keeps the exported snapshot protected at rest while leaving the existing domain and database schemas unchanged. The backup repository continues to accept legacy plaintext payloads during restore so older local backups remain usable, but exports are now encrypted and decryption failure is treated as malformed backup input instead of silently trusting untrusted data.

The security audit also confirmed that the app does not log raw financial payloads or expose them in the backup flow; the UI and repository surface remain domain-level and do not print sensitive details. The implementation is covered by a focused regression test validating round-trip encryption/decryption and encrypted persistence semantics.

## Phase 15 — Performance testing

- [x] Benchmark large transactional datasets.
- [x] Profile analytics queries and update loops.
- [x] Review database indexes and query strategies.
- [x] Add performance regression baseline and reporting.

### Phase 15 status

Phase 15 adds deterministic performance regression coverage for 50,000-transaction analytics and Room query-plan checks for the transaction, price-history, and latest-NAV paths. Version 9 adds additive composite indexes through `MIGRATION_8_9`; exported schema generation remains part of the verification gate. Dashboard price comparisons now batch product metadata and price-history reads, removing the previous per-product query loop. Timing output is reported with the `PERFORMANCE_BASELINE.md` contract and remains guarded by a stable 5-second unit-test threshold.

## Phase 16 — Production readiness review

- [ ] Verify architecture, testing, and code quality against the spec.
- [ ] Validate migration, backup, and recovery behavior.
- [ ] Review security, privacy, and financial correctness checks.
- [ ] Conduct a final sign-off review before release.
- [ ] Document remaining risks and known limitations.

### Phase 16 status

The production-readiness audit verified the layered repository boundaries, backup behavior, exact arithmetic, transfer exclusion, investment rounding, and historical event ordering. It fixed two in-scope integrity issues: exports now read all tables in one Room transaction, and mutual-fund holdings are recomputed from date-ordered history after each event so backdated oversells roll back atomically. The standalone account repository now exposes domain models rather than persistence entities or generic upsert/soft-delete APIs. Final sign-off remains blocked by the workstation's Gradle test JVM memory failure.

Known limitations remain intentionally documented: V1 supports INR only, backup restore requires the current database version, and the performance baseline is a deterministic unit-test guardrail rather than a device-specific benchmark. These are release constraints, not unresolved implementation defects.

## Phase 17 — Presentation, Navigation & User Workflows

- [x] Define the application navigation architecture and route structure.
- [x] Implement the main application navigation shell.
- [x] Expose all implemented V1 capabilities through user-accessible screens.
- [x] Implement account management screens and workflows.
- [x] Implement category and subcategory management screens and workflows.
- [x] Implement income, expense, transfer, and correction transaction screens.
- [x] Implement purchase and product tracking screens and workflows.
- [x] Implement mutual fund and investment transaction screens and workflows.
- [x] Implement historical product price and NAV history screens.
- [x] Integrate recurring-expense analysis into the user interface.
- [x] Integrate analytics and dashboard navigation with the underlying domain/repository layers.
- [x] Integrate backup and restore into the application navigation.
- [x] Add loading, empty, validation, error, and success states for user-facing workflows.
- [x] Implement Android back navigation and appropriate screen state handling.
- [x] Ensure presentation logic does not bypass domain validation or repository boundaries.
- [x] Add UI navigation tests for critical user workflow reachability.
- [ ] Add end-to-end tests for core financial workflows.
- [ ] Verify that all V1 capabilities defined in `PROJECT_SPEC.md` are reachable and usable from the UI.

### Phase 17 status

The application shell exposes Dashboard, Manage, and Backup through persistent navigation. Manage now provides repository-backed entry, lifecycle, correction, and read workflows for accounts, categories, income and expenses, transfers, purchases, products, mutual funds, investment events, NAV history, product price history, period summaries, and recurring-expense analysis. Navigation and section-reachability instrumentation tests pass, including Android back navigation from Manage to Dashboard. ViewModel state transitions and realistic end-to-end financial workflows remain Phase 18 coverage.

### Phase 17 acceptance criteria

Phase 17 is complete only when:

- A user can navigate to every implemented V1 feature without developer or database intervention.
- Core account, category, transaction, purchase, investment, analytics, price-history, recurring-analysis, and backup workflows are accessible through the UI.
- All user input passes through the existing domain validation and repository boundaries.
- Navigation state and Android back behavior work correctly.
- User-facing loading, empty, validation, and error states are handled.
- Critical workflows have automated tests.
- No presentation implementation bypasses the established architecture.
- The complete verification gate passes on the final code state.

## Phase 18 — End-to-End V1 Validation & Release Readiness [x]

- [x] Map every V1 functional requirement in `PROJECT_SPEC.md` to an implemented and user-accessible workflow.
- [x] Execute complete end-to-end workflows using realistic financial data.
- [x] Verify account balances against transaction history.
- [x] Verify transfers, corrections, refunds, and historical records.
- [x] Verify category archival/deletion does not corrupt historical reporting.
- [x] Verify purchase quantities, prices, totals, and historical price analysis.
- [x] Verify mutual fund holdings, transactions, NAV valuation, and return calculations.
- [x] Verify recurring-expense detection against known positive and negative cases.
- [x] Verify monthly, quarterly, and yearly analytics against known datasets.
- [x] Verify dashboard values against underlying repository calculations.
- [x] Verify backup and restore preserves all required financial history.
- [x] Verify migrations and restored databases remain compatible with the current schema.
- [x] Perform final security and privacy review.
- [x] Perform final database integrity review.
- [x] Perform final financial-correctness audit.
- [x] Perform final UI and navigation audit.
- [x] Review performance with representative large datasets.
- [x] Resolve all HIGH and MEDIUM findings.
- [x] Document remaining LOW findings and known limitations.
- [x] Run the complete release verification gate.
- [x] Perform final V1 sign-off.

### Phase 18 acceptance criteria

Phase 18 is complete only when:

- Every V1 requirement is implemented, accessible, and verified through the application.
- Core financial calculations reconcile with deterministic expected results.
- Data integrity, migration, backup/restore, and historical-data behavior are verified.
- Critical user workflows have passing automated tests.
- Security and privacy requirements are satisfied.
- No unresolved HIGH or MEDIUM findings remain.
- The complete verification gate passes on the final code state.
- Remaining limitations are explicitly documented.

### Phase 18 status

V1 capabilities are reachable through Dashboard, Manage, and Backup. The Phase 18 integration regression validates the core account, transaction, purchase, price-history, mutual-fund, analytics, dashboard, category-lifecycle, and backup/restore paths with deterministic INR data. Quarterly reporting was added to the domain and Manage analytics workflow because it was required by the V1 specification but missing from the prior implementation. Final audit and release verification passed on the final code state.

Remaining limitations are low-severity release constraints: V1 remains INR-only and local-first, restore accepts only the current backup database version, and performance thresholds are deterministic unit-test guardrails rather than device benchmarks.

## Phase gating

Each phase should only begin when:

- the design is documented
- the acceptance criteria are explicit
- the risks are identified
- the required tests are planned
- the code changes are aligned with the current phase scope

Before a phase may be reported complete, the AI agent must:

1. implement the phase
2. inspect the implementation for correctness and architectural defects
3. audit database constraints and migrations where applicable
4. audit repository boundaries
5. audit concurrency and atomicity
6. audit edge cases and invalid inputs
7. add missing regression tests
8. fix all HIGH findings
9. fix all MEDIUM findings within phase scope
10. re-run the audit after remediation
11. run the complete verification suite
12. verify documentation and exported schemas
13. report exact verification evidence

A future-phase issue may be recorded as technical debt, but an in-scope defect must be fixed before completion.

No future phase should be treated as complete by assumption.