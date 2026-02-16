# Contributing

## Requirements
- Android Studio (latest stable)
- JDK 21
- Android SDK for `compileSdk` in `app/build.gradle.kts`

## Development Workflow
1. Create a feature branch.
2. Implement changes with tests where possible.
3. Run local checks:
   - `./gradlew --no-daemon :app:lintDebug`
   - `./gradlew --no-daemon :app:testDebugUnitTest`
   - `./gradlew --no-daemon :app:assembleRelease`
4. Open a pull request using the template.

## Release Build Notes
- Release signing is optional by default and enabled only when all of the following are provided:
  - `PAPERWISE_RELEASE_STORE_FILE`
  - `PAPERWISE_RELEASE_STORE_PASSWORD`
  - `PAPERWISE_RELEASE_KEY_ALIAS`
  - `PAPERWISE_RELEASE_KEY_PASSWORD`
- Provide values via environment variables or Gradle properties (`-P...`).
- Never commit keystores or signing secrets.

## Code Standards
- Keep packages under `com.paperwise`.
- Avoid introducing blocking work on the main thread.
- Keep UI state in ViewModels and expose immutable flows.
- Prefer small, testable utility classes for transformation logic.
