# Copilot Instructions

This repository has a formal AI engineering contract. GitHub Copilot must follow these instructions when making changes.

## Required reading order

1. Read AGENTS.md before modifying code or documentation.
2. Use PROJECT_SPEC.md as the product source of truth.
3. Use ARCHITECTURE.md as the architectural source of truth.
4. Use IMPLEMENTATION_PLAN.md to determine the current phase and task status.
5. Do not assume unchecked tasks are complete.

## Scope and phase discipline

- Do not implement feature work outside the current phase.
- Do not create application screens, entities, repositories, business logic, or financial calculations unless the current phase explicitly permits it.
- Do not skip architecture review or documentation updates when requirements change.
- Do not add backend or cloud functionality unless it is explicitly required and documented.

## Design and change rules

- Read and respect AGENTS.md before any code edit.
- Keep changes consistent with the architecture and requirements.
- Update documentation when architectural decisions change.
- Prefer simple, maintainable, and testable solutions.
- Avoid unrelated changes or speculative refactors.
- Do not remove existing functionality without approval.
- Preserve backward compatibility where practical.

## Verification expectations

- Run the relevant tests or checks before claiming completion.
- If a change affects financial logic, test the deterministic edge cases explicitly.
- Be honest about failures and explain what is failing instead of hiding it.
- Never claim production readiness without evidence.

## Financial correctness requirement

- Do not use floating-point arithmetic for money, prices, percentages, or investment calculations without a documented architectural justification.
- Treat financial correctness and migration safety as first-class requirements.
- Any schema change must include a migration strategy.

## Documentation rules

- Keep AGENTS.md, PROJECT_SPEC.md, ARCHITECTURE.md, DATABASE_SCHEMA.md, and IMPLEMENTATION_PLAN.md consistent.
- If a decision changes architecture or product scope, document it and update affected files.
- Record unresolved design questions explicitly rather than pretending they are solved.
