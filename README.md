# Noter for Notion

A note app for Android in the style of Google Keep, with your notes kept in **Notion**.
A database (following one of its Notion views) or a page (its subpages are the notes) becomes a
**notebook** in the navigation, with its own filters and sort on the phone; **Pinned** shows the
notes you pinned from all of them, and **Search** finds your notes and anything else in Notion.

- Notes as cards with a preview of their content, pinned notes on top, drag and drop to reorder.
- A note's content is edited like in Notion: text, three heading sizes, bulleted, numbered and
  to-do lists, toggles, quotes, callouts, code and dividers, with Notion's Markdown shortcuts
  (`# `, `- `, `[] `, `> `, `" `, ```` ``` ````, `---`) and bold, italic, strikethrough, code and links.
- The note's properties (select, status, dates, people, relations, …) are edited above the content.
- Opens instantly and works offline: the app keeps a copy on the phone and sends changes to Notion
  about once a second while you type, within Notion's limits.
- Material 3 (light/dark, wallpaper colours), English and Dutch, Android 12+.

What the app sends and stores: [`PRIVACY.md`](PRIVACY.md). Rules for Claude Code sessions:
[`CLAUDE.md`](CLAUDE.md).

---

## Getting started

1. Install the app (see below) and open it.
2. In Notion, open <https://www.notion.so/developers/tokens> and make a **personal access token**:
   give it a name, turn on **Notion API**, choose your workspace and an expiry date, and copy it.
   It acts as you, so the app sees the pages you see; there is nothing to share with it.
3. In the app: **Settings › Connection**, paste the token and tap **Test and save**. Then tap
   **Search**, open a database and tap **Add as notebook** (or **Settings › Notebooks › Add
   notebook**).

When the token expires, make a new one and paste it in the same place. An older **internal
integration** key works too, but then the app only sees the pages you shared with that
integration and can't add pages at the top of your workspace; Settings says so.

---

## Installing the app on your phone

### From a GitHub Release (the normal way)
1. On your phone, open the repository on GitHub and tap **Releases** (or go to `…/releases/latest`).
2. Under **Assets**, tap `<app>-<version>.apk` to download it.
3. Open the downloaded file. The first time, Android asks you to allow installing apps from your
   browser ("Install unknown apps"). Allow it, go back, and tap **Install**.
4. Updates: install the newer APK the same way. Your data is kept.

### Testing a pull request
Every pull request builds a **debug APK**:
1. Open the pull request → **Checks** → **CI** → scroll to **Artifacts**.
2. Download `<app>-<version>-debug.apk` and install it like above.

The debug app ("Noter Dev") installs **next to** the released app, with its own data and its own
API key. Uninstall it when you're done testing.

---

## Maintaining the app

The app was made from the android-app template (`wimvdon/android-app`). The signing key, the four
repository secrets and the GitHub settings are set up; the sections below describe them for when
something needs to change.

### Signing key

Releases are signed with your own key, stored as four **repository secrets**. You need a
computer with Java once (any JDK, or Android Studio, which includes one).

**Create the keystore.** Pick a strong password and use it for both prompts (with a PKCS12
keystore, the key password *is* the keystore password).

macOS / Linux (Terminal):
```sh
keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```
Windows (PowerShell). If `keytool` is not found, use Android Studio's copy:
`& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"` instead of `keytool`.
```powershell
keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 `
  -alias upload -keyalg RSA -keysize 4096 -validity 10000
```
It asks for your name etc. Any values are fine; they are visible in the APK's certificate.

**Encode it as base64** (this copies it to the clipboard):

| System | Command |
|---|---|
| Windows (PowerShell) | `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\upload.jks")) \| Set-Clipboard` |
| macOS | `base64 -i upload.jks \| pbcopy` |
| Linux | `base64 -w 0 upload.jks \| xclip -selection clipboard` (or `base64 -w 0 upload.jks > upload.b64` and copy the file's content) |

**Add the secrets.** In the repository: **Settings → Secrets and variables → Actions →
New repository secret**, four times:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the base64 text from the clipboard |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | `upload` (the `-alias` you used) |
| `ANDROID_KEY_PASSWORD` | the key password (same as the keystore password for PKCS12) |

Or with the GitHub CLI: `gh secret set ANDROID_KEYSTORE_BASE64 < upload.b64`, and
`gh secret set ANDROID_KEYSTORE_PASSWORD` (it prompts for the value), and so on.

The release workflow is forgiving about copy/paste mistakes in the base64 secret (line breaks,
quotes, missing `=` and so on). If something is still wrong, the run explains what without showing
the secret. It also checks the passwords and alias before building.

### Backing up the key

Store **`upload.jks` and both passwords** in at least two safe places, e.g. a password manager
(as an attachment) and an encrypted USB stick. Do **not** put them in the repository, email or
chat. GitHub secrets cannot be read back, so they are not a backup. Then delete `upload.b64` and
any other copies on your computer that aren't backups.

### Making a release

1. Make sure `main` contains what you want to release and CI is green.
2. **Actions → Release → Run workflow**, branch `main`, choose:
   - `patch` (1.2.**3**): fixes, small changes
   - `minor` (1.**3**.0): new features
   - `major` (**2**.0.0): big changes
   - `none`: only build a downloadable APK (no version, no release)
3. When it finishes, the new release (with the APK) is on the **Releases** page.

The first `patch`/`minor`/`major` release becomes `0.0.1` / `0.1.0` / `1.0.0`.
Pushing a tag (`git tag v1.2.3 && git push origin v1.2.3`) also releases.

The workflow **refuses** to publish when you're not on `main`, when the tag already exists, when the
version would not be higher than the last one, or when nothing changed since the last release.

Each release also includes `<app>-<version>-mapping.txt`. You need it to read crash stack traces
from release builds (see [`docs/testing.md`](docs/testing.md)).

**Version numbers:** a release `X.Y.Z` gets `versionCode = X*10000 + Y*100 + Z` (so `Y` and `Z`
must stay below 100). All other builds are named after `git describe`, e.g. `1.2.0-3-gabc1234`
(3 commits after 1.2.0), or `0.0.0-dev` without tags, so they never look like a release.

### Google Play (optional)

`release.yml` contains a commented-out `play-internal` job that builds an AAB and uploads it to the
**internal testing** track. To use it:
1. Create the app in the [Play Console](https://play.google.com/console) with the same
   application ID, and upload the first AAB by hand (`./gradlew bundleRelease` with the signing
   setup), as Play requires. Opt in to Play App Signing, using your key as the *upload key*.
2. In Google Cloud, create a service account, create a JSON key for it, and in the Play Console
   (**Users and permissions**) invite its e-mail with the "Release to testing tracks" permission.
3. Add the JSON as the repository secret `PLAY_SERVICE_ACCOUNT_JSON`.
4. Set `PACKAGE_NAME` in the job and uncomment it. It runs after every published release.

### Licence

`LICENSE` says **all rights reserved**: nobody may use, copy or share the code without your
permission. This is the safe default, also for a public repository. Update the name and year in
`LICENSE` for each new app.

To let others reuse the code, replace the whole text of `LICENSE` with a standard open-source
licence from [choosealicense.com](https://choosealicense.com/) (fill in name and year), for example:
- **MIT**: anyone may do anything with the code, as long as they keep your copyright notice.
- **Apache-2.0**: like MIT, plus an explicit patent grant; common for Android code.

Or ask Claude Code: *"Switch this app to the MIT licence."* Add a dated entry to
`docs/decisions.md` when you change it.

### GitHub settings

Apply these once per repository (**Settings**):

1. **General → Template repository**: tick it, but only for the template itself, not for apps made from it.
2. **General → Pull Requests**: allow **squash merging** (you may untick merge commits and
   rebase merging) and tick **Automatically delete head branches**.
3. **Branches → Add branch ruleset** (or classic *branch protection rule*) for `main`:
   - Require a pull request before merging.
   - Require status checks to pass → add **`Test, lint and build`** (the CI job; it appears in the
     list after CI has run once).
   - Block force pushes.

   ⚠️ Branch protection/rulesets on **private** repositories need **GitHub Pro** (or Team).
   On a free account they work only for **public** repositories. Without it, CI still runs and
   shows ✅/❌ on every pull request, but GitHub won't *stop* you merging a red one, so only
   merge when the checks are green.

The same settings with the [GitHub CLI](https://cli.github.com/) (replace `OWNER/REPO`):
```sh
gh repo edit OWNER/REPO --template            # template repository only
gh repo edit OWNER/REPO --enable-squash-merge --delete-branch-on-merge
gh api -X PUT repos/OWNER/REPO/branches/main/protection --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["Test, lint and build"] },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 0 },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```

---

## For developers

Requirements: JDK 21 and the Android SDK (or Android Studio). Common commands:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug   # what every change must pass
./gradlew assembleRelease                              # minified release build (unsigned without keystore.properties)
python3 -m unittest discover -s scripts/tests          # tests for the release scripts
./gradlew installDebug                                 # install on a connected phone/emulator
```

Docs: [`docs/architecture.md`](docs/architecture.md) · [`docs/testing.md`](docs/testing.md) ·
[`docs/decisions.md`](docs/decisions.md) · [`PRIVACY.md`](PRIVACY.md)
