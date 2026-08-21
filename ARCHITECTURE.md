# Architecture

## 1. High-level architecture

This project follows a local-first, layered Android architecture designed for correctness, testability, and future extensibility. The V1 design intentionally keeps the app self-contained and independent from a remote backend.

The architecture is organized into the following conceptual layers:

- Presentation layer: UI, state, navigation, and user interaction
- Domain layer: business rules, financial logic, validation, calculations, domain models, and repository abstractions
- Data layer: persistence, Room DAOs, repository implementations, local data access, import/export, and mapping
- Shared support: common utilities, configuration, logging, security, and time abstractions

The dependency direction is:

Presentation -> Domain abstractions
Data -> Domain abstractions

The domain layer must not depend directly on Room, SQLite, Android persistence classes, or concrete data-layer implementations.

Repositories are defined at the abstraction boundary and implemented by the data layer. This keeps business logic independent from persistence technology.

Transaction workflows are exposed through the domain-facing financial repository. Normal expense and income creation is insert-only; retrieval uses explicit `LocalDate` values; summaries use exact minor units and exclude transfers. Duplicate candidates use deterministic amount/date/category/account matching with normalized merchant text. Corrections soft-delete the original transaction and insert the replacement atomically so historical records remain auditable.

Product and purchase workflows follow the same boundary. Quantities are positive scaled integers, unit prices and totals use INR minor units, and purchase totals are calculated with decimal arithmetic and explicit `HALF_UP` rounding. Purchase summaries use inclusive explicit date ranges and exact aggregation.

Backup and restore are handled in the data layer through a versioned JSON snapshot repository. Exports capture the full persisted Room dataset, including inactive and soft-deleted rows, and restores execute within a single Room transaction so any constraint or validation failure rolls back the whole operation. The backup format is schema-aware and rejects malformed or version-mismatched payloads before mutating the database.

The application shell is a small Compose Navigation graph with Dashboard as its start destination and Backup as a persistent bottom-navigation destination. Dashboard remains the Phase 12 root experience; the shell provides the application-level access path required by the V1 local backup capability without inventing destinations for domain features that do not yet have presentation screens. Backup file selection is owned by the Android presentation layer through the system document provider, while export and restore execution remain behind the `BackupRepository` abstraction.

Monthly and yearly transaction analytics use explicit inclusive reporting periods in the domain layer. The calculator creates contiguous date buckets, ignores transfers for income/expense reporting, preserves exact minor-unit category totals, and aggregates each transaction once before producing the latest-bucket category comparison. The repository filters by account and date before invoking the calculator.

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