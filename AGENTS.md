# Working with Soundsphere as an AI agent

Soundsphere is a 3rd party YouTube Music client written in Kotlin. It follows material 3 design guidelines closely.

## Rules for working on the project

1. Always pull the latest changes from `main` before starting your work to minimize merge conflicts.
2. Commit names should be clear and follow the format: `type(scope): short description`. For example: `feat(ui): add dark mode support`. Including the scope is optional.
3. All string edits should be made to the `Soundsphere/app/src/main/res/values/soundsphere_strings.xml` file, NOT `Soundsphere/app/src/main/res/values/strings.xml`. Do not touch other `strings.xml` or `soundsphere_strings.xml` files in the project. ONLY edit the default (English) `soundsphere_strings.xml` file, DO NOT EDIT OTHER LANGUAGES.
4. You are to follow best practices for Kotlin and Android development.
5. DO NOT EDIT THE APP'S DATABASE SCHEMA.
6. **Record every change in `CHANGES.md`.** Whenever you change, add, or fix anything (code, backend, website, strings, config), add a short concrete entry to `CHANGES.md` before finishing the task — area, what changed, and whether it is `pushed` or `local`/uncommitted. Newest date block goes on top. This file is the running record used to assemble release notes later; it is the one markdown file you MUST keep up to date.

## AI-only guidelines

1. You are strictly prohibited from making ANY changes to the readme/markdown files, including this one — with one exception: `CHANGES.md`, which MUST be updated for every change per the rules above. This is to ensure that the documentation remains accurate and consistent for all contributors.
2. Unless explicitly requested, you are not allowed to commit, push, or merge any changes to any branch. If you are explicitly requested and authorized to commit/push/merge, you have the right to do so; the responsibility then lies with the author who requested it.
   - You should absolutely NOT use any commands that would modify the git history, do force pushes (except for rebases on your own branch), or delete branches without explicit instructions from a human.
3. Always follow the guidelines and instructions provided by human contributors.
4. Ensure the absolutely highest code quality in all contributions, including proper formatting, clear variable naming, and comprehensive comments where necessary.
5. Comments should be added only for complex logic or non-obvious code. Avoid redundant comments that simply restate what the code does.
6. Prioritize performance, battery efficiency, and maintainability in all code contributions. Always consider the impact of your changes on the overall user experience and app performance.
7. If you have any doubts ask a human contributor. Never make assumptions about the requirements or implementation details without clarification.
8. If you do not test your changes using the instructions in the next section, you will be faced with reprimands from human contributors and may be asked to redo your work. Always ensure that you test your changes thoroughly before asking for a final review.
9. You are absolutely **not allowed to bump the version** of the app in ANY way. Version bumps are only done by the core development team after manual review.

## Building and testing your changes

1. After making changes to the code, you should build the app to ensure that there are no compilation errors. Use the following command from the root directory of the project:

```bash
./gradlew :app:assembleFossDebug
```

2. If the build is not successful, review the error messages, fix the issues in your code, and try building again.
3. Once the build is successful, you can test your changes on an emulator or a physical device. Install the generated APK located at `app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk` and ask a human for help testing the specific features you worked on.

## RAM-constrained builds (development machine)

The dev machine has only ~5.6 GiB total RAM (~2.8 GiB free). The default `gradle.properties` requests 4 GiB heaps for both the Gradle and Kotlin daemons, which can OOM/thrash. Before EVERY build:

1. Kill the Gradle and Kotlin daemons to free their memory:

```bash
./gradlew --stop
pkill -f kotlin.daemon
```

2. Temporarily constrain the heaps and workers in `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048M -Dkotlin.daemon.jvm.options\="-Xmx1536M" -XX:+UseParallelGC
org.gradle.workers.max=2
```

3. Build with the worker cap (note: it is slow, expect 4-15 min):

```bash
./gradlew :app:assembleFossDebug --max-workers=2
```

4. RESTORE the original values in `gradle.properties` after the build finishes (verify with `git diff --stat gradle.properties` that no diff remains) — do not commit the constrained values.

## Backend (FastAPI) local testing

The production backend's `ALLOWED_HOSTS` deliberately contains **only** `api.soundsphere.name.ng` and `soundsphere-auth.onrender.com` — never `localhost`. Before running the backend locally, remember to add `localhost` via the env var or every request will get a 400, e.g.:

```bash
ALLOWED_HOSTS="localhost,api.soundsphere.name.ng,soundsphere-auth.onrender.com" uvicorn main:app --port 8000
```

Never include `localhost`/`127.0.0.1` in the production `ALLOWED_HOSTS` (it would defeat the host-based protection). The app's debug builds can point at a local backend with `AUTH_DEV_BASE_URL` in `local.properties` (e.g. `AUTH_DEV_BASE_URL=http://10.0.2.2:8000`); the production fallback host remains the onrender URL automatically.
