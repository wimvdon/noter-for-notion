# Decision log

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-10-23: No detection of Notion's People directory (#92 reopened)
Two attempts to keep Notion's People directory off Home (by its built-in properties "Membership Type" and "Email", first from the search results and then by asking each data source once) didn't work on the owner's phone, so both were taken out again (#97, #98). Notion's API documentation and changelog have no mark for the People directory: the API returns it as an ordinary data source, and integrations that use it (for example Thunderbird's notion-scripts) have the user configure its ID. Notion's own MCP connector refuses it as "a Notion People page", so Notion can tell it apart internally, but the public API doesn't say how. The owner decided to leave it as it is: the People directory shows like any other database.

## 2026-10-22: The profile picture only for one person
Home's Settings button showed the main token's person's picture. With workspaces of several people (or a connection's token, named after the connection) that picture can be someone else's. The owner suggested a cogwheel or sortable workspaces with the top one's picture, and left the choice. Now the picture shows only when every workspace is the same person's; otherwise the button is the cogwheel. Rejected: sorting workspaces to pick whose picture (an order to manage for a small icon) and always the cogwheel (loses the picture for the usual case of one person).

## 2026-10-22: Pinned items as launcher shortcuts (#76)
The owner asked for the pinned items in the menu of a long press on the app icon. Android's dynamic shortcuts (`ShortcutManagerCompat` from androidx.core, already a dependency; no permission) show the pinned databases and then the pinned notes and pages, in Home's order, at most four (what launchers show). A shortcut's icon is the item's Notion emoji drawn on the launcher icon's white, else a page or database symbol; a picture icon from Notion isn't downloaded for it. Rejected: a widget (much more code for the same reach) and static shortcuts to New note (the owner asked for the pins).

## 2026-10-22: No trash in a card's long-press menu (#91)
A long press on a card opened Pin and Move to trash. The owner removed the trash there: a long press is also how a card is dragged, so a slip could reach it, and deleting is better done from the note itself (its ⋮ menu, which still asks first). The long-press menu keeps Pin or Unpin.

## 2026-10-22: Home finds old pages too
The owner noticed pages missing from Home. Home asked Notion's search for the 200 most recently edited things, but the search can't leave out the notes in databases, so a busy database pushed older pages out. Notion's API has no "pages at the top" or "two levels" request: only the search, and a page's own blocks, which list every subpage and database right inside it. So Home now reads the search further (up to 2000 results per workspace, 20 requests, paced; most workspaces end sooner), which finds the top-level pages of most workspaces, and then asks the newest 50 top-level pages for what is right inside them, adding up to 50 subpages or databases the search didn't reach. It runs at start and on pull-to-refresh, in the background, while Home shows the kept list. Rejected: reading the whole search every time (thousands of requests in a big workspace) and asking every page for its subpages (one request per page, every level).

## 2026-10-22: A page's icon and title in the top bar once scrolled away
After databases got a tappable icon and name in the top bar, the owner asked for the same on pages. A page's bar stays empty while its own icon and title are on screen (two titles at once would be noise), and shows them once they have scrolled away, as Notion does; tapping the icon opens the emoji sheet, tapping the title scrolls back up and puts the cursor at the end of the title, where a page is renamed. Rejected: always showing the title in the bar (a repeat of the big title right below it) and editing the title in the bar itself (too small to type in, and the bar is for going back).

## 2026-10-22: Rename a database and change its icon
Pages could be renamed and given an icon in the app, databases not. The owner wanted it without making the database screen busier, so both are in its ⋮ menu: Rename opens a dialog with the name, the icon uses the emoji sheet pages already have. Both go to Notion at once, like making a database (a database has no copy here to edit offline, and a failure shows in a snackbar). Notion keeps a name and icon on the database and on each data source; Noter's lists show the data source's for a database with one, so both change then; with several data sources only the database's (each tab keeps its name). As on a page, tapping the icon or the name in the app bar does the same, so both work the same way in both places; the menu items keep it findable. Rejected: an editable title in the screen itself, as on a page (the header there is the notes' header and scrolls away), and queueing the change (more code for something rarely done offline).

## 2026-10-22: No sync button in Settings
Settings had a line saying whether everything was saved in Notion, with Sync now. The owner found it of little use: syncing happens where the notes are. Each screen has its own sync button, shown while changes wait or the phone is offline, changes are sent when the app comes to the front, and Sync now only refreshed the databases of the pinned notes. So the line and the button are gone. Rejected: keeping only the line (it repeats what the screens' sync button says).

## 2026-10-22: Connect to Notion as the button, tokens under Advanced
The owner wanted the usual way in front: Workspaces now has a **Connect to Notion** button that goes straight to Notion, and a text button **Advanced** next to it that opens the dialog with the two tokens (personal access token and connection token), as before with what each is for. This saves a step for most people and keeps the tokens, which need a trip to Notion's developer pages, out of the way. A build without Connect to Notion shows Add workspace instead, opening the same dialog. Rejected: keeping the three ways side by side in one dialog (one more tap for the common case).

## 2026-10-21: Review before going live: no lost or doubled content
A review of what could duplicate or delete pages and blocks found three cases, now fixed. (1) Typing in a note while its newer version was still coming from Notion saved the edits on the older copy: blocks added in Notion meanwhile were deleted and blocks changed there set back. Now the edits go on top of the newer version (`logic/ContentMerge.kt`, by block key: changed, deleted and added here, the rest as Notion has it). Rejected: blocking typing until Notion answers (a wait at every open, long on a slow connection). (2) A new page whose answer was lost (timeout, server error) was made again on the next try, giving two pages. Now the first try is remembered (`PendingOp.CreatePage.sentAt`) and a later try first looks for a page with the note's title made in the same place since then (`NotesRepository.findCreated`), and takes it. Rejected: never trying again (the note would stay on the phone only). (3) More than 100 new blocks go in several requests; when a later one failed, the blocks of the earlier ones were sent again with the next edit. Now such a failure is treated like a lost answer: the content is compared with Notion's before anything is sent again (`BlockMatch`).

## 2026-10-21: Each workspace added once
The owner noticed the same workspace can be added several ways. Two ways for one workspace rarely help (a personal access token already sees everything; choosing more pages in Notion does the rest) and cost: every search ran twice, pages could be listed twice, and an edit could go with the token that sees less (and show in Notion as made by the connection). So each Notion workspace is added once, told apart by the workspace ID Notion gives for every kind of token. Adding it again replaces how it is connected, keeping its place, pins and what is known about its pages, except that a way that sees less never replaces one that sees more (the owner's rule: a personal access token, then Connect to Notion, then a connection token); then Settings says it is already added with more access and to remove it first. Duplicates from before are cleaned up at start, the one that sees most staying. Search results are also made unique per page, as a safety net. Rejected: allowing duplicates and marking the weaker ones in Settings, with Home merging their sections (more code for a case that rarely helps, and the edit and double search problems stay), and asking before replacing (it can't remove a workspace, and switching back is adding it again).

## 2026-10-21: One Add workspace, three ways
The owner wanted any number of workspaces, each added its own way: Connect to Notion (quickest, choose the pages in Notion), a personal access token (full access, nothing to choose) or a connection token (for a workspace one is a guest in). Settings had a Connect button, a token field and two blocks of help always on screen. Now Workspaces lists the workspaces with one Add workspace button, which opens a dialog with the three ways as radio buttons, each with what it is best for (`DESIGN.md`: a choice with long labels opens a dialog with radio buttons); a token then gets its own step with the link to make one and the field to paste it. Several Connect to Notion workspaces already worked (Notion's page has a workspace picker; connecting one again replaces its token). The connection's text says a member shares the pages, since a guest can't make a connection. The owner checked that Connect to Notion doesn't replace it: only a full member can add a workspace to a public connection, so a guest still needs a member's connection token. Rejected: three buttons side by side (long labels, no room for what each is for) and a separate screen (one more place to go back from).

## 2026-10-21: Connect to Notion (OAuth) with a stateless sign-in helper
The owner asked for proper OAuth for the first set-up, keeping tokens for full access and other workspaces. Notion's OAuth needs the connection's client secret to trade the code for tokens and to refresh them, and a secret in an APK isn't secret, so a small helper on Cloudflare Workers (`worker/noter-sign-in.mjs`, free tier, no dependencies, pasted into Cloudflare's editor) holds it. The owner approved it as the app's second network destination (`PRIVACY.md`). The helper stores nothing: Notion redirects to it, it trades the code and passes the tokens straight back to the app. So that another app catching the link back learns nothing (a custom scheme can be claimed by any app, and Notion's OAuth has no PKCE), the app sends a one-time public key in `state` and the helper seals the tokens for it (ECDH P-256, HKDF-SHA-256, AES-256-GCM: WebCrypto in the helper, the JDK in the app, no new library). The link back uses the app ID as scheme, so the debug app ("Noter Dev") and the released app don't catch each other's. Notion rotates the refresh token at every refresh and documents no expiry for access tokens, so the app refreshes only when Notion answers 401, one refresh at a time, and the workspace keeps its ID on the phone. Rejected: keeping the secret in the app; the helper storing tokens or sync data (an encrypted store needs a key the user carries to work on a new phone, else the developer can read it, and it makes the developer responsible for user data, Play's account deletion rules and a service to keep running; settings to sync can go in a page in the user's own workspace instead); Android App Links on the helper's domain (Chrome doesn't reliably hand redirects to them, and the debug build's signing key differs); a custom tab library (a new dependency; the browser works). Until the owner has made the public connection and the helper, `SignInConfig` is empty and Settings only offers tokens, as before. (Filled in the same day: Noter's public connection and `noter-sign-in.wim-d57.workers.dev`.)

## 2026-10-21: New databases as in Notion

After trying it, the owner asked for Notion's own defaults instead of the app's choice of starter properties ("Done" isn't one), and for several properties of a kind. A new database now starts like one made in Notion (Name and Tags), and Add property offers every kind Notion's API makes from a name alone, named as Notion names a new one ("Checkbox", "Text 2"). A toast after it opens says that its properties and views are edited in Notion. Rejected: status (the API can't make one), formulas, relations and rollups (they need an expression or another database), and editing options or formats in the app.

## 2026-10-21: Home newest first (#70); making databases (#71)

Home's tree was by title because Notion's API doesn't give the sidebar's order; the owner found that it didn't feel like Notion's order anyway and asked for the most recently updated first. Both levels now are, and an entry counts as edited when something right inside it was (Notion doesn't change a page's time when only a page inside it changes), so busy pages stay on top. New databases can be made in a page or at the top of a workspace, with a name and a few starter properties of kinds the app already edits (Tags, Date, Done, Link); everything else (status, relations, formulas, views) stays in Notion. A database is made at once rather than queued like a note: it has no local copy to edit meanwhile, and Notion's answer says which data source to open. Rejected: choosing any property type (a full schema editor is much more screen for what Notion does better), a status property (Notion's API can't create one), and queueing it offline.

## 2026-10-18: Share to Noter, with the page's content (#63)

Text and links can be shared to the app; Home asks where to save them (the places + offers) and the note opens. As the owner asked, a shared link is downloaded so its text and images go into the note, like Notion's own app does: this is the first request outside Notion's API (besides images), described in `PRIVACY.md`, and only ever for a link the user shared. The page is read by a small reader of the app's own (`logic/WebArticles.kt`) instead of an HTML library (no new dependency); pages it reads badly still have the link as a bookmark at the top, which Notion shows with its preview. Images are linked, not uploaded to Notion (uploading needs Notion's file upload API and more requests; the website keeps them). The content is sent with the page in one request, which also works offline (the note is made later). Rejected: a separate share screen (Home's sheet already lists the places), asking Notion to read the link (its API has no such thing), and a reader mode library such as Readability (a dependency, and written for browsers).

## 2026-10-18: Home's databases and pages as a tree (#64)

Home's list of the 30 most recently edited databases and pages becomes a tree of two levels, like Notion's sidebar: on top what isn't inside another page the app can see, below each (opened with a chevron) the databases and pages right inside it. Up to 200 of them (one search) are kept instead of 30, and the top level shows 30 at a time with "Show more", so a workspace with dozens of pages stays short. Both levels are by title, since Notion's API doesn't give the sidebar's own order. Where something is comes from Notion's `parent`; a page inside a block of another page (a column, a toggle) shows at the top. Search and + keep the most recently edited order, and with several workspaces the parts of the + sheet fold away like Home's. Rejected: tabs per workspace in + (they hide the other workspaces' databases, and with two or three workspaces a folded part is as quick), asking Notion for each page's subpages (one request per page, slow at Notion's three requests a second; the search already says where each page is), and keeping which rows are open across restarts (closed is the calm default).

## 2026-10-18: Workspaces in one place, shown everywhere; one Search list (#60)

OAuth was considered again to avoid pasting tokens, and rejected: Notion still needs a server for its client secret, and guests can't approve a public integration either (Notion's own sign-in refuses them), so it wouldn't help the case that started #58. Tokens stay, without the step-by-step help: Settings has one Workspaces section listing every token (the main one is no longer special to the person), with links to make a personal access token (a workspace of one's own: sees everything, can add pages at the top) or a connection (pages someone shared). Since the app now mixes workspaces, it says where things are: which workspace answered for a page, database or data source is kept on the phone (a short hash of the token, not the token), Home splits its list per workspace with parts that fold away, the + sheet has a part per workspace (a page at the top goes with that workspace's token; it isn't offered for a connection, and when Notion refuses anyway the message says to check the token), and Search's rows name their workspace. Search (#60) is one list: where a page is kept made no difference to the person searching, so the notes on the phone and Notion's results are mixed, best title matches first, with chips for pages, databases and workspaces. Rejected: pinned notes per workspace on Home (pins are one's own mix and are dragged among each other), and a workspace label on every card (the card's database icon already says where it is).

## 2026-10-18: Tokens for other workspaces (#58)

A personal access token belongs to one workspace, and guests and restricted members can't make one ([Notion](https://developers.notion.com/guides/get-started/personal-access-tokens)), so pages in a workspace someone was invited to never showed up. Settings now has Other workspaces: tokens for other workspaces next to the main one, each named after its workspace and removable on its own. To reach them a member makes a personal access token in that workspace; a guest needs a member to share the pages with a connection and hand over its token (Settings says so). The routing lives in `NotionClient` only: each request about an object goes with the token that worked for that ID before, else every token in turn until one isn't answered "not found"; search and people ask every workspace. Notion's IDs are unique across workspaces, so caches, pins and the outbox needed no change, and with one token the app behaves exactly as before. The ID-to-token map is kept in memory: after a restart the first request about another workspace's object costs one extra 404. Rejected for now: an account ID on every cached note and queued change (much more code for one saved request), deleting a removed workspace's notes from the phone (the phone can't tell which notes were whose before Notion has been asked), and OAuth (still needs a server).

## 2026-10-18: Database links, Cards in the app bar, pickers that hold still, Select text (issues #52 to #56)

- **A database's links use the database (#54).** Share sent `notion.so/` plus the *data source's* ID, which Notion doesn't open. A data source's JSON names its database (`parent.database_id`), now kept in `DatabaseSchema.databaseId`; a schema on the phone from before gets it at the next full refresh, and until then the database is taken from the last search or asked from Notion once when the screen opens (`NotesRepository.databaseOf`, one request). The ⋮ menu also has Open in Notion with the same link. Rejected: Notion's `url` field of the database (needs `GET /databases` for every data source just for a link).
- **Cards in the app bar (#55)** as the third button after Filters and Sort, with the Tune icon of a page's properties button, since both choose which properties show. The ⋮ menu keeps Pin, Open in Notion and Share.
- **Pickers hold still (#52).** The people picker listed the selected people first and re-sorted on every tick, so a row jumped away under the finger. Every picker (options, people, linked pages) now puts what is selected first when it opens and then keeps its order until it is closed (`logic/ChoiceOrder.kt`).
- **Select text (#53).** Each line of the editor is its own text field, and a selection can't go past one. Rather than rewrite the editor as one text field, the ⋮ menu has Select text: the note as plain text in one selection area (`SelectTextView`), where a selection runs across lines and the system's menu copies or shares it. Nothing can be changed there. Selecting whole lines (toolbar) stays for deleting several lines. Rejected for now: one text field for the whole note (a rewrite of the editor and its sync).
- **Settings (#56):** the sentence saying the token "acts as you" is gone (it confused more than it told), and the three steps have numbered circles like the guide in Snoozer.

## 2026-10-18: Seven small fixes to Home, filters and the note editor (issues #44 to #50)

- **Profile picture on Home (#50).** Notion's `GET /users/me` already tells who a personal access token acts as; the owner's user object has an `avatar_url` when the person has a picture (a connection's token is a bot of the workspace and has none, so it keeps the cogwheel). It is kept next to the token (`ApiKeyStore.avatarUrl`, encrypted, never backed up) and asked again at each app start, since the person can change it: one extra request per start. Coil shows it in a circle in place of the cogwheel, and falls back to the cogwheel while it loads or if it fails. No new network destination in kind: it is an image Notion gives a link to, like the images in notes (`PRIVACY.md` says so now). Rejected: listing the workspace's users to find the picture (a personal access token can't), and keeping a copy of the picture on the phone beyond Coil's own cache.
- **Pinned databases can be reordered (#44)** by a long press and drag, as the cards already are, so there is no handle or arrows on Home; a long press without moving opens the row's menu (Unpin). Screen readers get Move up, Move down and Unpin. The grid draws the rows itself (`NoteGrid`) so one drag code serves both. The order is the order of `AppSettings.pinnedDatabases` (new pins still go on top). Rejected: a separate Reorder mode with handles (clutter the owner didn't want).
- **Every filter can be reordered (#48).** The Filters panel only stored the properties that have a row of chips, so the order of the hidden ones was thrown away. `DatabaseSetup.filterOrder` keeps the order of all of them; `quickFilters` stays the shown ones.
- **Share (#45)** in the ⋮ menu of pages and databases: Android's share sheet with Notion's link (a page's own link; a database's is built from its ID, like the "Open in Notion" links of blocks). Rejected: sharing the content as text (that is what Copy on selected lines is for).
- **Properties button and the add-icon button take no line of their own (#49, #46).** The arrange button moved to the end of the title row; "Add icon" is a small grey label instead of a button.
- **Note editor (#47).** A new line used to be scrolled to the *top* of the list whenever it was the last visible one, which made the note jump when the keyboard was up; it is now scrolled just far enough to show above the keyboard (`logic/Reveal.kt`). Selecting several lines: each line is its own text field, so text can't be selected across them; instead a toolbar button starts a selection of whole lines (tap to select) with Select all, Copy (as Markdown) and Delete (asks first). Rejected: dragging a selection across lines (needs one text field for the whole note, a rewrite of the editor).

## 2026-10-18: Home and databases instead of notebooks

The owner redesigned the app: instead of setting up notebooks (Manage notebooks, Add notebook, a tab per notebook under a navigation bar), the user goes to the database itself. Home is now the only main screen: a large search bar, everything pinned (databases, pages and notes) and the databases and pages Notion's search lists as most recently edited, with a + button. A database keeps its filters, rows of chips, sort and cards on the phone as soon as they change (`DatabaseSetup` per data source, backed up like the notebooks were), so there is nothing to save. Any database, page or note can be pinned. Pages don't get a notebook view any more: a page opens in the editor, where its subpages are links.

Existing notebooks are converted when the settings are read (`LegacyNotebooks`): each database keeps its notebook's setup and is pinned on Home in the order of the tabs, a page's notebook becomes a pinned page, so nothing set up is lost. Gone with the notebooks: the navigation bar and its order setting, Manage notebooks and its icon picker (databases show Notion's own icon now), notebook names of the app's own (Notion's names are used), the check that notebooks follow data sources moved between databases (a pinned database is opened by its key and its tabs come from Notion each time) and the page notebook's sync of subpages and their page order. Home's list is kept on the phone so it shows offline; it is asked again when the app starts and on pull-to-refresh (one or two search requests, plus a request per database the first time per start, as Search already did).

Rejected: keeping notebooks next to pinned databases (two ways to do the same thing), a separate "recently opened" list kept by the app (Notion's own recently edited order needs nothing stored and also lists what was made elsewhere), and source chips above Home's pinned notes (the pinned databases are right above them).

## 2026-10-17: Edit a sub-notebook on its own

In a sub-notebook, Edit notebook opened the whole notebook's editor, with the cards of every sub-notebook one after another, so the owner had to look for the one they were in. The ⋮ menu now offers both, as the owner suggested: Edit “Notities” (the notebook: name, icon and every sub-notebook's cards, as before) and Edit “Development” (only that sub-notebook's cards, with the notebook it is part of under the title). Rejected: scrolling the whole editor to the sub-notebook (still a long sheet) and leaving out the notebook item (its name and icon would be out of reach from a sub-notebook).

## 2026-10-17: Same design rules as the sibling apps

A design review of all the apps found Noter doing some things its own way. Lists in your own order (Manage notebooks, Settings › Navigation bar) had up and down arrows while the Filters panel had drag handles: every reorderable list now has a drag handle at the end of the row, from the shared `Reorder.kt` (Move up and Move down stay as screen reader actions). The Filters panel's handle moved from the start to the end of the row to match. The navigation bar shows outlined icons for the destinations that aren't selected, as Tracker for Simkl does and Material describes. The note editor and Search keep a readable width (640dp) on wide windows (`Layout.kt`). Noter now also speaks French, German, Spanish and Italian, like the other apps. Rejected: keeping arrows for short lists (two ways to do the same thing across the apps).

## 2026-10-17: Notebooks follow data sources moved in Notion

The owner moved a database (Development) into another one (Notities) as a data source, and the app kept two notebooks. The app now asks Notion where each notebook's data source is (the data source's parent database) and which data sources that database has, when the app opens (at most every 15 minutes) and on pull-to-refresh: one request per data source and per database. The notebooks are then brought in line: the moved data source becomes a sub-notebook of the database's notebook and keeps its filters, sort and cards. A second notebook of the same data source (other filters) is left alone, since that may be on purpose. A data source Notion can't find (deleted, in the trash, or no access) isn't removed by itself, since it may only be a lost permission: its notebook says so, with a button to remove it and one to retry. Rejected: checking on every refresh (more requests for something that rarely changes).

## 2026-10-17: Pickers show new options at once

The owner opened a note and didn't see options made recently until going back and refreshing: the options a picker offers came from the database's properties as last downloaded (a full refresh). Now an option any note on the phone has is added to them straight away (also one just added on the phone, before it reaches Notion), and opening a note downloads its database's properties again in the background (one request), so options added in Notion show too. Rejected: downloading the properties each time a picker opens (a wait on every tap).

## 2026-10-17: Adding options to select and multi-select properties

The picker of a select or multi-select property has a "Search or add" field: a name that isn't an option yet can be added and is picked at once. Notion makes the option itself when the note is saved with a name it doesn't know (if the connection may edit the database), so it works offline and needs no extra request. Not for other properties: a status option can only be added by sending Notion the whole list of options, and an option missing from that list would be deleted from every note, which is too risky for a picker; people come from the workspace; a relation would need a new page in the other database (possible later). Names with a comma aren't offered (Notion doesn't allow them), nor a name that exists in another case.

## 2026-10-17: The navigation bar's order is a setting

The owner wants to choose the order of the navigation bar, like in Tracker for Simkl (Settings › Bottom bar with up and down arrows). Pinned and Notebooks can swap; the first one is where the app opens and where back goes before leaving the app. Search is locked last: it opens the keyboard, so it should never be the start destination. The order is kept with the other settings (backed up). Rejected: switches to hide destinations (there are only three, and each is needed), and drag handles (two movable rows; arrows are what the Simkl app uses).

## 2026-10-17: The whole header scrolls away

The owner wants as much room for notes as possible: the tabs and the rows of chips now scroll away together with the top app bar, and come back on the first scroll up (quick return). This replaces keeping the tabs fixed (decided earlier today): a notebook is a scroll up away instead of always in view. Built as one header around the app bar, tabs and chips with a nested-scroll connection, because Material's `enterAlways` behaviour only moves the app bar itself; no new library.

## 2026-10-17: One Filters list, app bar scrolls away

The Filters panel is one list again: each property has its drag handle, name and eye, with its values to filter by right below it, instead of the arranger above a separate list of values (owner's request: one integrated screen). The property arranger takes the content under each row and swaps rows by their measured height, since a property with chips is taller than a row. On Pinned and Notebooks the top app bar scrolls away with the notes and comes back on the first scroll up (Material 3's `enterAlways`); the tabs stay, so the notebooks remain one tap away, and the bar keeps the tabs' `surface` color instead of changing color while notes scroll under it. It comes back when another notebook is opened, since a short list can't scroll it into view. Rejected: letting the tabs scroll away too (switching notebooks would need a scroll up first).

## 2026-10-17: Notebooks with sub-notebooks for databases of several data sources

Notion's API (2025-09-03 and later) has no flag on a data source for "nested": every data source has a database as parent, and the database lists its data sources. A database with one data source is what users see as a plain database (Notities); one with several (Gezondheid: Huid, Spijsvertering, Mentale gezondheid) is a container. So the app asks Notion about each database once and keeps the answer. A container becomes one notebook with a sub-notebook per data source (secondary tabs, Material 3's tabs for peers within one place), each with its own filters, sort and cards, because the data sources have different properties. Rejected: an "All" tab across data sources (they share almost no properties). Notion's search can also list a data source the connection can't read (a "Gezondheid" that gave "Notion vindt dit niet"); a data source its own database doesn't list is left out. A key is now asked about as a database before as a data source, since Notion may answer for a database's ID as a data source.

## 2026-10-17: Cards per notebook, Add notebook from Search, filters arranged like properties

What a notebook's cards show (header image, content, up to three properties) is now set per notebook (`DatabaseConfig.cards`) instead of per database: two notebooks of one database can look different. A notebook from before starts from what its database's cards showed. The order and hiding of properties inside a note stays per database (it is about the note, not the notebook). "Add notebook" opens Search in a pick mode (databases and pages on their own) instead of a list of its own, so there is one way to find things; Search starts afresh each time it is opened and has + like Pinned. A database whose data sources are in the search results is left out: it is only their container and couldn't be opened. The Filters panel is the property arranger (Shown, Hidden, drag handles, eyes) without help text, above every property's values to filter by (a hidden property only loses its row of chips; it can still filter). Each chip row ends in × while something in it is selected, and a row only shows once a note has a value for its property. Common errors from Notion are said in the user's language. Help texts that explained the obvious are removed. "New subpage" is back in a page's menu (owner's request). Pinned stays one concept for any page: notebooks already are the way to keep a database at hand, so "pinned databases" would be a second word for the same thing. Rejected: renaming Pinned to "Pinned notes and databases".

## 2026-10-17: Pinned, Notebooks and Search in the bar; notebooks as tabs

The owner pointed out that notebooks pinned to the bar sat on the same level as Pinned and Notebooks while they belong under Notebooks, and that search is global. Material 3: the navigation bar is for top-level destinations, tabs for peer views within one destination. So the bar has Pinned, Notebooks and Search, and the notebooks are scrollable tabs inside Notebooks (no more pinning to the bar, no Notebooks sheet). Search moves from the top app bar to the bar: it is global, and the app bar is now free for a notebook's Filters (a badge dot while filters are on) and Sort buttons. Sort's Reverse order is gone: tapping the current sort again turns it around, with an arrow for the direction. The Filters panel is one list of properties with an eye and a drag handle each, instead of separate "Rows of chips" and "Only here" sections. "New subpage" is removed from a page's menu (owner's request). Rejected: a navigation rail or drawer for the notebooks (the drawer was hard to reach; a rail is for wide windows).

## 2026-10-17: Notebooks are a database's raw notes; Notebooks sheet instead of the drawer

The owner found following Notion views too confusing: "take the raw data sources and the user sets up the notebook as they want". Notebooks no longer use Notion's views at all: no view picker when adding one, no "Follow the view" or "Reset to Notion's view", and the app no longer asks Notion for views (`/views` calls removed). A notebook shows all notes of its database, with its own filters and sort on the phone. Notebooks that followed a view keep their name, icon, filters and chip rows and show all notes now; one sorted in the view's order gets the custom order. Rejected: keeping views as an option (the confusing part was the two layers of filters, not the default).

Each quick filter now has its own row of chips, starting with the property's name, instead of all chips in one row, and which properties have a row (and in which order) is arranged in the Filters panel with the same arranger as a note's properties (drag by the handle, the eye), so there is one way to arrange things in the app.

The owner found four notebooks in the bar too few and the menu button in the top corner hard to reach. The navigation drawer is replaced by a "Notebooks" item at the end of the navigation bar, which opens a bottom sheet with every notebook, Manage notebooks and Settings (Material 3: a bar for the few most used destinations; a sheet keeps the rest within reach of the thumb, a drawer behind a top-corner button doesn't). It costs one bar slot (three notebooks next to Pinned on a phone). Rejected: scrollable tabs at the top (Material: tabs are for peer views within one destination, and they are at the top too), and a bar beyond five items (labels get cut off).

## 2026-10-17: A page can be a notebook of its subpages

The owner asked to make regular pages notebooks too, with their subpages as the notes. A page's notebook lists the child pages directly on the page (`GET /blocks/{page}/children`, the `child_page` blocks), in the page's order, and downloads a subpage only when it changed; a new note is a new subpage. They are kept under a key of their own (`page:<id>`), so they work like a database's notes everywhere (Pinned, pins, trash, previews). Rejected: subpages nested deeper (inside toggles or columns; slower, and rarely what "the notes of this page" means) and the page's own text as a note (it is opened as a page as before).

## 2026-10-17: Notebooks follow Notion views; filters and sort on the phone

The owner wanted to add an existing Notion view as a notebook and change it on the phone without syncing back ("mobile needs a different experience"), with a filter and sort button and the chips as highlighted filters. Notion's Views API (March 2026) lists a database's views and can run one, but a view query can't take extra filters or sorts. So Notion runs the view (its filter and order, exactly as in Notion, also complex ones the app couldn't redo) and returns the page IDs; the app keeps them and applies its own filters (per property) and sort on top, on the phone, also offline. A new notebook starts from the view's quick filters, order and property layout. Everything after that is the phone's: Filters (follow the view or not, filters per property, which ones are chips, reset to the view) and Sort (Notion's order, custom, last edited, created, title, a date property). Dragging switches to the custom order with an Undo snackbar instead of a confirmation dialog (Material 3: undo for actions that are easy to reverse). Rejected: translating Notion's filter JSON into the app's filters (many conditions can't be redone exactly), writing changes back to the Notion view (it would change everyone's desktop view), and putting every filter in the chip row (too long to scan).

## 2026-10-17: Notebooks, and Search instead of Browse

The owner asked where browsing all of Notion fits, without making the app a full Notion client, and proposed calling the app's collections notebooks. "Views" become **notebooks** in every screen and language ("Notebook" stays in English, as a name of the app's own, like Noter); the code keeps its names (`DatabaseConfig`, `AppSettings.databases`) so saved settings stay. Browse leaves the navigation bar: the bar is for places to stay (Material 3), and browsing Notion is a tool used now and then. It becomes **Search**, a screen behind the search button that first lists the notes on the phone whose title matches (at once, offline too), then Notion's databases and pages; a database found there can be added as a notebook. "New page at the top of the workspace" moved to + on Pinned (with a personal access token). Rejected: keeping Browse as a tab (it competed with the notebooks for room in the bar) and removing it (opening any page or database stays useful, also to add notebooks).

## 2026-10-17: Properties arranged in place, all shown by default

The owner found choosing and ordering a note's properties too complicated: a separate sheet, and only the first three shown by default. Now every property is shown, as in Notion, and the tune button turns the properties into a list to arrange right where they are: drag to reorder, drag below the "Hidden" line (or tap the eye) to hide. The layout stores the hidden properties instead of the shown ones, so a property added in Notion later shows up, as it would in Notion. Rejected: keeping the sheet (one more screen to learn) and long-pressing a property to drag it while editing values (the two gestures clash).

## 2026-10-17: Personal access tokens

The app asked for an internal integration's key, which only sees the pages shared with it and can't add pages at the top of the workspace; the owner found "only available for some connections" a poor experience. Notion now has personal access tokens: they act as the person who made them, see everything that person sees, and can add private pages at the top of the workspace. Settings now walks through making one (with a button to Notion's token page), and the app asks Notion which kind of token it has (`GET /users/me`: a person owns a personal access token's bot, the workspace owns a connection's). With a connection's token, Settings says what it can't do and the top-of-workspace option isn't shown. A personal access token can't list the workspace's people, so person properties fall back to the people in the notes and the token's owner. Rejected: OAuth now (it needs a server for Notion's client secret; on hold) and refusing connection tokens (they still work for shared pages).

## 2026-10-17: Notion API 2026-03-11

The owner asked whether the app uses Notion's newest API. It moves from 2025-09-03 to 2026-03-11, whose breaking changes the app meets with small changes: a page goes to the trash with `in_trash` (no more `archived`), and new blocks go after a block with `position: {type: after_block}` (no more `after`). With it the app reads heading 4 (editable, `#### `), tab blocks (shown like columns, see `logic/Columns.kt`) and every kind of page icon: Notion's own icons used to come as an SVG link and now come as a name and color, uploaded images and custom emoji as a link. Notion's own icons are SVG files, which the app can't draw without a new library; it shows the closest Material Symbol in the icon's color instead. Rejected: staying on 2025-09-03 (no heading 4 or tabs, and the `archived` field it still used was deprecated in 2024) and adding Coil's SVG decoder (a new dependency for a small gain).

## 2026-10-17: Package name be.wimvandonck.noter

The app's package name (`applicationId` and the Kotlin package) was `com.impca201.noter`, after the owner's old GitHub name. Before the first Play Store release it becomes `be.wimvandonck.noter`, the owner's own domain written backwards: a domain stays the owner's, a username can change, and a package name can never change once the app is on the Play Store. Android treats the new name as a different app, so the old one doesn't update to it: the owner installs the new app next to the old one, sets it up again (key, views) and removes the old one. Notes are in Notion, so nothing is lost; settings on the phone (views, pins, order) are set up again. Rejected: `com.wimvdon.noter` (tied to a username again) and keeping the old name (the Play Store would freeze it).

## 2026-10-17: Less help text

The owner found the app over-explanatory; the shared rule is in `DESIGN.md` ("Wording"): help text only says what the screen can't. Shortened: the help above the views list (only "stays on this phone, Notion isn't changed"), the bottom-bar room line, the card-options note and the property-order sheet (no "drag to change the order", no pointer to another screen). Kept: consequences, privacy notes and setup steps. Rejected: removing every hint (the remaining ones say something the screen doesn't).

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
- **Languages:** English (default), Dutch, French, German, Spanish and Italian, like the sibling apps. `generateLocaleConfig`
  makes the app appear under Android's per-app language settings.
- **Persistence:** DataStore when needed (version in the catalog, not added as a dependency yet). No
  network, analytics or crash-reporting libraries.
- **No instrumented/UI tests** yet: they need an emulator in CI (slow, flaky). Logic is kept in plain
  Kotlin and unit tested instead. Manual device checks are listed in `docs/testing.md`.
- **No lint baseline:** the template has zero lint issues.
- **Play Store upload** is prepared but commented out in `release.yml` until a Play Console app and
  service account exist.
