# Operating Rules

## Token Efficiency
- Minimize context and token usage.
- Be concise. Do not explain unless necessary.
- Do not repeat the user's request.
- Do not provide summaries unless requested or useful.
- Read only files relevant to the task.
- Prefer targeted search over broad repository scans.
- Never reread unchanged files unnecessarily.
- Do not inspect generated files, dependencies, build output, caches, or binaries unless required.
- Avoid unrelated refactoring.
- Make the smallest correct change.

## Exploration
- Delegate substantial repository exploration to the Explore subagent.
- Use subagents when their isolated context prevents large search results, logs, or file contents from entering the main context.
- Give subagents narrowly defined tasks.
- Require concise, actionable results.
- Do not ask subagents for explanations, code dumps, or full file contents.
- Do not create multiple subagents for small or dependent tasks.

## Implementation
- Identify relevant files before editing.
- Reuse existing architecture and utilities.
- Do not introduce dependencies unless necessary.
- Do not change unrelated code.
- Do not add comments for obvious code.
- Do not create documentation unless required.

## Testing
- Batch related changes before testing.
- Prefer targeted tests over the complete test suite.
- Use TestRunner for verbose test execution or log analysis when useful.
- Do not repeatedly rerun the same failing command without changing something.
- Filter large command output to relevant errors.

## Completion
Before declaring success:
- Verify the changed behavior.
- Run the minimum sufficient checks.
- Report failures honestly.

### Final Response
- 1–5 bullets.
- State only changes, verification, and important issues.