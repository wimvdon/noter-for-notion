# Decision log

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-10-16: Shared empty state, same setup screen as Time Tracker

A Material 3 audit of the sibling apps found empty screens drawn three different ways; this app showed a grey icon with one line of text. Its empty, no-result, failed and "no API key" screens now use the shared `ui/theme/EmptyState.kt` (`DESIGN.md`, "Empty and setup states"): an icon in a circle, a title that says what the state means, one sentence on what to do, and a filled button where there is a next step. The "no API key" screen says "Connect to Notion" with an "Open settings" button, the same words as Time Tracker for Notion, which needs the same key. Filtered-empty now offers "Clear filters" instead of "All". Rejected: keeping a single line of text (it doesn't say whether the state is expected) and illustrations per screen (more to draw and maintain, and they don't help the next step).

## 2026-10-16: Notion API 2025-09-03, every data source a database of its own

The owner asked for databases with several data sources ("it's fine if each source appears as a
separate database"). Notion's API version 2022-06-28 can't read or write such databases, so the
app moves to 2025-09-03: schemas and queries go to the data source, new pages name their data
source, and search lists data sources. The app keeps each data source as a database of its own.
Views and copies saved under a database's ID are renamed to its (first) data source's ID the
first time Notion is reached, so nothing has to be set up again. Rejected: staying on 2022-06-28
(databases with a second data source stop working) and showing a database's data sources merged
into one list (their properties differ, so filters and new notes wouldn't work).

## 2026-10-16: Nested blocks as one flat list with a depth

The owner asked for nested blocks. They are kept in the editor's flat list of blocks, each with
its nesting depth, instead of a tree: the editor's rules (Enter, Backspace, the toolbar, focus,
the list on screen) stay simple, and a block's parent follows from the depth. Notion's API can't
move a block or change its kind, so indent, outdent and a kind change delete the block and add
it again with what is in it; this is only allowed when all of that can be added again. Nested
content is downloaded up to 5 levels deep (one request per block with nested content).
Rejected: a tree of blocks (every editor rule and the screen would need rewriting) and showing
nested blocks read-only (the owner wants to edit them).

## 2026-10-16: View icons from the whole Material Symbols set, with search

The owner asked for many more view icons, searchable like RSS Reader's folder icons. The app now
bundles the same file as RSS Reader: the Material Symbols set (4 229 icons, outlined rounded
style, Apache 2.0, licence in `assets/`) as one text file of names, categories and SVG path data
(about 0.9 MB in the APK), with the same search. Views store the icon's name; the 29 icons from
before are mapped to their Material Symbols names. Rejected: `material-icons-extended` alone (the
app already uses it, but it has no search words, and listing thousands of its icons by hand is
not practical). Trade-off: search words are English.

## 2026-10-16: Drawer for everything, the bar for pinned views as far as they fit

The owner asked for Settings in the menu, a separate Manage views screen, and Pinned and Browse
always in a bottom bar with views pinned to it "up to the number the screen size allows". The
drawer now always holds every destination plus Manage views and Settings, and opens only from
its menu button: Material's drawer also opens with a swipe from the edge, which on Android's
gesture navigation fights the back gesture. The bar holds one item per 80dp of window width, so
five on a phone and more on a tablet or unfolded phone; this goes beyond Material's three to five
on wide windows, by the owner's choice. Rejected: the bar only up to five, with a drawer instead
of it beyond that (the old behaviour: the bar disappeared once a sixth destination was added).
## 2026-10-16: Browse, pages on their own, and views instead of added databases

The owner asked for pages outside databases, one list of all pages and databases, and for "added
databases" to become views: a database pinned to the navigation, optionally with filters on.

- **Destinations:** Pinned, Browse, then the views. Material's navigation bar holds three to five
  destinations, so up to three views fit; with more, all destinations move to a navigation
  drawer (views under their own header), as Material prescribes. Rejected: a scrolling bar
  (Material's bar never scrolls) and an always-on drawer (the bar is quicker with few views).
- **Browse** uses Notion's search (one or two requests of 100, newest first). A database's own
  pages are left out unless searching, so a big database doesn't bury the rest. Rejected: a page
  tree (one request per level).
- **Views** reuse `DatabaseConfig` with a `databaseId` and `filters`, so views saved before keep
  working without a migration (their ID is their database's). A database can have several views.
  Pins and the note order stay per database.
- **Pages on their own** are kept like a database's notes under `NotesRepository.PAGES`, so
  editing, saving, pinning and the trash need no second code path. New pages can't be created
  outside a database yet (Notion needs a parent page; not asked for).
- **Databases opened from Browse** get the filter property of one of their views, else their
  first status or select property.

## 2026-10-16: The user chooses which properties are shown, per database

The owner asked for only three properties by default instead of collapsing the read-only ones,
a way to choose the shown ones per database, drag and drop, and up to three on the cards.

- **One layout per database** (`PropertyLayout` in the backed-up settings): order, shown
  properties (null means the default, the first three) and up to three on the card. Properties
  added in Notion later come last and start collapsed.
- **Edited in a bottom sheet** from the note (tune button below the properties), because the
  choice is made while looking at a note. Rows have a drag handle (and move up/down actions for
  screen readers), an eye toggle and a card toggle. Rejected: a page in Settings (far from where
  the result shows) and a drag-and-drop library (CLAUDE.md prefers no new dependencies; a list of
  fixed-height rows needs little code).
- **Cards** show the chosen properties' values; with none chosen they show the filter property's
  options, as before.

## 2026-10-16: Images with Coil, and more of Notion's blocks shown

The owner asked for header images and more block types, viewing before editing, without
making the app slow.

- **Network:** the app now also downloads images from Notion's file storage and from the
  websites that image blocks and covers link to. The owner agreed to this (it was asked before
  adding it), and `PRIVACY.md` says so. Other files are only opened in the browser on a tap.
- **Coil 3** (`io.coil-kt.coil3`) loads the images: the mainstream image library for Compose,
  with memory and disk caching and downscaling. The owner agreed to the new library. It uses
  OkHttp, which the app already has. Rejected: an own loader on OkHttp (more code, worse caching).
- **Expiring links:** files Notion hosts come with links that work for about an hour. Content with
  an expired link is downloaded again when the note opens, and Coil caches images under the link
  without its signature. Rejected: refreshing links in the background (more requests).
- **Shown read-only:** images, tables, equations, files, videos, PDFs, audio, bookmarks, embeds,
  linked pages and the table of contents. Tables cost one extra request each (their rows are
  nested blocks). Still not shown: other nested content (inside toggles, list items, columns,
  synced blocks), because every level is another request per block.

## 2026-10-16: An × on to-do items, "Pinned" and "filters"

- **To-do items get an ×** to delete them with one tap, because the owner asked for it after
  using the app (it was removed earlier only because Notion's editor has none).
- **"Home" is called "Pinned"**, because that is all it shows, and **chips are called
  "filters"** in the app's text (Material's component is still the filter chip).
- **Moving a note to the trash from the editor asks first**, as the design rules require for
  destructive actions.

## 2026-10-16: Keep-style overview, Notion's block types, saving about once a second

After trying v0.0.1 the owner asked for the overview to look and work like Google Keep, for the
editor to stick to what Notion supports, and for saving as often as Notion allows.

- **Overview:** notes are outlined cards in a staggered grid (`LazyVerticalStaggeredGrid`, two
  columns on a phone) with a preview of their content, pinned notes on top under "Pinned".
  Long-press and drag moves a note; a long press without moving opens its menu. The reorder
  button with drag handles and the pin button on every card are gone (pin and unpin from the
  menu or the note). Previews come from the on-phone copy; missing or outdated copies are
  downloaded one note at a time, at most 24 per round (`NotePreviews`), so the overview never
  floods Notion. Rejected: a preview property in Notion (changes the user's database) and
  downloading every note's content on each refresh (hundreds of requests).
- **Navigation:** Home and the databases are top-level destinations in Material's docked
  `NavigationBar` (the shared design rules: floating bars are for actions), or a navigation drawer
  when there are more than five. Rejected: keeping the floating pill.
- **Editor:** the checklist extras (section progress, "Add item" under a list, "Move checked items
  down", "Delete checked items", the × on every to-do, the Checkbox and Text buttons below a note)
  are gone: Notion has none of them. Added the block types Notion has with editable text: toggles,
  toggle headings, quotes, callouts, code and dividers, with Notion's shortcuts (`> ` toggle,
  `" ` quote, ```` ``` ```` code, `---` divider). Updates send only the text, so a callout's icon and
  a code block's language stay as they are. Still read-only: images, tables, embeds and other
  blocks without text, and nested blocks.
- **Saving:** edits reach the on-phone copy after a 250 ms pause and at most 1 s after the first
  change, and the queue is sent after a 400 ms pause and at most 1 s after the first unsent change
  (`SaveTiming`). That is about one round per second while typing, one request per changed block,
  under Notion's limit of about three requests per second; 429 answers are retried after
  Retry-After. Rejected: saving on every keystroke (a request per letter hits the limit) and
  waiting for typing to stop (long notes typed in one go were only sent at the end).
- **Icon:** a minimal sticky note whose top and left edges show depth, a nod to the Notion cube,
  with a folded corner, two white text lines and the amber gradient. The previous icon (a checkbox
  on the note) was too busy.

## 2026-10-15: Noter's building blocks

- **Notion API:** OkHttp and kotlinx.serialization, the same libraries as Time Tracker for Notion,
  with Notion-Version 2022-06-28. Calls only in `data/api/NotionClient.kt`.
- **Internet permission**, needed to reach `api.notion.com`; nothing else is contacted.
- **API key** in `EncryptedSharedPreferences` (androidx.security-crypto), as in Time Tracker for
  Notion; never backed up.
- **Material icons extended** for the database icons a user can pick.
- **On-phone copy as JSON files** in the no-backup folder (`CacheStore`), not DataStore: notes and
  their content can be large and are a cache of Notion, while DataStore is meant for small
  settings. Settings (databases, pins, order, theme) are in the DataStore `backed_up_settings`.
- **Changes are queued and sent as diffs:** every edit goes to the copy at once and into a queue
  (`Outbox`) that is sent in order and retried when offline; content is sent as the changed blocks
  (`ContentDiff`), never by rewriting the note, so edits made in Notion elsewhere in the note stay.
- **minSdk 31**, as the template.
- **No navigation library:** three screens on a small back stack (`ui/AppNavigation.kt`).
  Rejected: Navigation Compose (another dependency for three screens).

## 2026-10-15: Android's backup copies settings only, from an allow list

Users expect their settings back after a reset or on a new phone, and Android does that for free with its own backup (Google account) and phone-to-phone transfer. The template now lists exactly one file, the DataStore `backed_up_settings`, in `data_extraction_rules.xml`; everything else stays on the phone. An allow list means a file added later (a sign-in token, device state) is never backed up by accident, and `BackupRulesTest` fails if the rules drift. Rules for what goes where are in `docs/architecture.md`, "Backup". Rejected: backing up everything with exclusions (one forgotten exclusion leaks a token into the backup), switching backup off (users lose their settings) and our own backup to Google Drive (needs network access, sign-in and more code for what Android already does).

## 2026-10-15: Phone testing doesn't block merging

The owner can't review code and can't always test a pull request on the device right away, so pull requests waited for a manual test that adds little over CI. Green CI (unit tests, lint, builds) is now enough to merge; the owner tests afterwards with the steps from the pull request and always before a release, and problems are fixed in a new pull request. Merging to `main` does not reach users: releases stay manual. Rejected: keeping the phone test as a merge gate (pull requests pile up and go stale).

## 2026-10-15: Code is "all rights reserved" by default

Without a `LICENSE` file the legal default is already all rights reserved, but that isn't visible and is easy to misread for a public repository. The template now ships an explicit all-rights-reserved `LICENSE`, so every app starts strict and the owner opts in to sharing. Switching to a permissive licence (MIT or Apache-2.0) is a one-file swap, described in the README. Rejected: a commented-out alternative inside `LICENSE` (GitHub and licence scanners read the whole file, so it would be misdetected) and starting with MIT (opening up later is easy, taking it back for code already shared is not).

## 2026-10-14: Shared design rules have their master copy in the template

`DESIGN.md` was kept identical in the three sibling apps by hand, with no single place to start a change, and the copies had drifted (Snoozer was missing the Loading section; the Tracker for Simkl icon row was out of date in two apps). The android-app template (`wimvdon/android-app`) now holds the master copy; each app keeps a full copy so Claude sessions can read the rules without opening another repository, and only its "This app" section is its own. Rejected: keeping the file only in the template (sessions in an app would have to fetch it every time) and a shared library for the design kit (a new dependency and more setup than three small apps need). The design kit files (`ui/theme/`) stay in the apps.

## 2026-10-13: Template baseline

- **Language/UI:** Kotlin + Jetpack Compose + Material 3, single `app` module. This is Google's
  recommended, mainstream stack with the most documentation and examples.
- **Tool versions (latest stable on this date):** Gradle 9.8.1, Android Gradle Plugin 9.4.1
  (uses its built-in Kotlin support, so no separate `kotlin-android` plugin), Kotlin 2.4.21
  (Compose compiler plugin), Compose BOM 2026.09.00. Dependabot keeps these current.
- **minSdk 31 (Android 12).** The owner installs apps on their own, recent phone, so older
  Android versions aren't worth the extra code and testing. Android 12 brings Material You dynamic
  colour (no fallback code path), the modern splash screen and per-app language settings. It still
  covers the large majority of active devices. Lower it only if someone with an older phone needs
  the app.
- **compileSdk / targetSdk 37 (Android 17)**, the latest stable API level. The minor SDK release
  37.2 exists but isn't needed by anything yet.
- **Java 17 bytecode, JDK 21 to build.** JDK 21 is what Claude Code cloud sessions provide, so local
  checks match CI exactly.
- **Version numbers:** release `X.Y.Z` → `versionCode = X*10000 + Y*100 + Z`, passed in by the release
  workflow. All other builds use `git describe --tags --long` (e.g. `1.2.0-3-gabc1234`), falling back to
  `0.0.0-dev`, so a dev build can never be mistaken for a release. Version logic lives in
  `scripts/release_version.py` (unit tested) and `app/build.gradle.kts`.
- **Debug builds use `applicationIdSuffix = ".debug"`** so a pull request's APK installs next to the
  released app instead of conflicting with it.
- **Releases are GitHub Releases with a signed APK**, published by `gh release create` (preinstalled
  on runners) instead of a third-party action, which means one fewer dependency with write access.
  The R8 mapping file is attached to each release.
- **Signing secrets** are base64 and decoded by a tolerant, tested script (`scripts/decode_keystore.py`)
  that never prints the secret. The key password is checked with a small Java program
  (`scripts/VerifySigningKey.java`), because `keytool` ignores the key password of PKCS12 keystores and
  the build would otherwise fail later with an unclear error.
- **Caching:** `gradle/actions/setup-gradle` with `cache-provider: basic` (MIT licensed). The default
  "enhanced" provider is closed source under separate terms of use.
- **CI artifacts:** the debug APK is uploaded unzipped (`archive: false`) so it can be installed on a
  phone straight from the download.
- **Languages:** English (default) + Dutch, as a working example of translations. `generateLocaleConfig`
  makes the app appear under Android's per-app language settings.
- **Persistence:** DataStore when needed (version in the catalog, not added as a dependency yet). No
  network, analytics or crash-reporting libraries.
- **No instrumented/UI tests** yet: they need an emulator in CI (slow, flaky). Logic is kept in plain
  Kotlin and unit tested instead. Manual device checks are listed in `docs/testing.md`.
- **No lint baseline:** the template has zero lint issues.
- **Play Store upload** is prepared but commented out in `release.yml` until a Play Console app and
  service account exist.
