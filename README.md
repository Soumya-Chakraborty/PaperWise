<h1 align="center">PaperWise</h1>
<p align="center"><strong>Offline-First Android PDF Reader</strong></p>
<p align="center">Built with Kotlin, Jetpack Compose, Room, Hilt, and MuPDF.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Language: Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="UI: Jetpack Compose" />
  <img src="https://img.shields.io/badge/DI-Hilt-4285F4" alt="DI: Hilt" />
  <img src="https://img.shields.io/badge/Database-Room-5C6BC0" alt="Database: Room" />
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Min%20SDK-26-0A7EA4" alt="Min SDK 26" />
  <img src="https://img.shields.io/badge/Target%20SDK-36-0A7EA4" alt="Target SDK 36" />
  <img src="https://img.shields.io/badge/Build-Gradle-02303A?logo=gradle&logoColor=white" alt="Build: Gradle" />
  <img src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white" alt="CI: GitHub Actions" />
  <img src="https://img.shields.io/badge/Security-Policy%20Present-2E7D32" alt="Security policy" />
  <img src="https://img.shields.io/badge/Dependencies-Dependabot-025E8C?logo=dependabot&logoColor=white" alt="Dependabot" />
</p>

---

## Why PaperWise
- Offline-first architecture for reliable reading without network dependency.
- Modern Android stack with Compose UI and lifecycle-aware Kotlin coroutines.
- Production-focused repo setup with strict lint/test/release quality gates.
- Secure baseline posture with policy docs and dependency update automation.

## Production Readiness
| Area | Baseline |
|---|---|
| Build | Release minification + ProGuard (`isMinifyEnabled = true`) |
| Quality | Lint as error + unit test gate in CI |
| Delivery | Release APK assembly in CI with artifact upload |
| Security | `SECURITY.md`, Dependabot updates, clear secret-handling guidance |
| Collaboration | PR template + contributor workflow |

## Tech Stack
- Kotlin, Coroutines, Flow
- Jetpack Compose + Material 3
- Room (local persistence)
- Hilt (dependency injection)
- MuPDF (PDF rendering engine)

## Quick Start
### Prerequisites
- JDK 21
- Android Studio (latest stable)
- Android SDK matching `compileSdk` in `app/build.gradle.kts`

### Build Debug
```bash
./gradlew :app:assembleDebug
```

## Quality Gates
Run all checks before opening a PR:

```bash
./gradlew --no-daemon :app:lintDebug
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleRelease
```

## Release Signing (Optional)
Release signing is enabled only when all values are provided via environment variables or Gradle properties:

- `PAPERWISE_RELEASE_STORE_FILE`
- `PAPERWISE_RELEASE_STORE_PASSWORD`
- `PAPERWISE_RELEASE_KEY_ALIAS`
- `PAPERWISE_RELEASE_KEY_PASSWORD`

Example:

```bash
export PAPERWISE_RELEASE_STORE_FILE=/path/to/release.jks
export PAPERWISE_RELEASE_STORE_PASSWORD=***
export PAPERWISE_RELEASE_KEY_ALIAS=paperwise
export PAPERWISE_RELEASE_KEY_PASSWORD=***
./gradlew --no-daemon :app:assembleRelease
```

## CI/CD
Workflow: `.github/workflows/android-ci.yml`

Pipeline stages:
1. Validate (`lintDebug` + `testDebugUnitTest`)
2. Release build (`assembleRelease`)
3. Artifact upload (lint/test reports + APK + mapping files)

## Repository Standards
- Contribution guide: `CONTRIBUTING.md`
- Security reporting: `SECURITY.md`
- Dependency update automation: `.github/dependabot.yml`

## License Note
MuPDF is dual-licensed (AGPL/commercial). Validate your distribution model for compliance before release.
