# Fix Kotlin Compose Plugin Sync Error

The project is currently configured with Kotlin `1.9.24` but attempts to use the `org.jetbrains.kotlin.plugin.compose` plugin, which was only introduced in Kotlin `2.0.0`. This causes the sync error because the plugin artifact cannot be found for the specified Kotlin version.

## Proposed Changes

I will upgrade the project to Kotlin `2.0.20` to enable the new Compose Compiler Gradle plugin, which is the recommended approach for modern Android development. This also simplifies dependency management by removing the need for matching manual Compose Compiler versions.

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///D:/Android/financial_app/gradle/libs.versions.toml)
- Upgrade `kotlin` to `2.0.20`.
- Upgrade `ksp` to `2.0.20-1.0.25`.
- Remove the `composeCompiler` version as it's no longer required with the Kotlin Compose plugin.

## Verification Plan

### Automated Tests
- Run `./gradlew build` to ensure the project compiles and syncs correctly.
- Run `gradle_sync` via the IDE tools.

### Manual Verification
- Verify that the Gradle sync finishes without the "plugin not found" error.
