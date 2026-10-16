# Testing

## Automatic (CI, on every pull request and push to `main`)

| Check | Command | Catches |
|---|---|---|
| Release script tests | `python3 -m unittest discover -s scripts/tests` | Version maths, keystore-secret decoding |
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
```

### What the unit tests cover
| Test | Covers |
|---|---|
| `logic/DocumentEditorTest` | Enter, Backspace, Markdown shortcuts (Notion's), dividers, code blocks, numbering |
| `data/notion/BlockCodecTest` | Reading and writing every editable block type; images, files, links, tables and linked pages read-only |
| `data/model/NotionFilesTest`, `logic/ReadOnlyBlocksTest` | Expiring links to files Notion hosts; file names and links of read-only blocks |
| `data/notion/InlineMarkdownTest` | Bold, italic, strikethrough, code and links ⇄ Notion rich text |
| `data/notion/PropertyCodecTest` | Reading and writing page properties |
| `data/sync/ContentDiffTest` | The block changes sent to Notion for an edit |
| `data/sync/OutboxTest` | The queue of unsent changes (merging, renaming new notes) |
| `data/sync/SyncPolicyTest` | When to refresh, and merging refreshed notes |
| `data/sync/SaveTimingTest` | How soon edits are saved while typing (within Notion's rate limit) |
| `data/NotesRepositoryTest` | The repository against a fake Notion server (`FakeNotion`, the JDK's own HTTP server): new notes stay open while Notion creates them, nothing is sent for a note in the trash, table rows, expired image links |
| `data/store/CacheStoreTest` | On-phone copies survive a restart |
| `logic/NoteOrderingTest`, `logic/ChipFilterTest` | Pins, custom order, filters |
| `logic/PropertyLayoutsTest` | Which properties are shown, collapsed and on the card, and in which order |
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
