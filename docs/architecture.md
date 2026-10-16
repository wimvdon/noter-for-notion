# Architecture

## This app

Noter shows the pages of Notion databases as notes, in the style of Google Keep, and edits their
title, properties and content.

### Screens
- **Overview** (`ui/main/`): Material's navigation bar with Pinned and one destination per added
  database (a navigation drawer instead when there are more than five). Notes are cards in a
  staggered grid with a preview of their content; pinned notes come first under "Pinned".
  Long-press and drag moves a note within its section; a long press without moving opens its menu
  (pin, move to trash). Filters (Material filter chips) come from one select, status, multi-select
  or person property per database. Pinned shows the pinned notes of every database.
- **Note** (`ui/note/`): title, editable properties (`PropertyEditors.kt`), then the content as a
  list of blocks, each its own text field. To-do items have an × to delete them. A toolbar above the keyboard changes the block's kind,
  adds a divider or deletes the block. Tapping below the content continues at the end. Moving a
  note to the trash asks first.
- **Settings** (`ui/settings/`): Connection (API key, sync status), Databases (add, edit, order),
  Appearance; built from the shared `SettingsKit.kt`.
- `ui/AppNavigation.kt`: a small back stack of three screens (no navigation library).

### Data flow
```
Notion API ── data/api/NotionClient (OkHttp, Notion-Version 2022-06-28, retries 429/5xx)
     │
data/NotesRepository ── single source for the screens
     │   reads:  on-phone copy first (data/store/CacheStore), then refresh (data/sync/SyncPolicy)
     │   writes: on-phone copy at once, queued in data/sync/Outbox, sent in order
     │   content: sent as a diff of blocks (data/sync/ContentDiff), never by rewriting the note
     ▼
ViewModels (ui/*/…ViewModel) ── StateFlow<UiState> ── Composables
```

- **Blocks** (`data/notion/BlockCodec.kt`): paragraphs, headings (also toggle headings), bulleted,
  numbered and to-do lists, toggles, quotes, callouts, code and dividers are editable. Other blocks
  (images, tables, embeds, …) are shown as a read-only box and left untouched. Nested blocks
  (inside a toggle, a list item, …) are not shown; a toggle, callout or quote with nested content
  says so. A block with nested content can't change its kind, because Notion can't change a
  block's kind and recreating it would lose what is nested.
- **Inline styles** (`data/notion/InlineMarkdown.kt`): bold, italic, strikethrough, inline code and
  links are kept as Markdown in the editor and turned back into Notion rich text. Code blocks keep
  their text literally.
- **Editing rules** (`logic/DocumentEditor.kt`): Enter, Backspace at the start of a line and the
  Markdown shortcuts, following Notion's own editor.
- **Saving** (`data/sync/SaveTiming.kt`): an edit reaches the on-phone copy after a short pause in
  typing (at most 1 s later), and the queue goes to Notion after a pause (at most 1 s after the
  first unsent change). While typing, that is about one round of requests per second, one per
  changed block, under Notion's limit of about three requests per second; `NotionClient` waits when
  Notion answers 429. Leaving a note sends at once.
- **Previews** (`logic/NotePreviews.kt`): the overview shows the start of each note's content from
  the on-phone copy. Copies that are missing or older than the note's last edit in Notion are
  downloaded one note at a time (at most 24 per round).
- **Order and pins** are only on this phone (`AppSettings.order`, `AppSettings.pinned`); Notion is
  not changed.

### Where data lives
| What | Where | Backed up |
|---|---|---|
| Databases, pins, order, theme | DataStore `backed_up_settings` | Yes |
| API key | `EncryptedSharedPreferences` `notion_key_encrypted_prefs` | No |
| Copies of notes, schemas, users; unsent changes | JSON files in `noBackupFilesDir/notion_cache` | No |

## Structure
```
app/src/main/kotlin/com/impca201/noter/
  MainActivity.kt, NoterApp.kt   Entry point; the app object creates the repository.
  data/api/                      NotionClient: every HTTP call.
  data/model/                    Notes, blocks, properties, schemas.
  data/notion/                   Notion JSON ⇄ models (blocks, properties, rich text).
  data/store/                    Settings (DataStore), API key, on-phone copies.
  data/sync/                     Outbox, ContentDiff, SyncPolicy, SaveTiming.
  data/NotesRepository.kt        The single source of notes for the screens.
  logic/                         Plain Kotlin rules: editor, ordering, chips, previews, dates.
  ui/main/, ui/note/, ui/settings/, ui/common/, ui/theme/   Screens and the shared design kit.
app/src/test/kotlin/             JVM unit tests (mirrors the main package structure).
scripts/                         Release helpers (Python/Java) with their own tests.
.github/workflows/               ci.yml (every PR), release.yml (signed releases).
```

## Conventions
- **Single `app` module.** Split into modules only if the app grows large; record it in `decisions.md`.
- **Logic in plain Kotlin classes** with no `Context`, `View` or Compose types, so it can be
  tested on the JVM. Example: `logic/DocumentEditor.kt`.
- **UI state**: small screens use `rememberSaveable`. When a screen gets real logic or data,
  add a `ViewModel` (`androidx.lifecycle:lifecycle-viewmodel-compose`) that exposes a
  `StateFlow<UiState>` and calls into the plain-Kotlin logic. Record it in `decisions.md`.
- **Navigation**: three screens on a small back stack in `ui/AppNavigation.kt` (see `decisions.md`).
- **Persistence**: Jetpack DataStore (version already in the catalog). Wrap it behind a small
  repository interface so logic tests can use a fake. Decide per value whether it is backed up
  (see "Backup" below).
- **Network: only Notion's API**, only in `data/api/NotionClient.kt`. No analytics, crash
  reporting or ads (see `CLAUDE.md` and `PRIVACY.md`).
- **Strings** in resources only; plurals via `<plurals>`.
- **Versions** of libraries only in `gradle/libs.versions.toml`.
- **App identity** (`applicationId`/`namespace`) only in `app/build.gradle.kts` (`appId`).

## Backup
Android backs up app data to the user's Google account (if they turned backup on) and copies it
to a new phone during set-up. The app decides which files take part, in
`app/src/main/res/xml/data_extraction_rules.xml` (`cloud-backup` = Google account,
`device-transfer` = new phone). The rule is an **allow list**: only listed files are copied, so a
new file is never backed up by accident.

- **Backed up:** settings the user chose and would want back after a reset or on a new phone
  (theme, layout, toggles, names). Keep them in one DataStore named `backed_up_settings`
  (`preferencesDataStore(name = "backed_up_settings")`), which the rules already list.
- **Not backed up:** sign-in tokens, passwords and API keys (security, and keys encrypted with the
  Android Keystore can't be read on another phone anyway); state that only means something on this
  phone (a running job, notification IDs, "switched on right now"); settings that only work after
  a permission or set-up on this phone; copies of data an online service can download again; caches
  (Android never backs up `cacheDir` and `noBackupFilesDir`). Keep these in another DataStore file.
- Android rules work per **file**, not per key. Moving a value from one file to another in an app
  that is already released needs a `DataMigration` (see Snoozer's `MoveKeysMigration`).
- `BackupRulesTest` checks both sections list exactly the settings file. Change the test together
  with the rules, and update `PRIVACY.md` when what is backed up changes.
- **minSdk below 31:** Android 8-11 ignore `data_extraction_rules.xml` and read
  `android:fullBackupContent` instead. Add `res/xml/backup_rules.xml` with the same `include` lines,
  point `fullBackupContent` at it in the manifest, and let `BackupRulesTest` check it too.
- To test on a phone: change a setting, then open Settings, search for "Backup" and tap **Back up now**. Uninstall the app and install the same APK again: the
  setting comes back. Restore only works for an APK signed with the same key.
