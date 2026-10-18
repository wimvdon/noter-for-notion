# Architecture

## This app

Noter shows Notion pages as notes, in the style of Google Keep, and edits their title,
properties and content: the pages of databases and pages on their own.

### Screens
- **Home** (`ui/main/MainScreen.kt`, `MainViewModel`): no navigation bar. On top a large, rounded
  search bar ("Search Notion") with the sync button (while changes wait or the phone is offline)
  and Settings at its end (the profile picture of the person the token acts as when Notion tells it, else a cogwheel); tapping it opens Search. Below it, one scrolling grid:
  - **Pinned**: the pinned databases as rows (`AppSettings.pinnedDatabases`: the key that opens
    each, with its name and icon as last seen, so Home shows them offline; a pinned database keeps
    Notion's name and icon each time it is opened; a long press and drag moves one among the others, a long press without moving opens its menu, Unpin; `NotesRepository.savePinnedDatabaseOrder`), then the pinned notes and pages as cards
    (`AppSettings.pinned`, `logic/NoteLists.kt`), in the order they were pinned unless dragged.
    Every database with a copy on this phone is read when the app starts, so they show at once;
    coming to the front refreshes the databases the pinned notes are in (`pinnedSources`).
    Any database, page or note can be pinned: a long press on a row (Home, Search), the pin in a
    note's app bar, a database's ⋮ menu, or a card's menu.
  - **Databases and pages** (#64): what Notion's search lists (not the notes in databases), as a
    tree of two levels like Notion's sidebar (`logic/PageTree.kt`): on top what isn't inside
    another page of the list (at the top of the workspace, or in a page the connection can't see),
    by title, at most 30 until "Show more" (30 more each time); a chevron at the end of a row shows
    the databases and pages right inside it, indented (closed at first; which are open is kept
    while the app runs). Deeper pages are links in their page's content. Where something is comes
    from Notion's `parent` (`WorkspaceItem.parentId`; a data source is placed where its database
    is, `DatabaseInfo.parentId`). Notion's API doesn't give the sidebar's own order, hence by title.
    With several workspaces, one part per workspace (`Workspaces.group`), each folded away with a
    tap on its name (`ApiKeyStore.collapsed`, on this phone only); pinned databases say their
    workspace (`NotesRepository.recent`, `refreshRecent`: one search's worth, newest first, at most
    200). The list is kept on this phone (`recent.json`), so Home shows it at once and offline; it
    is asked again when the app starts (with a token) and on pull-to-refresh. A pin at the end of
    a row shows it is pinned.
  - **+** (`logic/NoteTargets.kt`) offers a page at the top of the workspace (not with a
    connection's token; when Notion refuses, the page is removed again and the app says to check
    the token in Settings) and a note in the pinned databases, then the other databases on Home's
    list (most recently edited first); a database of several data sources offers each, named
    "Database · Data source". With several workspaces it has a part per workspace
    (`NoteTargets.grouped`; a tap on its name folds it away while the sheet is open), and a page at the top
    goes with that workspace's token (`PendingOp.CreatePage.workspace`). A new note opens at once.
  Without a token the screen says "Connect to Notion" with "Open settings" (`DESIGN.md`, "Empty
  and setup states"). Back on Home leaves the app.
- **Sharing to the app** (#63): `MainActivity` takes text shared from another app (Android's send
  action, `text/plain`) and keeps it in `NoterApp.shares` (`logic/SharedText.kt`: the subject or a
  short line before the link is the title, the first web link the link). The app goes back to Home,
  which shows a sheet "Save to" with the places + offers (`MainScreen.ShareSheet`). On a tap,
  `NotesRepository.saveShared` downloads the link's page (`data/web/WebPageClient.kt`, one GET,
  2 MB at most; the only request outside Notion's API besides images, see `PRIVACY.md`), reads its
  title, headings, paragraphs, list items, quotes, code and images (`logic/WebArticles.kt`, a small
  reader of its own: no HTML library), and makes the note (`logic/SharedNotes.kt`): the title, then
  the link as a bookmark (Notion shows its preview), the shared text and the page's content, at
  most 100 blocks. In a database with a URL property, the shared link also goes in that property
  (#68, `SharedNotes.urlProperty`: one named URL, Link, Source, Website or Web, else the first). Images stay links to the website (external image blocks). The content goes
  with the page when Notion makes it (`PendingOp.CreatePage.children`); when Notion refuses it
  (status 400, mostly an image link it can't use), the page is made again without the images,
  then without the bookmark, then empty (`BlockCodec.createAttempts`). Offline, the link isn't
  downloaded (the note gets the bookmark and text) and the note is made once the phone is online.
- **Database** (`DatabaseScreen.kt`, `DatabaseViewModel`): a database opened from Home, Search or a
  link in a note. All notes of its data source (Notion's views aren't used), shown as its setup
  says (`DatabaseSetup` in `AppSettings.setups`, per data source; `logic/DatabaseSetups.kt`): the
  values selected per property (a note shows when it has one of the selected values of every
  filtering property), the properties with a row of chips above the notes, the sort (the custom
  order (dragged), last edited, created, title or a date property) and what the cards show.
  Every change is kept at once; there is nothing to save. A database opened for the first time
  shows all notes in the custom order, with its status (else select) property as its first row
  of chips (`DatabaseSetups.initial`). The top app bar has the sync button, **Filters** (a dot on
  the button while filters are on: the panel lists the properties it can filter by, arranged like
  a note's properties with `PropertyArranger` ("Shown" ones have a row of chips above the notes, in
  that order; dragged past the "Hidden" line, or the eye, it loses its row; the order of the hidden ones is kept too, `DatabaseSetup.filterOrder`), each with its values
  to filter by right below it (`PropertyArranger`'s `below`; also the hidden ones); Clear
  filters), **Sort** (a menu: tapping the current sort again turns it around, an arrow shows which
  way), **Cards** (the Tune icon, as a page's properties button) and ⋮: Pin (or Unpin), Open in Notion and Share (Notion's link to the database; a data source's database comes from `DatabaseSchema.databaseId`, the last search or Notion, `NotesRepository.databaseOf`). Cards sets the header image (off by default), the start of the
  content (on; when off, no previews are downloaded for it) and up to three properties, else the
  card shows the first row's property). Above the notes, one row of chips per quick filter,
  each starting with the property's name and, while something in it is selected, ending in ×; a
  row only shows once a note has a value for its property (`ChipFilter.anyValue`). The header
  (app bar, tabs and rows of chips) scrolls away with the notes and comes back on the first scroll
  up, and on another data source (`ui/main/QuickReturnHeader.kt`, offset in `logic/QuickReturn.kt`).
  Dragging a note in another sort switches to the custom order, starting from the order on screen,
  with an Undo snackbar.
  - **A database of several data sources** (`DatabaseInfo.sources`): a tab per data source, each
    with its own setup. It is pinned as the database; a database of one data source as its data
    source.
  - **A data source Notion can't find** (`NotesRepository.missingSources`: a 404): "Notion can't
    find this data source" with Unpin (when pinned) and Retry, instead of its notes.
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
- **Pages** open in the editor, also a page with subpages: its subpages are links in its content
  (child pages), which open in the app. A page's ⋮ menu has "New subpage".
- **Search** (`SearchScreen.kt`, a child page from Home's search bar, sharing Home's ViewModel): one
  list (#60) of the notes on this phone whose title has every word typed (`logic/NoteSearch.kt`,
  at once and offline) and the databases and pages Notion's search finds, each page once, best
  matching titles first (`logic/SearchResults.kt`: the same title, starts with it, every word
  starts a word, every word is in it, else Notion's own match; newest first among equals);
  before anything is typed, Notion's most recently edited ones. Filter chips show only pages,
  only databases or (with several workspaces) one workspace; each row says its workspace. Notion's search lists data sources, not databases. Each data
  source names its database; the app asks Notion about that database once
  (`NotesRepository.databaseInfo`, kept in `databases.json`) and groups the results
  (`WorkspaceCodec.group`): a database of several data sources is listed once, as the database; a
  data source its database doesn't list is left out (the search can show one the connection can't
  read). The list shows at once with what is kept, then again after asking. The search starts
  afresh each time Search is opened; a long press on a database or page pins it.
- **Notebooks from before** (`logic/LegacyNotebooks.kt`): until this redesign the app had
  notebooks (tabs set up in Manage notebooks, kept under `AppSettings.databases`). They are read
  once, when the settings are read: each database keeps the filters, rows of chips, sort and cards
  of its notebook (the first one, when there were several) and is pinned on Home, in the order of
  the tabs; a page's notebook becomes a pinned page (downloaded at start when there is no copy
  of it, `NotesRepository.openPinnedPages`). Copies of a page notebook's subpages (keys
  `page:<id>`) are still read, so a change or new subpage waiting to be sent isn't lost; they
  aren't refreshed.
- **Note** (`ui/note/`): header image, icon (tap it, or "Add icon", to choose an emoji in
  `PageIconSheet.kt`: common ones, any other typed with the keyboard (`logic/EmojiInput.kt`), or
  none; sent as `PendingOp.SetIcon`, or with the create for a new note), title, properties (`PropertyEditors.kt`; the
  picker of a select or multi-select can add an option: type a name, tap Add, and Notion makes the
  option when the note is saved, `logic/NewOptions.kt`; an option any cached note has is added to
  the database's properties at once, `logic/SchemaOptions.kt`, and opening a note downloads its
  database's properties again so options added in Notion show; every picker lists what is selected first when it opens and then keeps its order while ticking, `logic/ChoiceOrder.kt`), then the content as a
  list of blocks, each its own text field. To-do items have an × to delete them. A toolbar above the keyboard changes the block's kind,
  adds a divider, deletes the block or starts selecting lines (a tap selects a line; the bar then has Select all, Copy as Markdown text and Delete, `logic/BlockSelection.kt`). A new line is scrolled into view just above the keyboard and no further (`logic/Reveal.kt`). The title row ends in the button that arranges the properties; the ⋮ menu has Share (the page's Notion link) and Select text: the note as plain text in one selection area, so a selection can run across lines (`SelectTextView.kt`, `logic/ReadingText.kt`; nothing can be changed there). Tapping below the content continues at the end. Moving a
  note to the trash asks first.
- **Properties per database** (`logic/PropertyLayouts.kt`, `ui/note/PropertyArranger.kt`): as in
  Notion, every property is shown until the user hides it; hidden ones are folded away under "N
  more properties". The tune button turns the properties into a list to arrange in place: drag a
  property by its handle to change the order, and below the "Hidden" line to hide it (the eye does
  the same with one tap); "Done" goes back. This is kept per database in
  `AppSettings.propertyLayouts` (backed up). What the cards show is part of the database's setup
  (`DatabaseSetup.cards`); a database set up before starts from what its cards showed
  (`PropertyLayouts.cards`), and on Home a note's card follows its database's setup
  (`PropertyLayouts.cardLayouts`).
- **Icons of Notion's own** (`data/icons/IconCatalog.kt`, `DatabaseIcons.kt`): the Material Symbols
  set (4 229 icons) from `assets/material_symbols.txt` (name, categories and SVG path data per icon,
  from RSS Reader), drawn as vectors, for the closest match to a Notion icon (`logic/PageIcons.kt`).
- **Settings** (`ui/settings/`): Workspaces (every token, links to make a personal access token
  or a connection, a field to add one, and sync status) and Appearance; built from the shared `SettingsKit.kt`.
- **Workspaces** (#58): a token belongs to one workspace, so there can be one per workspace or
  Notion account. The first is the main token (`ApiKeyStore.get`, with its names in
  `ApiKeyStore.main`); the others are `ApiKeyStore.extras` (encrypted like it and never backed
  up; the list itself is `logic/WorkspaceTokens.kt`). Each is tested with `GET /users/me` when
  added and asked again at each start, and shown with its workspace's name and kind. Removing the
  main token makes the next one the main; removing the last deletes the notes on the phone.
  `NotionClient` gets every token: a request about a page, block, database or data source goes
  with the token of the workspace that answered for that ID before (`logic/KeyRouter.kt`; IDs come
  from the path or a new page's parent and from the answers, see `data/api/NotionRoutes.kt`),
  else with each token in turn until one isn't answered 404 (or 401/403). Which workspace each
  page, database and data source is in is kept on the phone (`workspaces.json` in the cache,
  under `Workspaces.id`, a short hash of the token, never the token) and remembered with one
  token too, so lists can say where things are (`logic/Workspaces.kt`); blocks are only
  remembered while the app runs.
  Search and the people list ask every workspace and merge (newest first); a refused token only
  drops its own results. Things without an ID (a page at the top of the workspace, `/users/me`)
  use the main token. The rest of the app doesn't know about workspaces: Notion's IDs are unique,
  so cache files and pins stay as they are. Removing a token keeps the notes on the phone; Notion
  answers "not found" for them from then on.
- **Which data source** (`NotesRepository.resolveSource`): a key is asked about as a database first
  (its first data source), then as a data source, because Notion may answer for a database's ID
  as if it were a data source. The answers are kept in `sources_v2.json`.
- **Errors** (`data/api/NotionErrors.kt`, `ui/common/ErrorText.kt`): the screens keep an error as
  a key for the common cases (no connection, not found or not shared, Notion busy, token refused,
  no data source), said in the user's language; other errors keep Notion's own (English) message.
- **Token kind** (`data/notion/TokenCodec.kt`): Notion's `GET /users/me` says whether the token
  is a personal access token (a bot owned by a person) or an internal connection's (owned by the
  workspace). It is kept with the token (`ApiKeyStore.kind`) and asked again at each start, together with the profile picture of the person
  it acts as (`TokenInfo.avatarUrl`, kept in `ApiKeyStore.avatarUrl`, shown on Home). With a connection's token, Settings warns that the app only sees the pages shared
  with it, and "New page at the top of your workspace" isn't offered (Notion refuses it). A
  personal access token can't list the workspace's people, so person properties offer the people
  in the notes here and the token's owner (`logic/KnownUsers.kt`).
- **Empty lists** use the shared `ui/theme/EmptyState.kt`: an icon in a circle, a title, one sentence and, where it helps, a button (Clear filters, Retry). Without an API key the whole screen says "Connect to Notion" with an "Open settings" button, the same words as Time Tracker for Notion (`DESIGN.md`, "Empty and setup states").
- `ui/AppNavigation.kt`: a small back stack of five screens (Home, Search, Settings, notes and
  databases; no navigation library).
- **Pages on their own** (not in a database) are kept like a database's notes under the key
  `NotesRepository.PAGES`, so editing, pinning and the trash work the same. Linked pages and
  databases in a note (child pages, links to pages) open in the app.

- **Data sources** (Notion API 2025-09-03): a Notion database holds one or more data sources,
  each with its own properties and pages. The app keeps notes, setups, order and property layouts
  under the data source's ID. A link to a database (a child database or a link in a note) opens
  its first data source, with a tab per data source when it has several. Keys saved before
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
  stays in Notion; such a block says it has nested content. Previews on the cards download only the top level
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
- **Sync button** (`ui/common/SyncButton.kt`): in a note's app bar, and on Home and in a database
  while changes wait or the phone is offline. It sends what is waiting and gets the latest version.
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
- **Previews** (`logic/NotePreviews.kt`): the cards show the start of each note's content from
  the on-phone copy. Copies that are missing or older than the note's last edit in Notion are
  downloaded one note at a time (at most 24 per round).
- **Order and pins** are only on this phone (`AppSettings.order`, `AppSettings.pinned`,
  `AppSettings.pinnedDatabases`); Notion is not changed.

### Where data lives
| What | Where | Backed up |
|---|---|---|
| Each database's setup (filters, rows of chips, sort, cards), pinned databases (key, name, icon), pinned notes and pages, order, property layouts, theme | DataStore `backed_up_settings` | Yes |
| Token and its kind | `EncryptedSharedPreferences` `notion_key_encrypted_prefs` | No |
| Copies of notes, schemas, users, Home's list of databases and pages; unsent changes | JSON files in `noBackupFilesDir/notion_cache` | No |
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
- **Navigation**: five screens on a small back stack in `ui/AppNavigation.kt` (see `decisions.md`).
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
