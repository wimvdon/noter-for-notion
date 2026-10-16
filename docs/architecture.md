# Architecture

## This app

Noter shows Notion pages as notes, in the style of Google Keep, and edits their title,
properties and content: the pages of databases and pages on their own.

### Screens
- **Overview** (`ui/main/`): top-level destinations (`logic/Destinations.kt`). All of them are in
  the navigation drawer behind the menu button (it opens only from the button; a swipe from the
  edge stays Android's back gesture), together with Manage views and Settings. The navigation bar
  at the bottom holds Pinned, Browse and the views pinned to it, as many as fit: one item per
  80dp of window width (five on a phone).
  - **Pinned**: the pinned notes of everything (databases and pages), filtered by source.
  - **Browse** (`BrowsePane.kt`): the databases and pages shared with the integration, from
    Notion's search, most recently edited first, with a search field. Without a search a
    database's own pages are left out (they are one tap further); a search finds them too.
  - **Views**: a database in the navigation with its filter property and the filters that are on
    when it opens (`DatabaseConfig`, kept under `AppSettings.databases`; views saved before views
    existed have the database's ID as their own).
  Notes are cards in a staggered grid with a preview of their content; pinned notes come first
  under "Pinned". Long-press and drag moves a note within its section; a long press without
  moving opens its menu (pin, move to trash). Filters (Material filter chips) come from one
  select, status, multi-select or person property.
- **Database** (`DatabaseScreen.kt`): a database opened from Browse or a link in a note, as a
  child page with its notes and filters; "Add to navigation" makes it a view.
- **Note** (`ui/note/`): header image, icon, title, properties (`PropertyEditors.kt`), then the content as a
  list of blocks, each its own text field. To-do items have an × to delete them. A toolbar above the keyboard changes the block's kind,
  adds a divider or deletes the block. Tapping below the content continues at the end. Moving a
  note to the trash asks first.
- **Properties per database** (`logic/PropertyLayouts.kt`, `PropertyLayoutSheet.kt`): the first
  three are shown and the rest collapsed under "N more properties", until the user chooses
  otherwise with the tune button: drag to reorder, the eye to show or collapse, the card icon to
  show up to three on the note's card in the overview (else the card shows the filter property).
  Kept per database in `AppSettings.propertyLayouts` (backed up).
- **Manage views** (`ui/views/`): the views in their order (up/down), each with a pin for the
  navigation bar (`DatabaseConfig.inBar`; disabled when the bar is full); tapping one edits its
  name, icon, bar pin, filter property and filters; "Add view" picks a database. Opens from the
  drawer and from Settings.
- **Settings** (`ui/settings/`): Connection (API key, sync status), Views (a link to Manage
  views), Appearance; built from the shared `SettingsKit.kt`.
- **New note** on Pinned and Browse asks which database (`logic/NoteTargets.kt`): the views'
  databases, then (on Browse) the other databases listed there. A note made on Pinned is pinned.
- **Empty lists** show a large icon, a short text and, where it helps, a button (Show all, Retry).
- `ui/AppNavigation.kt`: a small back stack of five screens (no navigation library).
- **Pages on their own** (not in a database) are kept like a database's notes under the key
  `NotesRepository.PAGES`, so editing, pinning and the trash work the same. Linked pages and
  databases in a note (child pages, links to pages) open in the app.

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
  are shown read-only and left untouched (`ui/note/ReadOnlyBlocks.kt`): images, tables (their rows
  cost one more request per table), equations, files, videos, PDFs, audio, bookmarks, embeds,
  linked pages and the table of contents; tapping one opens it in the browser or in Notion.
  Anything else is a box with its kind and "(edit in Notion)". Nested blocks
  (inside a toggle, a list item, …) are not shown; a toggle, callout or quote with nested content
  says so. A block with nested content can't change its kind, because Notion can't change a
  block's kind and recreating it would lose what is nested.
- **Inline styles** (`data/notion/InlineMarkdown.kt`): bold, italic, strikethrough, inline code and
  links are kept as Markdown in the editor and turned back into Notion rich text. Code blocks keep
  their text literally.
- **Editing rules** (`logic/DocumentEditor.kt`): Enter, Backspace at the start of a line and the
  Markdown shortcuts, following Notion's own editor.
- **Never twice** (`data/sync/BlockMatch.kt`): the editor keeps its own copy of the blocks and
  doesn't learn the IDs Notion gives new blocks, so the repository keeps them by the block's key
  when the editor saves again (before, a new to-do was deleted and added again at each edit).
  Requests that add something (new blocks, new pages) are only retried by `NotionClient` when
  Notion says "too many requests". When adding blocks gets no clear answer (timeout, server
  error), the note is marked `uncertain`: before it is sent again, its content is downloaded and
  each block without an ID takes the ID of a matching block Notion added, so it isn't added
  twice. Notion's answer to adding blocks may also list blocks that were already there; new
  blocks are the ones whose IDs the phone doesn't know.
- **Offline**: unsent changes are retried after 15 s, then waiting longer each time (at most a
  minute without a connection, five minutes after server errors), and at once when the app comes
  to the front or the sync button is tapped. The app doesn't sync while it is closed.
- **Sync button** (`ui/common/SyncButton.kt`): in a note's app bar, and in the overview's while
  changes wait or the phone is offline. It sends what is waiting and gets the latest version.
- **Saving** (`data/sync/SaveTiming.kt`): an edit reaches the on-phone copy after a short pause in
  typing (at most 1 s later), and the queue goes to Notion after a pause (at most 1 s after the
  first unsent change). While typing, that is about one round of requests per second, one per
  changed block, under Notion's limit of about three requests per second; `NotionClient` waits when
  Notion answers 429. Leaving a note sends at once.
- **Images** (`ui/common/NotionImage.kt`, Coil): the page's header image above the title and
  image blocks, downscaled to the size they are shown at and cached in memory and in
  `cacheDir/images` (100 MB). Links to files Notion hosts work for about an hour: content with an
  expired link is downloaded again when the note opens (`NotionFiles`), and images are cached
  under the link without its signature, so a fresh link doesn't download the image again.
- **Previews** (`logic/NotePreviews.kt`): the overview shows the start of each note's content from
  the on-phone copy. Copies that are missing or older than the note's last edit in Notion are
  downloaded one note at a time (at most 24 per round).
- **Order and pins** are only on this phone (`AppSettings.order`, `AppSettings.pinned`); Notion is
  not changed.

### Where data lives
| What | Where | Backed up |
|---|---|---|
| Views, pins, order, property layouts, theme | DataStore `backed_up_settings` | Yes |
| API key | `EncryptedSharedPreferences` `notion_key_encrypted_prefs` | No |
| Copies of notes, schemas, users; unsent changes | JSON files in `noBackupFilesDir/notion_cache` | No |
| Images | Coil's cache in `cacheDir/images` | No |

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
- **Navigation**: four screens on a small back stack in `ui/AppNavigation.kt` (see `decisions.md`).
- **Persistence**: Jetpack DataStore (version already in the catalog). Wrap it behind a small
  repository interface so logic tests can use a fake. Decide per value whether it is backed up
  (see "Backup" below).
- **Network: only Notion's API**, only in `data/api/NotionClient.kt`, plus the images in notes
  (Coil, `NoterApp.newImageLoader`). No analytics, crash
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
