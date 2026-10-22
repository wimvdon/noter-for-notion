# Testing

## Automatic (CI, on every pull request and push to `main`)

| Check | Command | Catches |
|---|---|---|
| Release script tests | `python3 -m unittest discover -s scripts/tests` | Version maths, keystore-secret decoding |
| Sign-in helper tests | `node --test worker/test/noter-sign-in.test.mjs` | The helper's code exchange, sealing, refresh and what it refuses (with a stand-in for Notion) |
| Unit tests | `./gradlew testDebugUnitTest` | Logic bugs in plain-Kotlin classes (`app/src/test/`) |
| Lint | `./gradlew lintDebug` | Android API misuse, missing translations, accessibility, hard-coded text, … (errors fail the build) |
| Debug build | `./gradlew assembleDebug` | Compile errors; produces the APK to test on the phone |
| Release build | `./gradlew assembleRelease` | Problems caused by R8 minification / resource shrinking |

Reports (test results, lint HTML) are attached to every CI run as the `reports` artifact, also
when the run fails.

Run all of it locally before pushing:
```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
python3 -m unittest discover -s scripts/tests
node --test worker/test/noter-sign-in.test.mjs
```

### What the unit tests cover
| Test | Covers |
|---|---|
| `logic/ContentMergeTest` | Typing while a newer version comes from Notion: the edits go on top of it, keeping blocks added or changed in Notion meanwhile |
| `data/NotesRepositoryTest` (lost answers) | A page Notion made without answering isn't made again; one it didn't make is made on the next try |
| `logic/HomeLevelsTest` | Which pages are Home's top level, and which subpages and databases inside them the search missed |
| `logic/DatabaseEditTest` | What a database's new name or icon sends to Notion, and which rows, pins and kept names change |
| `logic/SubpageTitlesTest` | A page's subpage and database titles follow Notion, and only those blocks change |
| `logic/WorkspaceWaysTest` | Which tokens Advanced offers, in which order, and which is chosen first |
| `logic/SealedBoxTest`, `logic/SignInsTest` | Connect to Notion: opening what the sign-in helper sealed (a sample sealed by the helper itself), the state and authorize link, reading Notion's token answer |
| `logic/TokenRefresherTest`, `data/api/RenewKeyClientTest` (in `MultiWorkspaceClientTest.kt`) | Refreshing a refused Connect to Notion token once, one refresh at a time, keeping its workspace's ID; pasted tokens are never refreshed |
| `logic/WorkspaceTokensPlaceTest` (in `WorkspaceTokensTest.kt`) | Each Notion workspace once: where a new token goes (main, after the others, in place of the same workspace's token), that a way that sees less never replaces one that sees more, and the clean-up of duplicates from before |
| `data/notion/TokenCodecTest`, `data/api/NotionRoutesTest` | Notion's workspace ID for every kind of token; a page found by two tokens listed once |
| `logic/DocumentEditorTest` | Enter, Backspace, Markdown shortcuts (Notion's), dividers, code blocks, numbering; nesting (indent, outdent, Enter and Backspace in nested blocks, what may move) |
| `data/notion/BlockCodecTest` | Reading and writing every editable block type; images, files, links, tables and linked pages read-only |
| `data/model/NotionFilesTest`, `logic/ReadOnlyBlocksTest` | Expiring links to files Notion hosts; file names and links of read-only blocks |
| `data/notion/InlineMarkdownTest` | Bold, italic, strikethrough, code and links ⇄ Notion rich text |
| `data/notion/PropertyCodecTest` | Reading and writing page properties |
| `data/sync/ContentDiffTest` | The block changes sent to Notion for an edit, also for nested blocks (new blocks in their parent, moved blocks added again) |
| `data/sync/BlockMatchTest` | New blocks keep the IDs Notion gave them, and blocks Notion added without answering are recognised, so nothing is added twice |
| `data/sync/OutboxTest` | The queue of unsent changes (merging, renaming new notes) |
| `data/sync/SyncPolicyTest` | When to refresh, and merging refreshed notes |
| `data/sync/SaveTimingTest` | How soon edits are saved while typing (within Notion's rate limit) |
| `data/sync/SourceKeysTest` | Keys saved before data sources renamed to the data source (setups, pinned databases, order, layouts, waiting new notes) |
| `data/NotesRepositoryTest` | The repository against a fake Notion server (`FakeNotion`, the JDK's own HTTP server): a setup saved before data sources moves to its data source, every database with a copy read at start and a pinned page without one downloaded, a waiting new subpage of a page's notebook from before still made inside the page, Home's list of databases and pages kept on the phone, setups kept at once and databases pinned and unpinned, pages made inside a page and a refused top-level page removed, new notes stay open while Notion creates them, nothing is sent for a note in the trash, table rows, expired image links, pages from Browse and linked pages, new to-dos added once (also after a lost answer), nested blocks downloaded and added to their parent, columns downloaded and new lines added to their column, the database a data source is in for links to Notion |
| `data/SyncStressTest` | Random editing (typing, Enter, ticking, changing kinds, indenting, outdenting, deleting) while a slow fake Notion that keeps the blocks, nested ones too, answers: Notion ends with exactly the note on the phone, also when it can't insert at the start |
| `data/store/CacheStoreTest` | On-phone copies survive a restart |
| `logic/NoteOrderingTest`, `logic/ChipFilterTest` | Pins (Home's pinned notes of every database), custom order, filters (checkbox, date ranges, relations, created by, formulas, numbers in order of their value) |
| `data/notion/TokenCodecTest`, `logic/NoteListsTest` | The token's kind, owner and profile picture; pinned databases in the order they were dragged to |
| `logic/BlockSelectionTest`, `logic/RevealTest` | Selecting lines of a note (what can be selected, the Markdown that is copied) and how far a new line scrolls into view above the keyboard |
| `logic/ReadingTextTest` | The note as plain text for Select text: list markers, numbers, ticked boxes, Markdown marks removed (not in code), blocks the editor only shows left out |
| `logic/ChoiceOrderTest` | A picker's order: selected first when it opens, then nothing moves while ticking; new choices at the end |
| `data/api/NotionErrorsTest` | Which errors the screens say in the user's language |
| `logic/NewOptionsTest` | When the picker of a select or multi-select offers to add what was typed (not for an existing name in any case, nor with a comma) |
| `logic/SchemaOptionsTest` | An option a note has (added on the phone or in Notion) joins its property's options once, so every picker shows it |
| `logic/DatabaseSetupsTest` | A database's notes as its setup shows them: filters per property, sorts, the first chip row of a database opened for the first time, the setup found with or without dashes, the order of the chip rows, a page's order from before read as the custom order |
| `logic/LegacyNotebooksTest` | Notebooks from before the redesign: each database keeps its notebook's setup and is pinned in the order of the tabs (also a notebook of several data sources), a page's notebook becomes a pinned page, the first notebook of a database wins, a setup made since then stays, old filter properties |
| `logic/ColumnsTest` | Columns: how far their blocks are indented, and that blocks stay in their column |
| `ui/main/PresetForTest` | The value a new note gets from the selected filter |
| `data/notion/BlockCodecTest` (shares) | Images and bookmarks from a share written as links; a refused shared page tried with less |
| `data/notion/WorkspaceCodecTest` | The list of databases and pages from Notion's search, data sources (grouped by their database; one the database doesn't list is left out) and the data source a page belongs to; the page each page or database is in, a data source placed where its database is |
| `data/icons/IconCatalogTest` | Reading the icon file, icon search, old icon keys, and that the bundled file has every suggested icon |
| `logic/PageTreeTest` | Home's tree of databases and pages: the top level (what isn't in another page of the list) and the level below, most recently edited first (a page counting what is inside it), IDs with or without dashes |
| `logic/NewDatabasesTest` | A new database's request: where it goes, its title, the title property and its properties in order, several of a kind numbered as Notion does, each name once |
| `logic/SharedTextTest`, `logic/SharedNotesTest`, `logic/WebArticlesTest` | Sharing to the app: the title, text and link of a share; the new note's title and blocks; a web page's content read in order without scripts and menus, links made whole, the description when there is nothing to read |
| `logic/NoteTargetsTest` | Where + on Home offers a new note: the pinned databases, then the others on Home's list, each data source of a database of several; a part per workspace, with a page at the top unless the token is a connection's |
| `logic/WorkspacesTest` | A token's short ID, the workspaces' names, which workspace a page or database is in, lists split by workspace |
| `logic/SearchResultsTest` | Search's one list: phone notes and Notion's results mixed, each page once, best matches first; filters by kind and workspace |
| `logic/PropertyLayoutsTest` | Which properties are shown, collapsed and on the card, and in which order; header image and content on cards; cards per database setup, starting from what its cards showed before |
| `logic/NotePreviewsTest` | Which previews are downloaded, and the preview text |
| `logic/NotionDatesTest` | Dates as Notion sends them |
| `BackupRulesTest` | Only the settings file is backed up |

### Writing unit tests
- Every logic change gets a test. Put logic in plain Kotlin classes (no Android types) and test
  them with JUnit in `app/src/test/kotlin/`, mirroring the package. See `DocumentEditorTest`.
- Test names describe behaviour in words: `` fun `tap stops at the maximum`() ``.
- No device tests (instrumented/UI tests) are set up: they need an emulator, which makes CI slow and
  flaky. Add them only if a decision in `decisions.md` says so.

## Manual (on the phone)

What automation can't check: how it looks and feels on a real device. For each pull request (before or after it is merged: merging only waits for green CI),
install the debug APK (see README, "Testing a pull request") and follow the steps in the pull request
description. In addition, check every now and then (always before a release):

- [ ] The app starts, and the changed screen looks right.
- [ ] **Dark mode**: switch the phone's dark theme on/off. Text stays readable.
- [ ] **Rotation** (if auto-rotate is on): the screen still works and keeps its state.
- [ ] **Offline**: turn on flight mode, edit a note, turn it off. The cloud icon in the top bar
      disappears and the change shows up in Notion.
- [ ] **Notion round trip**: edit a note in Notion, pull down to refresh the overview. The card's
      preview and the note show the change.
- [ ] **Language**: Settings → System → Languages → App languages → this app → Nederlands.
      All text is translated.
- [ ] **Large text**: Settings → Display → Font size at maximum. Nothing is cut off.
- [ ] **Back** gesture/button behaves as expected.
- [ ] After a release: install the release APK over the previous release. It updates without
      losing data.
- [ ] **Backup** (once the app stores settings): change a setting, Settings → search "Backup" →
      Back up now, uninstall, install the same APK again. The setting is back.

## Reading a crash from a release build
Release builds are minified, so stack traces show short names like `a.b.c`. Download
`<app>-<version>-mapping.txt` from the matching GitHub Release and decode the trace with R8's
`retrace` (Android SDK `cmdline-tools/latest/bin/retrace`):
```sh
retrace <app>-<version>-mapping.txt stacktrace.txt
```
Or ask Claude Code to do it: give it the stack trace and the version.
