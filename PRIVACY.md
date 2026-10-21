# Privacy policy

_Last updated: 2026-10-21 (Connect to Notion)_

Noter (listed on the Play Store as Noter for Notion) shows and edits notes in **your own Notion workspace**. It has no server that keeps your data
(only a small sign-in helper that stores nothing, see below), no analytics, no advertising and no
crash reporting.

## What the app sends, and where

- The app talks to **Notion's API** (`api.notion.com`), with the personal access token you
  make yourself in Notion (or the key of an integration you made). With a personal access token it
  can read the pages and databases you can see in Notion; with an integration's key only the ones
  you shared with it. If you add tokens for several workspaces (Settings › Workspaces), each
  is used only for that workspace's pages, and searches ask every workspace. It reads the notes you open and the lists you show (Browse searches by
  title), and writes the changes you make (titles, properties, note content, moving a note to
  Notion's trash).
- **Noter's sign-in helper**, only when you use **Connect to Notion** (OAuth) instead of pasting a
  token. Notion requires a secret for that sign-in that can't be kept inside an app, so a small
  helper run by the developer on Cloudflare Workers (`noter-sign-in.wim-d57.workers.dev`) adds it. When you allow access in Notion,
  Notion sends you to the helper, which asks Notion for your tokens and passes them straight back
  to the app, sealed so that only your phone can read them. Later, when Notion asks for a new
  token, the app sends the helper its refresh token and gets the new tokens back. The helper
  stores nothing and keeps no logs of tokens; like any website, Cloudflare sees your IP address.
  Pasted tokens never go to the helper.
- **Images** in your notes (header images, image blocks and page icons that are pictures) and your own Notion profile
  picture (shown on Home, when Notion gives its link) are downloaded straight from where
  Notion keeps them: Notion's own file storage for images uploaded to Notion, or the website an
  image links to. Those websites see an ordinary image request from your phone (its IP address),
  as a browser would; nothing about you or your notes is sent with it. Files, videos and links
  in a note are only opened (in your browser) when you tap them.
- **Links you share to the app**: when you share a web link to Noter from another app, Noter
  downloads that one page from its website, once, to copy its text and images into the new note
  (the images themselves stay on that website, and Notion and your phone load them from there).
  The website sees an ordinary request from your phone (its IP address), as a browser would; no
  cookies, nothing about you or your notes is sent with it. Nothing is downloaded for text without
  a link, and no link is ever opened without you sharing it.
- Nothing else leaves your phone. Notion's own privacy policy applies to the data in your
  workspace: <https://www.notion.so/notion/Privacy-Policy-3468d120cf614d4c9014c09f6adc9091>.

## What the app stores on your phone

- **Your tokens** (with each workspace's name, whether it is a personal access token, and the link to your profile picture), and which workspaces you folded away on Home, encrypted with the Android Keystore. For Connect to Notion also its refresh token and Notion's ID
  of the workspace, and during a sign-in a one-time key to open what the helper sends back. They
  are never included in a backup.
- **A copy of your notes** (titles, properties and content of the notes and pages you have seen),
  the list of databases and pages Home shows (their names and icons), which workspace each page and
  database is in (marked with a short code made from the token, not the token itself), and the list of changes that
  still have to be sent to Notion, so the app opens instantly and works offline. This copy is
  never included in a backup; Notion is the real copy.
- **Images you have seen**, in the app's cache folder (up to 100 MB), so they don't have to be
  downloaded again. Android may clear this folder when space runs low; it is never backed up.
- **Your settings**: per database, its filters, sort and what its cards show; the databases you
  pinned (their IDs, names and icons), pinned notes and pages, your own note order, which properties are shown (and in which order) per database,
  theme and dynamic colour. Only these settings may be included in
  Android's own backup (to your Google account, if you turned that on) and in a transfer to a new
  phone.

## Permissions

- **Internet**, to reach Notion's API and the sign-in helper, download the images in your notes and download a page
  you share to the app. No other permissions.

## Deleting your data

- *Settings › Workspaces › Remove* removes that workspace's token; removing the last one also
  removes the copy of your notes.
- Uninstalling the app removes everything it stored on your phone.
- To stop the app's access to Notion completely, revoke the token at
  <https://www.notion.so/developers/tokens> (or delete the integration, if you used one). For
  Connect to Notion, remove Noter in Notion under **Settings › Connections**.

If this changes, this file will be updated **before** the change is released.

Questions: open an issue in this repository.
