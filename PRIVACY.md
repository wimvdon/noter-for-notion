# Privacy policy

_Last updated: 2026-10-16_

Noter for Notion shows and edits notes in **your own Notion workspace**. It has no server of its
own, no analytics, no advertising and no crash reporting.

## What the app sends, and where

- The app talks only to **Notion's API** (`api.notion.com`), with the API key of the Notion
  integration you create yourself. It reads the databases you shared with that integration and
  their pages, and writes the changes you make (titles, properties, note content, moving a note to
  Notion's trash).
- Nothing else leaves your phone. Notion's own privacy policy applies to the data in your
  workspace: <https://www.notion.so/notion/Privacy-Policy-3468d120cf614d4c9014c09f6adc9091>.

## What the app stores on your phone

- **Your API key**, encrypted with the Android Keystore. It is never included in a backup.
- **A copy of your notes** (titles, properties and content of the notes you have seen) and the
  list of changes that still have to be sent to Notion, so the app opens instantly and works
  offline. This copy is never included in a backup; Notion is the real copy.
- **Your settings**: which databases you added (their names, icons and filter property), pinned
  notes, your own note order, theme and dynamic colour. Only these settings may be included in
  Android's own backup (to your Google account, if you turned that on) and in a transfer to a new
  phone.

## Permissions

- **Internet**, to reach Notion's API. No other permissions.

## Deleting your data

- *Settings › Connection › Remove key* removes the API key and the copy of your notes.
- Uninstalling the app removes everything it stored on your phone.
- To stop the app's access to Notion completely, delete the integration at
  <https://www.notion.so/my-integrations>.

If this changes, this file will be updated **before** the change is released.

Questions: open an issue in this repository.
