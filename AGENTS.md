# Repository Guidelines

## Project Structure & Module Organization
- `app/`: main Android application module (flavors: `full`, `pumpcontrol`, `aapsclient`, `aapsclient2`).
- `core/`, `plugins/`, `pump/`, `database/`, `shared/`, `ui/`, `workflow/`: feature and shared libraries.
- `wear/`: Wear OS companion app; `benchmark/`: Android benchmark module.
- Build logic lives in `build.gradle.kts`, `settings.gradle`, and `buildSrc/`.
- Tests are split by module in `*/src/test` and `*/src/androidTest`, with shared helpers in `shared/tests`.

## Build, Test, and Development Commands
- `./gradlew assembleFullDebug`: build the default full debug APK.
- `./gradlew :app:assemblePumpcontrolDebug`: example of building a specific flavor.
- `./gradlew testFullDebugUnitTest`: run unit tests for the full debug variant.
- `./runtests.sh` or `runtests.bat`: CI-aligned unit tests with coverage flags.
- `./gradlew ktlintCheck` / `./gradlew ktlintFormat`: lint/format Kotlin code via ktlint.

## Coding Style & Naming Conventions
- Use Android Studio defaults: 4-space indentation, spaces (see `CONTRIBUTING.md`).
- Run auto-format before committing (Android Studio: `Ctrl+Alt+L` / `Cmd+Alt+L`).
- Kotlin/Gradle formatting follows ktlint with repo rules in `.editorconfig`.
- Prefer string resources (`strings.xml`) over hardcoded text; only add English strings (Crowdin handles translations).

## Testing Guidelines
- Unit tests: `*/src/test` (JUnit-based). Instrumentation tests: `*/src/androidTest` (AndroidX runner).
- Shared test utilities live in `shared/tests`; reuse them instead of duplicating fixtures.
- Name tests to match the class/feature they cover and keep variants explicit (e.g., `*Test.kt`).

## Commit & Pull Request Guidelines
- Recent history shows short, scoped subjects (e.g., `Wear: …`) and occasional conventional `fix:` prefixes.
- Keep commit subjects concise and action-oriented; add scope prefixes when useful.
- Follow `CONTRIBUTING.md`: fork the repo, branch from `dev`, rebase on `dev`, then open a PR to the main repo.
- Include a clear PR description and link to relevant issues; small, focused PRs are preferred.

## Configuration & Local Setup
- Ensure Android SDK paths are configured via `local.properties` (not committed).
- Firebase configs are already present in `app/google-services.json` and `wear/google-services.json`.
