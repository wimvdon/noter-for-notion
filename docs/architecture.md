# Architecture

## This app

Noter shows Notion pages as notes, in the style of Google Keep, and edits their title,
properties and content: the pages of databases and pages on their own.

### Screens
- **Overview** (`ui/main/`): three top-level destinations in the navigation bar
  (`logic/Destinations.kt`): **Pinned**, **Notebooks** and **Search**. The notebooks are peers
  inside Notebooks, as scrollable tabs under the top app bar (in the order of Manage notebooks);
  tapping Notebooks shows the notebook seen last. The top app bar has the sync button, a
  notebook's **Filters** and **Sort** buttons, and a ⋮ menu with Edit notebook (in a notebook),
  Manage notebooks and Settings. The header (app bar, tabs and rows of chips) scrolls away with
  the notes and comes back on the first scroll up, and on another notebook
  (`ui/main/QuickReturnHeader.kt`, offset in `logic/QuickReturn.kt`). The bar's order is a setting (`AppSettings.navigation`,
  `Destinations.bar`): Pinned and Notebooks can swap, Search is always last; the app opens on the
  first one (`Destinations.START`) and back goes there before leaving the app.
  - **Pinned**: the pinned notes of everything (databases and pages), filtered by source. Any
    page can be pinned, also one that isn't in a notebook; a note made with + on Pinned is pinned.
  - **Notebooks** (`DatabaseConfig`, kept under `AppSettings.databases`; the code still calls them
    views, the screens say "notebook" in every language, as a name of the app's own): all notes of
    a database (its raw data source; Notion's views aren't used), set up on this phone
    (`logic/Notebooks.kt`): the notebook's own filters per property (a note shows when it has one
    of the selected values of every filtering property), and its sort: the custom order (dragged),
    last edited, created, title or a date property. **Filters** (a dot on the button while
    filters are on) opens the panel: the properties it can filter by, arranged like a note's
    properties with `PropertyArranger` ("Shown" ones have a row of chips above the notes, in
    that order; dragged past the "Hidden" line, or the eye, it loses its row), each with its
    values to filter by right below it (`PropertyArranger`'s `below`; also the hidden ones); Clear
    filters. **Sort** is a menu: tapping the current sort again turns it around, an arrow shows
    which way. Above the notes, one row of chips per quick filter (`DatabaseConfig.quickFilters`),
    each starting with the property's name and, while something in it is selected, ending in ×;
    a row only shows once a note has a value for its property (`ChipFilter.anyValue`).
    Dragging a note in another sort switches to the custom order, starting from the order on
    screen, with an Undo snackbar. Notebooks that followed a Notion view before (`viewId` in old
    settings) now show all notes of their database, in the custom order.
  - **A notebook of several data sources** (`DatabaseConfig.children`): a Notion database with
    more than one data source (`DatabaseInfo.sources`) is one notebook with a sub-notebook per
    data source, shown as secondary tabs below the notebook tabs. Each sub-notebook is a notebook
    of its own (filters, sort, cards, its notes); `Notebooks.leaves`, `update` and `named` treat
    them as such, and on Pinned and for + they are named "Notebook · Sub-notebook".
  - **A page's notebook** (`DatabaseConfig.pageId`, kept under the key
    `NotesRepository.pageNotebookKey`): its notes are the page's subpages (the child pages directly
    on it), "Page order" is their order on the page (`ViewResult`, `views.json`), and a new
    note is a new subpage. Each refresh lists the page's blocks and downloads only the subpages
    that changed (`refreshSubpages`). It has nothing to filter by. Added from "Add notebook"
    (a page in the search) or a page's ⋮ menu, "Add as notebook".
  Notes are cards in a staggered grid with a preview of their content; pinned notes come first
  under "Pinned". Long-press and drag moves a note within its section; a long press without
  moving opens its menu (pin, move to trash). While dragging, the card's place is worked out from
  the finger (not from where the grid put it), so it stays under the finger when cards of other
  heights change places; a card just swapped with isn't swapped back until the finger leaves it;
  near the top or bottom the grid scrolls; after the drop the new order stays on screen until
  the saved one arrives. Filters (Material filter chips) come from one
  property (`logic/ChipFilter.kt`): select, status, multi-select, person, checkbox ("Done" and
  "Not Done", named after the property), relation (a chip per linked page, named by its title),
  created by / last edited by, date (Overdue, Today, Next 7 days, Later, No date) or formula (a
  yes/no formula like a checkbox, others a chip per value, numbers in order of their value).
  A chip's count of notes is set apart in a small pill, so an option "5" with one note doesn't
  read as "5 1". A new note made with a filter selected
  gets its value where it can (not for computed properties; a date only for Today).
- **Search** (`SearchScreen.kt`, a destination in the navigation bar): the notes on this phone whose
  title has every word typed (`logic/NoteSearch.kt`, at once and offline), then the databases and
  pages Notion's search finds; before anything is typed, Notion's most recently edited ones.
  Notion's search lists data sources, not databases. Each data source names its database; the app
  asks Notion about that database once (`NotesRepository.databaseInfo`, kept in
  `databases.json`) and groups the results (`WorkspaceCodec.group`): a database of several data
  sources is listed once, as the database; a data source its database doesn't list is left out
  (the search can show one the connection can't read). The list shows at once with what is kept,
  then again after asking. The search starts afresh each time Search
  is opened. + offers a new note as on Pinned (not pinned). "Add notebook" in Manage notebooks
  opens the same search (`PickNotebookScreen`) with only databases and pages on their own.
- **Database** (`DatabaseScreen.kt`): a database opened from Search or a link in a note, as a
  child page with its notes and filters; "Add as notebook" makes it a notebook, after which the
  button is "Edit notebook" and opens its setup (Manage notebooks), so it isn't added twice.
- **New pages**: + on Pinned offers a note in one of the notebooks and, with a personal access
  token, a page at the top of the workspace (among the user's private pages; when Notion refuses,
  the page is removed again and the app explains); a page made there is pinned. A page's ⋮ menu
  has "New subpage".
- **Note** (`ui/note/`): header image, icon (tap it, or "Add icon", to choose an emoji in
  `PageIconSheet.kt`: common ones, any other typed with the keyboard (`logic/EmojiInput.kt`), or
  none; sent as `PendingOp.SetIcon`, or with the create for a new note), title, properties (`PropertyEditors.kt`; the
  picker of a select or multi-select can add an option: type a name, tap Add, and Notion makes the
  option when the note is saved, `logic/NewOptions.kt`), then the content as a
  list of blocks, each its own text field. To-do items have an × to delete them. A toolbar above the keyboard changes the block's kind,
  adds a divider or deletes the block. Tapping below the content continues at the end. Moving a
  note to the trash asks first.
- **Properties per database** (`logic/PropertyLayouts.kt`, `ui/note/PropertyArranger.kt`): as in
  Notion, every property is shown until the user hides it; hidden ones are folded away under "N
  more properties". The tune button turns the properties into a list to arrange in place: drag a
  property by its handle to change the order, and below the "Hidden" line to hide it (the eye does
  the same with one tap); "Done" goes back. This is kept per database in
  `AppSettings.propertyLayouts` (backed up). What the cards in the overview show is set up per
  notebook (`DatabaseConfig.cards`, in Edit notebook): the header image (off by default), the
  start of the content (on; when off, no previews are downloaded for it) and up to three
  properties (else the card shows the filter property). A notebook from before starts from what
  its database's cards showed (`PropertyLayouts.cards`); on Pinned a note's card follows the
  first notebook of its database (`PropertyLayouts.cardLayouts`).
- **Manage notebooks** (`ui/views/`): the notebooks in their order (up/down; the order of the
  tabs); tapping one edits its name, icon and what the cards show. "Add notebook" opens Search to pick a database (all its notes;
  `Notebooks.newNotebook` gives it a status, else a select, property as its first row of chips,
  in the custom order) or a page (its subpages). Filters and sort are changed in the notebook
  itself. "Add as notebook" on a database's page opens the same step. Opens from the ⋮ menu
  and from Settings.
- **View icons** (`data/icons/IconCatalog.kt`, `ui/common/IconPicker.kt`, `DatabaseIcons.kt`):
  the Material Symbols set (4 229 icons) from `assets/material_symbols.txt` (name, categories and
  SVG path data per icon, from RSS Reader), drawn as vectors and searchable by English name,
  category and a few related words. Views store the icon's name; the 29 keys from before are
  mapped to their Material Symbols names.
- **Settings** (`ui/settings/`): Connection (a personal access token, with the steps to make one,
  and sync status), Views (a link to Manage views), Navigation bar (the order of Pinned and
  Notebooks with up and down arrows, the first marked as where the app opens; Search locked
  last), Appearance; built from the shared
  `SettingsKit.kt`.
- **Data sources** (`NotesRepository.resolveSource`): a key is asked about as a database first
  (its first data source), then as a data source, because Notion may answer for a database's ID
  as if it were a data source. The answers are kept in `sources_v2.json`.
- **Errors** (`data/api/NotionErrors.kt`, `ui/common/ErrorText.kt`): the screens keep an error as
  a key for the common cases (no connection, not found or not shared, Notion busy, token refused,
  no data source), said in the user's language; other errors keep Notion's own (English) message.
- **Token kind** (`data/notion/TokenCodec.kt`): Notion's `GET /users/me` says whether the token
  is a personal access token (a bot owned by a person) or an internal connection's (owned by the
  workspace). It is kept with the token (`ApiKeyStore.kind`; keys saved before are checked once
  at start). With a connection's token, Settings warns that the app only sees the pages shared
  with it, and "New page at the top of your workspace" isn't offered (Notion refuses it). A
  personal access token can't list the workspace's people, so person properties offer the people
  in the notes here and the token's owner (`logic/KnownUsers.kt`).
- **New note** on Pinned asks which notebook (`logic/NoteTargets.kt`). A note made on Pinned is pinned.
- **Empty lists** use the shared `ui/theme/EmptyState.kt`: an icon in a circle, a title, one sentence and, where it helps, a button (Clear filters, Retry). Without an API key the whole screen says "Connect to Notion" with an "Open settings" button, the same words as Time Tracker for Notion (`DESIGN.md`, "Empty and setup states").
- `ui/AppNavigation.kt`: a small back stack of five screens (no navigation library).
- **Pages on their own** (not in a database) are kept like a database's notes under the key
  `NotesRepository.PAGES`, so editing, pinning and the trash work the same. Linked pages and
  databases in a note (child pages, links to pages) open in the app.

- **Data sources** (Notion API 2025-09-03): a Notion database holds one or more data sources,
  each with its own properties and pages. The app treats every data source as a database of its
  own: Browse and Add view list them separately, and notes, views, order and property layouts are
  kept under the data source's ID. A link to a database (a child database or a link in a note)
  opens its first data source, with a tab per data source when it has several. Keys saved before
  (database IDs) are looked up once (`NotesRepository.resolveSource`, remembered in
  `sources.json`) and renamed everywhere to the data source's ID (`data/sync/SourceKeys.kt`).

### Data flow
```
Notion API ── data/api/NotionClient (OkHttp, Notion-Version 2026-03-11, retries 429/5xx)
     │
data/NotesRepository ── single source for the screens
     │   reads:  on-phone copy first (data/store/CacheStore), then refresh (data/sync/SyncPolicy)
     │   writes: on-phone copy at once, queued in data/sync/Outbox, sent in order
     │   content: sent as a diff of blocks (data/sync/ContentDiff), never by rewriting the note
     ▼
ViewModels (ui/*/…ViewModel) ── StateFlow<UiState> ── Composables
```

- **Blocks** (`data/notion/BlockCodec.kt`): paragraphs, headings 1 to 4 (also toggle headings), bulleted,
  numbered and to-do lists, toggles, quotes, callouts, code and dividers are editable. Other blocks
  are shown read-only and left untouched (`ui/note/ReadOnlyBlocks.kt`): images, tables (their rows
  cost one more request per table), equations, files, videos, PDFs, audio, bookmarks, embeds,
  linked pages and the table of contents; tapping one opens it in the browser or in Notion.
  Anything else is an outlined box with its kind, "Can't be shown or edited here" and an "Open in
  Notion" button that opens the page at that block.
- **Nested blocks** (inside a toggle, a list item, a quote, …): downloaded with the note (one
  request per block with nested content, up to 5 levels deep) and kept in the same flat list,
  each block with its `depth` and its Notion parent (`parentId`); the editor indents them 24dp per
  level. The toolbar's indent and outdent buttons, Enter and Backspace nest and un-nest blocks as
  Notion does (`logic/DocumentEditor.kt`). Notion can't move a block or change its kind, so a
  block that moves to another parent or changes kind is deleted and added again with what is in
  it (`data/sync/ContentDiff.kt`); the editor only allows that when everything in it can be added
  again (text and dividers; not images, tables or content nested deeper than the app shows).
  Columns (`logic/Columns.kt`) are downloaded too and shown one below the other, a thin line
  between columns, without indenting what is in them; a block in a column can be edited and
  nested but not taken out of its column. Tabs work the same way: each tab's name (Notion's text
  line directly in the tab block, `BlockCodec.asTab`) is a label with its emoji, and the tab's
  blocks follow it; the names themselves are edited in Notion. What is nested in images, synced blocks and the like
  stays in Notion; such a block says it has nested content. Previews in the overview download only the top level
  (`PageContent.nestedLoaded`), and opening the note gets the rest. Until it arrives, the blocks
  whose nested content is missing show a loading placeholder (`logic/NestedContent.kt`) and the
  note can't be edited, so an edit can't keep the incomplete copy on screen; when it can't be
  downloaded (offline), they say so and the sync button tries again.
- **Page icons** (`data/notion/IconCodec.kt`, `ui/common/PageIconView.kt`): an emoji, an image
  (uploaded, a link, or a custom emoji of the workspace; downloaded like the images in notes), or
  one of Notion's own icons. Notion draws those from SVG files the app can't draw, so the app shows
  the closest Material Symbol (`logic/PageIcons.kt`) in the icon's color.
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
  A send that has started is never cancelled (`NotesRepository.flush` runs `NonCancellable`):
  a new edit only restarts the wait before the next send. Before, typing cancelled the request
  under way, after Notion had added the block but before its ID was recorded, so the block was
  added again (empty checklist items twice).
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
| Notebooks (with their filters and sort), pins, order, property layouts, theme | DataStore `backed_up_settings` | Yes |
| Token and its kind | `EncryptedSharedPreferences` `notion_key_encrypted_prefs` | No |
| Copies of notes, schemas, users, pages' order of their subpages; unsent changes | JSON files in `noBackupFilesDir/notion_cache` | No |
| Images | Coil's cache in `cacheDir/images` | No |

## Structure
```
app/src/main/kotlin/be/wimvandonck/noter/
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
