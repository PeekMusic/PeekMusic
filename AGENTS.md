# Working with PeekMusic as an AI agent

PeekMusic is a performance-focused fork of Metrolist, a 3rd party YouTube Music client written in Kotlin. It follows material 3 design guidelines closely. Repository: `PeekMusic/PeekMusic` on GitHub (upstream: `MetrolistGroup/Metrolist`).

## Rules for working on the project

1. Always pull the latest changes from `main` before starting your work to minimize merge conflicts.
2. Commit names should be clear and follow the format: `type(scope): short description`. For example: `feat(ui): add dark mode support`. Including the scope is optional.
3. All string edits should be made to the `app/src/main/res/values/metrolist_strings.xml` file, NOT `app/src/main/res/values/strings.xml`. Do not touch other `strings.xml` or `metrolist_strings.xml` files in the project. ONLY edit the default (English) `metrolist_strings.xml` file, DO NOT EDIT OTHER LANGUAGES (one-time explicit permission by the maintainer overrides this).
4. You are to follow best practices for Kotlin and Android development.
5. DO NOT EDIT THE APP'S DATABASE SCHEMA.

## AI-only guidelines

1. Changes to markdown files are allowed only for fork-owned documentation: `README.md`, `changelog.md`, `docs/`, `AGENTS.md` (when explicitly requested), and `fastlane/metadata/**.txt`. All other readme/markdown files (e.g. upstream guides) must not be edited, so upstream documentation stays accurate for merges.
2. Unless explicitly requested, you are not allowed to commit, push, or merge any changes to any branch. If you are explicitly requested and authorized to commit/push/merge, you have the right to do so; the responsibility then lies with the author who requested it.
   - You should absolutely NOT use any commands that would modify the git history, do force pushes (except for rebases on your own branch), or delete branches without explicit instructions from a human.
3. Always follow the guidelines and instructions provided by human contributors.
4. Ensure the absolutely highest code quality in all contributions, including proper formatting, clear variable naming, and comprehensive comments where necessary.
5. Comments should be added only for complex logic or non-obvious code. Avoid redundant comments that simply restate what the code does.
6. Prioritize performance, battery efficiency, and maintainability in all code contributions. Always consider the impact of your changes on the overall user experience and app performance.
7. If you have any doubts ask a human contributor. Never make assumptions about the requirements or implementation details without clarification.
8. If you do not test your changes using the instructions in the next section, you will be faced with reprimands from human contributors and may be asked to redo your work. Always ensure that you test your changes thoroughly before asking for a final review.
9. **Version bumps only on explicit request.** The version lives in `app/build.gradle.kts` (`peekVersionName` and `versionCode`) and may only be changed when the maintainer explicitly asks for a release. Never bump it on your own initiative.

## Fork-specific notes

- The in-app updater checks this fork's GitHub releases (`PeekMusic/PeekMusic`); release APK assets must be named `PeekMusic-<version>-fossRelease.apk` (universal, foss variant).
- Update-badge trap (burned us in v0.5.0): the badge compares the app's version against the release **tag** (minus the `v` prefix), never against the GitHub release **title** (`ReleaseInfo.versionName`, e.g. "PeekMusic 0.5.0" ≠ "0.5.0" — a title-based comparison badges permanently). After merging upstream changes to `MainActivity.kt`'s update check, re-verify that `latestVersionName` is only set when `hasUpdate` and from `tagName.removePrefix("v")`. Also re-check `Updater.parseAssets` still matches our `PeekMusic-<version>-fossRelease.apk` naming after upstream updater refactors.
- `gh` CLI may resolve the upstream repo by default — pass `--repo PeekMusic/PeekMusic` for release operations.
- `tap-notes.md` (repo root) contains adb tap coordinates for device testing. It is gitignored on purpose; extend it whenever new screen positions were measured.

## Building and testing your changes

1. After making changes to the code, you should build the app to ensure that there are no compilation errors. Use the following command from the root directory of the project:

```bash
./gradlew :app:assembleFossDebug
```

2. If the build is not successful, review the error messages, fix the issues in your code, and try building again.
3. Once the build is successful, you can test your changes on an emulator or a physical device. Install the generated APK located at `app/build/outputs/apk/foss/debug/PeekMusic-<version>-fossDebug.apk` and ask a human for help testing the specific features you worked on.
4. Release builds (signed with the private release keystore at `app/keystore/release.keystore`, gitignored):

The signing credentials come from `keystore.properties` in the repo root (gitignored, format: `storePassword=…`, `keyAlias=…`, `keyPassword=…`) or, as a fallback, from the environment variables `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`:

```bash
./gradlew :app:assembleFossRelease
```

The signed APK lands at `app/build/outputs/apk/foss/release/PeekMusic-<version>-fossRelease.apk`. NEVER commit the keystore or its passwords — the signing key is what makes updates trustworthy.
