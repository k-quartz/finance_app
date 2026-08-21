# Project Specification

## 1. Product purpose

This application is a personal financial management and financial analytics tool designed for a single user who wants an offline-first, local-first system for understanding spending, net worth, investments, and historical price trends without relying on a remote backend.

The product is intended to help a user answer questions such as:

- How much was spent this month or this year?
- Which categories dominate spending?
- Which expenses are recurring?
- How has spending changed over time?
- How have product or commodity prices changed over time?
- What was the price of a commodity or investment at a historical date?
- What is the current value of an investment portfolio?
- What is the investment return and net worth?

## 2. Target user

The primary user is a financially literate individual who:

- manages personal accounts locally
- wants privacy and control over data
- operates without a cloud dependency for core usage
- values data correctness and historical analysis
- may need to track both everyday spending and long-term investment holdings

## 3. V1 scope

V1 is intentionally constrained and should only cover the foundational personal finance and analytics capabilities that can be implemented locally.

### In scope for V1

- Financial account tracking for bank, cash, credit card, investment, and other account types
- Income and expense transaction entry
- Transfers between accounts
- Basic category and subcategory management
- Purchase records with commodity, quantity, unit, unit price, total amount, date, merchant, and notes
- Basic product and commodity tracking
- Local analytics for expense totals by month, year, and category
- Recurring expense pattern detection as a local analysis feature
- Mutual fund holdings, purchases, redemptions, units, and NAV tracking
- Historical price and NAV tracking for analysis
- Local net worth and investment value summaries
- Local import/export or backup capability

### Out of scope for V1

- Multi-user support
- Remote backend services or cloud-managed data
- Authentication or account login
- Real-time bank aggregation or external financial APIs
- Server-side processing or API-driven sync
- Budgeting rules tied to automatic notifications beyond local app functionality
- Advanced AI-generated financial advice

## 4. Future scope

Future versions may incorporate:

- secure cloud synchronization with explicit conflict resolution rules
- multiple currencies and exchange-rate history
- richer investment support such as stocks, ETFs, bonds, and crypto
- imported bank statements and CSV import
- recurring transaction automation
- report export and sharing
- advanced dashboards and trend analytics
- external product-price feeds and exchange-rate sources

## 5. Explicit non-goals

The following are not part of V1 and should not be assumed as hidden requirements:

- Real-time banking connectivity
- Account opening or lending flows
- Tax preparation or tax filing
- Social or collaborative financial features
- Backend deployment or multi-device synchronization
- Payment processing or payment rails
- AI-generated recommendations as a primary feature

## 6. Major functional requirements

### 6.1 Accounts

- Users must be able to define and manage financial accounts.
- Each account must know its type, name, currency, and active state.
- Account balances must be derived or reconciled consistently from transactions and holdings.

### 6.2 Transactions

- Users must be able to record income, expense, transfer, and investment transactions.
- Transactions must support date, amount, currency, category, notes, and account references.
- Transfers must not be double-counted as expense or income in aggregate reporting.

### 6.3 Purchases and product records

- Users must be able to track product purchases including quantity, unit, rate/unit price, total amount, category, merchant, and notes.
- A purchase may use a product record or a commodity-level record.
- Historical price lookups must be supported for analysis.

### 6.4 Categories

- Categories and subcategories must support classification and filtering.
- Archived or deleted categories must not corrupt historical transaction reporting.

### 6.5 Investment support

- Users must be able to maintain mutual fund holdings and record purchases and redemptions.
- Historical NAV values and valuation snapshots must support analysis and return calculations.
- The architecture must allow extension to other investment types without major redesign.

### 6.6 Analytics

- The application must support local monthly, quarterly, and yearly summaries.
- The user must be able to compare period-over-period spend and price movement.
- The system must support analysis of recurring patterns and value changes over time.

## 7. Major non-functional requirements

- Offline-first local utility with no required backend connectivity in V1
- Privacy-sensitive personal data stored locally with secure handling by default
- High data correctness standards for financial calculations
- Deterministic behavior and reproducible tests for calculations
- Maintainable architecture with clear separation of responsibilities
- Android-native implementation using Kotlin and Jetpack Compose
- Clear migration strategy for schema evolution
- Graceful handling of invalid, duplicate, and historical data

## 8. Financial correctness requirements

The project must explicitly prioritize correctness over convenience. This application is not a casual ledger tool; its value depends on accurate financial analysis.

The architecture must account for:

- monetary precision and exact arithmetic
- rounding policy and rounding boundaries
- currency codes and local currency context
- quantities and unit conversions
- unit prices and total amounts
- percentages and returns
- date, time, and timezone semantics
- recurring transaction detection logic
- transaction ordering and historical sequence
- duplicate detection and correction workflows
- transfers between accounts and their reporting treatment
- archived or deleted category handling
- historical product prices and investment values
- database migration safety for historical data

The application must not use floating-point types casually for money, prices, or investment calculations. Deterministic precision rules must be defined before implementation.

## 9. V1 assumptions and constraints

- This is a single-user Android app.
- Data remains local unless a future synchronization feature is explicitly added.
- No server-side transaction processing is assumed.
- Security and privacy must be designed in from the beginning, even though cloud sync is deferred.
- The architecture must remain extensible for later cloud synchronization without requiring a full redesign.
