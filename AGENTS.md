# AGENTS.md

Instructions for AI coding agents working in this repository.

## Working a Kanban task: one chat per task

This project uses the OpenChamber Kanban board to queue work. When you take a
task from the board:

1. **Claim it first.** Claim the task before you start, so two agents never
   work the same item.
2. **Create one chat per claimed task.** Do not do several board tasks in one
   long chat, and do not continue an unrelated previous conversation for a new
   task. Each task gets its own session.
3. **Name that chat in English, in a few words.** The title must be English,
   regardless of the language the user or the card is written in. Keep it short
   (roughly 2–5 words), specific to the task, and in Title case without trailing
   punctuation.

Examples

| Card / request language | Bad chat name | Good chat name |
| --- | --- | --- |
| `Клонування репозиторію jelu` (Ukrainian) | `Клонування репозиторію jelu` | `Clone jelu repository` |
| Shelfmark returns 502 | `fix bug` | `Fix Shelfmark 502 error` |
| Add test connection button | `Add a button to the preferences page that tests the shelfmark connection` | `Add Shelfmark connection test` |

A good name says what the task does, not how the session went. Avoid generic
titles such as "Fixes", "Working on task", "Bug" or a bare card number.

## Finishing a task

- Move the task to **Review** when the work is ready to look at, and to **Done**
  only when it is verified complete.
- Marking a task Done may be gated by the project's board settings; if it is,
  ask the user rather than retrying.

## Repository conventions

- **Build**: `./gradlew copyWebDist assemble` produces `build/libs/jelu-*.jar`;
  the `Dockerfile` is multi-stage and builds everything from source.
- **Tests**: `./gradlew test`. Tests use MockK (`io.mockk`), not Mockito.
- **Formatting**: ktlint runs in the build and in the `pre-commit` hook. Run
  `./gradlew ktlintFormat` before committing; `./gradlew ktlintCheck` must pass.
- **Commits**: conventional commit messages, enforced by commitlint
  (`feat:`, `fix:`, `style:`, `refactor:`, `chore:` ...).
- **Database**: Exposed (`dao/`), with schema changes in
  `src/main/resources/liquibase.xml`. Never change a table without a migration.
- **Tests against SQLite**: `src/test/resources/application-test.yml` uses a
  shared-cache in-memory database. Keep the pinned single connection; a plain
  `jdbc:sqlite::memory:` URL gives every pooled connection its own empty
  database and breaks test isolation.
