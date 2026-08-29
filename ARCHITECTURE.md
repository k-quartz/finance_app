# Architecture

## 1. High-level architecture

This project follows a local-first, layered Android architecture designed for correctness, testability, and future extensibility. The V1 design intentionally keeps the app self-contained and independent from a remote backend.

The architecture is organized into the following conceptual layers:

- Presentation layer: UI, state, navigation, and user interaction
- Domain layer: business rules, financial logic, validation, calculations, domain models, and repository abstractions
- Data layer: persistence, Room DAOs, repository implementations, local data access, import/export, and mapping
- Shared support: common utilities, configuration, logging, security, and time abstractions

Local backup security is handled in the shared support/security boundary. Exported financial snapshots are encrypted before they are written to disk, and the encryption key is stored in AndroidX `EncryptedSharedPreferences` using the Android Keystore-backed AES-GCM implementation. Restore accepts both encrypted and legacy plaintext payloads to preserve older local backups, but malformed ciphertext is rejected before any database mutation.

The dependency direction is:

Presentation -> Domain abstractions
Data -> Domain abstractions

The domain layer must not depend directly on Room, SQLite, Android persistence classes, or concrete data-layer implementations.

Repositories are defined at the abstraction boundary and implemented by the data layer. This keeps business logic independent from persistence technology.

Transaction workflows are exposed through the domain-facing financial repository. Normal expense and income creation is insert-only; retrieval uses explicit `LocalDate` values; summaries use exact minor units and exclude transfers. Duplicate candidates use deterministic amount/date/category/account matching with normalized merchant text. Corrections soft-delete the original transaction and insert the replacement atomically so historical records remain auditable.

Product and purchase workflows follow the same boundary. Quantities are positive scaled integers, unit prices and totals use INR minor units, and purchase totals are calculated with decimal arithmetic and explicit `HALF_UP` rounding. Purchase summaries use inclusive explicit date ranges and exact aggregation.

Backup and restore are handled in the data layer through a versioned JSON snapshot repository. Exports capture the full persisted Room dataset, including inactive and soft-deleted rows, and restores execute within a single Room transaction so any constraint or validation failure rolls back the whole operation. The backup format is schema-aware and rejects malformed or version-mismatched payloads before mutating the database.

Backup exports read all tables inside one Room transaction to prevent cross-table snapshots from observing concurrent mutation. Restore rejects duplicate primary keys before clearing the target database, while database constraints continue to reject invalid references and duplicate natural keys.

The account repository exposes domain accounts only; Room entities and generic persistence mutations remain internal to the data layer. Mutual-fund holdings are recomputed from the complete date-ordered event history after each insert inside the same transaction, so backdated events cannot diverge persisted holdings from historical valuation behavior.

The application shell is a small Compose Navigation graph with Dashboard as its start destination and Dashboard, Manage, and Backup as persistent bottom-navigation destinations. Manage exposes the implemented account, category, transaction, purchase, product, investment, historical-price, NAV, analytics, and recurring-analysis workflows. Backup file selection is owned by the Android presentation layer through the system document provider, while export and restore execution remain behind the `BackupRepository` abstraction.

Phase 17 adds a persistent Manage destination. Its workflow ViewModel accepts user input, constructs domain models, loads domain records through repository read APIs, and delegates every mutation to repository abstractions; Compose does not access Room entities or DAO operations. Account, product, and category lifecycle operations remain soft-delete or active-state transitions, preserving historical references.

Monthly, quarterly, and yearly transaction analytics use explicit inclusive reporting periods in the domain layer. The calculator creates contiguous date buckets, ignores transfers for income/expense reporting, preserves exact minor-unit category totals, and aggregates each transaction once before producing the latest-bucket category comparison. The repository filters by account and date before invoking the calculator. Manage exposes all three granularities.

Performance-sensitive repository paths use bounded batch reads. Dashboard product comparisons load product metadata and requested price history with one query each rather than per-product reads, while Room date-range and latest-NAV queries use explicit composite indexes. The performance baseline and query-plan regression tests are documented in `PERFORMANCE_BASELINE.md`.

Recurring expense detection is a local domain analysis over persisted transaction history. It considers only positive expense transactions with a non-blank merchant, groups by account/category/normalized merchant, requires at least three observations, and recognizes weekly or monthly cadence only when every consecutive date interval matches the cadence tolerance. Amounts must remain within 10 percent, and the returned confidence score combines cadence consistency (60 percent) and amount similarity (40 percent). No recurring pattern is persisted and transfers are never candidates.

Mutual fund workflows use a domain-facing repository backed by dedicated Room tables for fund metadata, per-fund/per-investment-account holdings, immutable purchases/redemptions, and dated NAV history. Fund units are positive scaled integers; NAV and transaction amounts are INR minor units; NAV multiplied by units is calculated with `BigDecimal` and `HALF_UP` rounding. Purchases increase holdings and redemptions decrease them atomically in the same database transaction, with oversell rejection. Optional linked cash transactions must match the event direction and exact amount. Valuation uses the latest NAV on or before an explicit date, and return is calculated as redeemed proceeds plus current value minus invested amount.

## 2. Simple architecture diagram

```mermaid
flowchart LR
    UI[Presentation Layer\nJetpack Compose / UI State / ViewModel]
    DOMAIN[Domain Layer\nModels / Rules / Validation / Calculations / Repository Interfaces]
    DATA[Data Layer\nRepository Implementations / DAOs / Mappers / Local DB]
    STORE[(SQLite / Room)]
    SEC[Security / Logging / Config]

    UI --> DOMAIN
    DATA --> DOMAIN
    DATA --> STORE
    DOMAIN --> SEC
    UI --> SEC