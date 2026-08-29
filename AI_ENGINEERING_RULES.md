## 0. AI Output and Communication Rules

The AI agent is primarily an implementation and verification executor, not a
narrator.

During normal execution:

- Do not explain implementation steps to the user.
- Do not summarize every file changed.
- Do not describe routine commands being executed.
- Do not provide progress reports unless explicitly requested.
- Do not repeat requirements already present in repository documentation.
- Do not explain successful test results individually.
- Do not provide unsolicited architectural explanations.
- Do not ask for confirmation for decisions that are already defined by the
  repository rules.
- Do not stop to report routine findings that can be fixed within the current
  phase.

The agent should silently:

1. Inspect the required files and source code.
2. Implement the requested phase.
3. Audit the implementation.
4. Fix HIGH and MEDIUM findings within scope.
5. Add or update regression tests.
6. Update required documentation.
7. Run the required verification.
8. Remediate failures and repeat verification.

### Normal completion output

When all requirements and verification gates pass, output only:

`PHASE X COMPLETE — AUDIT PASS`

Do not provide a summary unless explicitly requested.

### Blocked execution

If the agent cannot legitimately complete the phase, output only a concise
blocking report containing:

- `PHASE X BLOCKED`
- the specific blocker
- the command or operation that failed
- whether the blocker is code-related or environment/tool-related
- the minimum action required to continue

Do not provide general explanations, implementation summaries, or unrelated
diagnostics.

### User intervention

Ask the user a question only when:

- a required architectural/product decision is genuinely ambiguous and cannot
  be resolved from repository documentation;
- an operation requires explicit user authorization;
- the environment prevents further progress and the agent cannot resolve it
  safely.

Otherwise, make the appropriate implementation decision according to the
repository contract and continue.

### Token-efficiency rule

Prefer execution over narration.

Do not spend output tokens describing work that can be represented by successful
execution and the final completion status.

Detailed explanations, changed-file summaries, test inventories, audit reports,
or implementation rationale should only be produced when the user explicitly
requests them or when they are necessary to resolve a blocker.


# AI Engineering Rules

This document defines the mandatory workflow for all AI-assisted development in this repository.

These rules apply to every implementation phase and every AI coding agent.

---

## 1. Phase Execution Workflow

For every Phase X:

### Step 1 — Understand

Read:

- `AGENTS.md`
- `IMPLEMENTATION_PLAN.md`
- `ARCHITECTURE.md`
- `DATABASE_SCHEMA.md`
- `CHANGELOG_AI.md`
- `PROJECT_SPEC.md`
- existing source code relevant to the phase
- existing tests relevant to the phase

Determine:

- exact phase scope
- acceptance criteria
- existing implementation state
- existing constraints
- migration requirements
- known risks

Do not begin implementation until the phase scope is understood.

### Step 2 — Implement

Implement the complete current-phase scope.

Do not intentionally leave known required functionality unfinished.

Do not implement future-phase functionality.

Do not create placeholder implementations unless explicitly requested.

### Step 3 — Test During Implementation

Add or update tests as part of implementation.

Tests must cover:

- valid behavior
- invalid behavior
- boundary conditions
- data-integrity rules
- regression cases
- migration behavior where applicable

Do not wait until the end to discover that the implementation is not testable.

### Step 4 — Mandatory Internal Audit

After implementation, perform a fresh audit of the actual code.

Do NOT assume that passing tests means the implementation is correct.

Inspect:

#### Correctness

- business rules
- domain invariants
- null handling
- invalid inputs
- duplicate handling
- boundary conditions
- state transitions
- accidental bypasses

#### Data integrity

- database constraints
- uniqueness
- NULL uniqueness behavior
- foreign keys
- delete actions
- soft deletion
- historical references
- accidental overwrites
- accidental resurrection
- alternate mutation paths

#### Architecture

- domain/data separation
- repository boundaries
- DAO exposure
- entity leakage
- dependency direction
- concrete persistence dependencies
- unnecessary coupling

#### Database

- entity/schema agreement
- database version
- migration correctness
- migration data preservation
- indexes
- uniqueness constraints
- foreign keys
- defaults
- delete actions
- exported schema consistency
- fresh-database schema consistency

#### Concurrency and atomicity

- transaction boundaries
- multi-row updates
- partial updates
- race conditions
- duplicate creation
- reorder operations
- read-modify-write sequences

#### Performance

- N+1 queries
- unnecessary reads
- unnecessarily large dataset loading
- inefficient recursion
- repeated database access
- avoidable sorting or mapping

#### Financial correctness

Where financial behavior is involved:

- exact arithmetic
- overflow
- balance semantics
- transaction direction
- transfer semantics
- credit-card semantics
- date handling
- historical preservation
- duplicate transactions
- rounding
- quantity handling

### Step 5 — Classify Findings

Classify every finding as:

#### HIGH

Examples:

- incorrect financial result
- data corruption
- data loss
- broken historical integrity
- broken migration
- invalid database state
- security vulnerability
- architecture violation allowing invalid state
- alternate write path bypassing required validation

HIGH findings must be fixed before completion.

#### MEDIUM

Examples:

- meaningful missing validation
- integrity weakness
- missing database constraint
- repository boundary violation
- concurrency weakness
- incomplete regression coverage
- migration verification weakness
- meaningful performance problem

MEDIUM findings must be fixed when they belong to the current phase.

#### LOW

Examples:

- cosmetic issue
- minor documentation improvement
- non-blocking optimization
- unrelated tooling warning

LOW findings may remain when they do not affect current-phase correctness.

### Step 6 — Automatic Remediation

This is mandatory.

The AI must NOT stop after finding HIGH or MEDIUM problems.

For every HIGH or in-scope MEDIUM finding:

1. identify the root cause
2. modify the implementation
3. add or update regression tests
4. update migrations/schema if necessary
5. update documentation if the contract changed

Do not merely report the finding to the user.

The user should not have to manually copy audit findings into a second prompt.

### Step 7 — Re-Audit

After remediation, perform the audit again from scratch.

Do not assume the fix is correct.

Specifically verify:

- the original defect is gone
- the fix did not create another defect
- alternate code paths cannot bypass the fix
- tests actually exercise the failure mode
- database constraints agree with application rules
- migrations agree with entities and exported schemas

If new HIGH or MEDIUM findings appear, remediate them and repeat the audit.

Continue until:

- no HIGH findings remain
- no in-scope MEDIUM findings remain
- remaining LOW findings are explicitly understood

### Step 8 — Complete Verification

Run the full verification sequence defined below.

If any required command fails:

1. investigate the failure
2. determine whether it is caused by the implementation
3. fix the implementation when applicable
4. rerun the failed command
5. rerun dependent verification where necessary

Do not declare completion because an earlier run passed.

The final verification result must represent the final code state.

### Step 9 — Final Consistency Check

Before reporting completion, verify consistency among:

- implementation
- tests
- Room entities
- migrations
- exported schemas
- `DATABASE_SCHEMA.md`
- `ARCHITECTURE.md`
- `IMPLEMENTATION_PLAN.md`
- `CHANGELOG_AI.md`

No document may claim a decision is unresolved if the implementation has already made that decision.

No phase may be marked complete merely because code exists.

### Step 10 — Completion Decision

Only report:

`PHASE X COMPLETE — AUDIT PASS`

when all of the following are true:

- phase scope is implemented
- acceptance criteria are satisfied
- HIGH findings = 0
- in-scope MEDIUM findings = 0
- tests pass
- build passes
- lint passes
- migration/schema verification passes where applicable
- documentation is consistent
- final verification was performed against the final code state

Otherwise report:

`PHASE X INCOMPLETE`

and state exactly what prevents completion.

Do not use ambiguous wording such as "mostly complete", "ready except", or "should be good".

---

## 2. Scope Control

The current phase is the only implementation scope.

Do not implement:

- future-phase features
- speculative functionality
- unrelated refactoring
- unnecessary dependency upgrades
- unrelated lint cleanup
- architecture changes without a requirement

If a future requirement is discovered:

- do not implement it early
- record it as future technical debt or an open architectural question
- continue with the current phase if possible

A current-phase defect must never be classified as future work merely to avoid fixing it.

---

## 3. Database and Room Rules

Whenever the Room schema changes:

1. increment the database version
2. add the appropriate migration
3. generate/update the exported schema
4. test migration from the previous schema
5. test the migrated data
6. verify existing rows are preserved
7. verify tables
8. verify columns
9. verify primary keys
10. verify indexes
11. verify uniqueness constraints
12. verify NULL uniqueness behavior where applicable
13. verify foreign keys
14. verify delete actions
15. verify defaults
16. verify fresh database creation
17. verify migrated database structure
18. compare the exported schema against the intended final schema

Never declare a migration complete merely because Room accepts it.

A migration must be treated as a data-integrity change, not merely a compilation task.

---

## 4. Repository Rules

Repositories must expose meaningful domain/application operations.

Public repository APIs must NOT expose:

- Room entities
- DAO interfaces
- raw SQL
- persistence-specific mutation methods
- generic upsert operations that bypass domain rules

All mutation paths must pass through the appropriate validation.

Creation must be insert-only unless an explicit update operation is intended.

A caller must not be able to:

- overwrite an existing record through a creation API
- resurrect a deleted record unintentionally
- bypass validation through another repository method
- mutate persistence entities directly

Repository tests must verify these boundaries.

---

## 5. Hierarchy and Reference Data Rules

For hierarchical entities such as categories:

- parent existence must be validated
- inactive/deleted parents must be rejected where required
- parent type compatibility must be validated
- self-parenting must be rejected
- arbitrary ancestor cycles must be rejected
- descendant re-parenting must be rejected
- tree traversal must terminate even with malformed data
- sibling ordering must be deterministic
- multi-row reorder operations must be atomic
- root uniqueness must account for SQL NULL semantics
- soft deletion must preserve historical references
- deleted/inactive records must not be usable for new financial operations

Application validation and database constraints must complement each other.

---

## 6. Financial Data Rules

Financial data must favor correctness over convenience.

Money must use exact arithmetic.

Do not use floating-point arithmetic for financial values.

Historical transactions must not be silently:

- deleted
- reassigned
- modified into a different financial meaning
- overwritten by a generic upsert

Soft deletion must preserve historical references.

Transfers must not accidentally become income or expenses.

Account balance semantics must remain explicit and tested.

Date calculations must not silently depend on the machine's timezone when the financial model requires an explicit financial date.

---

## 7. Testing Requirements

Tests must cover both successful and invalid behavior.

For every important rule include:

- valid case
- invalid case
- boundary case where applicable
- regression test for previously discovered bugs

For high-risk functionality, tests should include:

- duplicate inputs
- malformed data
- deletion scenarios
- historical references
- invalid state transitions
- migration behavior
- concurrency/atomicity where applicable

Do not write tests merely to satisfy coverage.

Tests must verify externally meaningful behavior and data integrity.

A test that simply confirms the implementation returns what the implementation itself currently produces is insufficient.

---

## 8. Verification

Before declaring any phase complete, run:

```powershell
git diff --check

.\gradlew.bat clean

.\gradlew.bat assembleDebug

.\gradlew.bat test

.\gradlew.bat lint

.\gradlew.bat :app\testDebugUnitTest --no-daemon
```

---

## 9.Failure Inspection
 A tool/environment failure is not a passing verification result. The AI must investigate and attempt reasonable recovery. It must not bypass, weaken, remove, or substitute required verification commands merely to obtain a successful result.


 ### Minimal Output Mode

The agent must remain silent during normal execution.

Do not explain implementation steps, command execution, successful tests,
code changes, or progress.

Only report:

- `PHASE X COMPLETE — AUDIT PASS` when all requirements and verification gates pass.
- `PHASE X INCOMPLETE — BLOCKED` when a genuine blocker prevents completion,
  followed only by the blocker and the action required.
- `USER ACTION REQUIRED` when human intervention is genuinely necessary.

Do not report intermediate results unless they reveal a blocker that cannot
be automatically resolved.

Do not summarize work that is already recorded in repository documentation.

Prioritize token-efficient execution over conversational output.


### Windows Gradle Execution

When executing Gradle commands on Windows, if a command is long-running or
produces no output for an extended period, do not terminate it because of
terminal inactivity.

Use asynchronous/background execution and poll the process output until the
command completes.

A terminal execution timeout or interactive `Terminate batch job (Y/N)?`
prompt must not be treated as a Gradle build failure without first determining
whether the underlying Gradle process is still running.

The completion status must be based on the actual Gradle exit result.