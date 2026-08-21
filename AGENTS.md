# AI Engineering Contract

This repository is a personal financial management and analytics application intended for a single user, offline-first use on Android. This document defines the non-negotiable engineering guardrails for all AI-assisted work.

## Core principles

- Do not silently change requirements.
- Do not mark work complete without verification evidence.
- Do not create placeholder implementations unless explicitly requested.
- Do not remove existing functionality without approval.
- Do not modify architecture casually or without updating the relevant design documentation.
- Prefer simple, maintainable solutions over unnecessary abstraction.
- Preserve backward compatibility where practical.
- Document significant architectural decisions and rationale.
- Tests are part of implementation, not optional cleanup.
- Never claim something is production-ready without evidence.
- A passing test suite is necessary but does not prove correctness.
- AI agents must audit their own implementation before reporting completion.
- Fix discovered HIGH and MEDIUM issues within the current phase before completion.
- Do not delegate fixable in-scope problems back to the user.

## Required workflow

1. Read this file before making changes.
2. Read the project source-of-truth documents:
   - `PROJECT_SPEC.md` for product scope and requirements
   - `ARCHITECTURE.md` for design decisions and constraints
   - `DATABASE_SCHEMA.md` for persistence rules
   - `IMPLEMENTATION_PLAN.md` for current phase progress
   - `CHANGELOG_AI.md` for previous AI decisions and known issues
   - `AI_ENGINEERING_RULES.md` for the mandatory implementation and audit workflow
3. Inspect existing source code and tests relevant to the current phase.
4. Confirm the task fits the current phase before editing code or documentation.
5. Make the smallest change that addresses the requirement.
6. Add or update tests as part of the implementation.
7. Perform a complete internal audit of the implementation.
8. Fix all HIGH and MEDIUM findings that belong to the current phase.
9. Re-run the audit after remediation.
10. Run the complete verification suite.
11. Verify documentation, migrations, exported schemas, and implementation are consistent.
12. Only then report the phase as complete.
13. Do not create a Git commit unless explicitly instructed.

The AI must not stop after discovering a fixable problem and simply report it to the user. If the problem belongs to the current phase, the AI must fix it before completion.

## Requirements and scope rules

- Phase 1 is limited to foundational documentation and architecture work. Do not implement application features, database entities, repositories, ViewModels, or UI screens in this phase.
- Do not implement a backend or cloud architecture unless the requirement explicitly calls for it and the documentation is updated.
- This project is intentionally offline-first and local-first for V1.
- Future cloud synchronization must not be coupled into the V1 domain model or app architecture without a clear migration path.
- Do not implement future-phase functionality merely because it is useful to the current implementation.
- If a future requirement is discovered, record it as future technical debt or an open architectural question instead of implementing it early.

## Financial correctness rules

- The application must not use floating-point arithmetic casually for money, prices, percentages, quantities, or investment values.
- Monetary values must be represented in a deterministic, exact form appropriate for financial correctness.
- Any financial calculation must be testable with explicit deterministic inputs and outputs.
- Rounding policy must be documented and consistently applied.
- Currency conversion, price history, investment NAV, and profit calculations must be modeled with clear rules and explicit edge-case handling.
- Historical data must never be silently rewritten when the underlying logic changes without an approved migration strategy.
- Financial records must not be accidentally overwritten, duplicated, resurrected, or reassigned through alternate persistence paths.

## Architecture rules

- Respect separation of concerns: presentation, domain, and data should not be mixed.
- Domain code must not depend directly on Room, SQLite, DAO implementations, or Android persistence classes.
- Repository interfaces belong to the appropriate abstraction/domain boundary; persistence implementations belong in the data layer.
- Keep dependency direction from higher-level abstractions to lower-level implementations through interfaces.
- Prefer repository abstractions over direct data access from the UI or business logic.
- Local persistence and data access must be isolated from presentation concerns.
- Business logic should remain deterministic and testable outside UI code.
- Any schema change requires a migration strategy and documentation update.
- Recovery from corrupted or partial data must be considered as part of schema and data handling design.
- Public repositories must not expose persistence entities or DAO-shaped mutation APIs.

## Testing rules

- Unit tests, repository tests, database tests, migration tests, and financial calculation tests are required for implementation work.
- Tests must cover both valid and invalid behavior for important rules.
- Regression tests are mandatory for previously discovered bugs.
- Financial correctness tests should explicitly cover rounding, price changes, quantity handling, duplicate transactions, refund logic, month/year boundaries, date conversion, and transfer semantics.
- Tests must be deterministic and should avoid depending on the current clock unless the code under test explicitly uses time injection.
- Migration tests must verify data preservation, constraints, indexes, foreign keys, defaults, and schema consistency where applicable.
- Tests must not merely reproduce the implementation's current behavior; they should verify externally meaningful requirements.

## Security and privacy rules

- Treat personal financial information as highly sensitive.
- Do not log raw financial details, account numbers, or full transaction payloads by default.
- Do not expose secrets, tokens, or local secure settings in logs or error messages.
- Any future synchronization, backup, or export strategy must include protection of data at rest and in transit.
- Do not implement authentication or cloud synchronization in V1 unless the project explicitly requires it and the architecture is updated.

## Documentation expectations

- Update documentation when architectural decisions change.
- When a change affects requirements, financial logic, storage design, migration strategy, or testing, document the rationale and consequences.
- Keep design docs consistent with implementation status and plan state.
- Do not leave documentation claiming that an already-implemented decision is still unresolved.
- Do not mark a phase complete until implementation, tests, audit findings, migrations, schema exports, and documentation agree.

## Final responsibility

No AI agent or contributor should decide on the architecture or scope unilaterally when a requirement, risk, or migration issue is unclear.

If an important decision is unresolved:

1. Record it explicitly in the design documents.
2. Do not silently invent a requirement.
3. Continue only where the existing requirements are unambiguous.
4. Call out the unresolved decision in the final report.

An AI agent is responsible for delivering a verified implementation, not merely identifying defects.