# Android Rule Specification

## File Paths
- `**/*.kt`
- `**/*.kts`
- `**/AndroidManifest.xml`
- `**/build.gradle`
- `**/build.gradle.kts`

## Android Rules
- Follow the existing Android architecture.
- Do not introduce new libraries unless necessary.
- Prefer existing project utilities and patterns.
- Keep changes narrowly scoped.
- Do not modify Gradle configuration unless required.
- Do not perform full builds after every small change.
- Prefer targeted unit tests and module checks.
- Do not inspect generated Android/build files unless required.