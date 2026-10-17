# Privacy policy

_Last updated: 2026-10-17 (notebooks, personal access tokens, page icons)_

Noter for Notion shows and edits notes in **your own Notion workspace**. It has no server of its
own, no analytics, no advertising and no crash reporting.

## What the app sends, and where

- The app talks only to **Notion's API** (`api.notion.com`), with the personal access token you
  make yourself in Notion (or the key of an integration you made). With a personal access token it
  can read the pages and databases you can see in Notion; with an integration's key only the ones
  you shared with it. It reads the notes you open and the lists you show (Browse searches by
  title), and writes the changes you make (titles, properties, note content, moving a note to
  Notion's trash).
- **Images** in your notes (header images, image blocks and page icons that are pictures) are downloaded straight from where
  Notion keeps them: Notion's own file storage for images uploaded to Notion, or the website an
  image links to. Those websites see an ordinary image request from your phone (its IP address),
  as a browser would; nothing about you or your notes is sent with it. Files, videos and links
  in a note are only opened (in your browser) when you tap them.
- Nothing else leaves your phone. Notion's own privacy policy applies to the data in your
  workspace: <https://www.notion.so/notion/Privacy-Policy-3468d120cf614d4c9014c09f6adc9091>.

## What the app stores on your phone

- **Your token** (and whether it is a personal access token), encrypted with the Android Keystore. It is never included in a backup.
- **A copy of your notes** (titles, properties and content of the notes and pages you have seen) and the
  list of changes that still have to be sent to Notion, so the app opens instantly and works
  offline. This copy is never included in a backup; Notion is the real copy.
- **Images you have seen**, in the app's cache folder (up to 100 MB), so they don't have to be
  downloaded again. Android may clear this folder when space runs low; it is never backed up.
- **Your settings**: your notebooks (their names, icons, database or page, their filters,
  sort and what their cards show), pinned
  notes, your own note order, which properties are shown (and in which order) per database,
  theme and dynamic colour. Only these settings may be included in
  Android's own backup (to your Google account, if you turned that on) and in a transfer to a new
  phone.

## Permissions

- **Internet**, to reach Notion's API and download the images in your notes. No other
  permissions.

## Deleting your data

- *Settings › Connection › Remove token* removes the token and the copy of your notes.
- Uninstalling the app removes everything it stored on your phone.
- To stop the app's access to Notion completely, revoke the token at
  <https://www.notion.so/developers/tokens> (or delete the integration, if you used one).

If this changes, this file will be updated **before** the change is released.

Questions: open an issue in this repository.
