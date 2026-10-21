# Design

Noter for Notion is one of the sibling Android apps (Tracker for Simkl, Time Tracker for Notion,
Snoozer, RSS Reader and Noter for Notion) that share one design language.

The shared rules (every section except "This app") have their master copy in `DESIGN.md` of
the android-app template (`wimvdon/android-app`). Change them there first, then copy them
unchanged to every app. Only the "This app" section belongs to this repository.

## Icons

- White adaptive-icon background.
- One bold shape with a diagonal gradient through neighbouring hues (top left to bottom
  right), filling most of the 66dp safe zone.
- One white detail inside that shape.
- A themed (monochrome) layer: the same shape with the detail cut out.

| App | Shape | Gradient |
| --- | --- | --- |
| Tracker for Simkl | TV screen with a checkmark | `#3D7BFF` → `#7A5CFF` → `#D15CE6` |
| Time Tracker for Notion | Stopwatch dial with a hand | `#FF8A3D` → `#F04438` → `#C81E5A` |
| Snoozer | Nine-lobed "cookie" (the home screen toggle) with a bell | `#22C3B4` → `#3E7BDA` → `#4E5BB0` |
| RSS Reader | The feed symbol on its own (owner's choice, see its "This app") | `#4FC3F7` → `#0091EA` → `#0D47A1` |
| Noter for Notion | Sticky note on a 3D box with a folded corner | `#FFC94D` → `#F5A623` → `#E8710A` |

## Colour

- Each app's fallback palette is a full Material 3 tonal scheme generated from its icon's
  middle gradient stop, so every colour role is set (none fall back to Material's purple).
- Settings › Theme offers System / Light / Dark and, on Android 12+, a Dynamic color switch.
  Dynamic color is off by default so the app's own colours show.
- The status and navigation bar icons follow the app's theme, not the system's
  (`SystemBarsFollowTheme`).
- `android:windowBackground` (and the splash background) matches the theme's background.

## Material 3

- Top app bars are Material's `TopAppBar` family with the default title style
  (Title Large, regular weight). Weight is not used to make app bar titles stand out.
- Emphasis through weight is kept for hero content (a title on a detail page, a big number).
- Button labels are in sentence case and short: the action in one to three words ("Watched",
  "Open settings", "Retry"). The screen around the button gives the context, so a label never
  repeats it ("Watched", not "Mark as watched"); explanations go in the text above the button.
  Longer wording is fine in menus and for screen readers (`contentDescription`).
- Icons come from Material's Rounded set; direction-dependent icons use `AutoMirrored`.
- Cards and grouped surfaces use the `surfaceContainer*` roles.
- Components keep Material's shape scale (`MaterialTheme.shapes`). App-level containers use
  `AppShapes`: `Group` (24dp) for banners and grouped content, `Item` (20dp) for tiles and
  highlighted rows.

## Navigation and structure

Follows Material 3's split between destinations and actions:

- Top-level destinations (two to five) sit in Material's `NavigationBar`, docked at the bottom
  edge. On wide windows an app may switch to a `NavigationRail` with the same destinations in
  the same order. A selected destination shows its filled (Rounded) icon, the others their
  Outlined icon, as Material's navigation guidelines describe. Floating bars (M3 Expressive's floating toolbar) are for actions on the
  current screen, never for switching sections.
- Tabs are only for peer views of the same content. Chips filter or sort a collection; they are
  not navigation and not general buttons.
- Every other page is a child page: a top app bar with a back arrow, no navigation bar.
- One FAB at most, for the screen's main action (add, start). Destructive actions never sit in
  a FAB or next to routine actions without separation, and ask for confirmation or offer Undo.
- Lists are lists: `ListItem` rows (or rows with the same paddings), separated by spacing or
  dividers. A card is for a unit that stands on its own (a poster, a banner, a running timer),
  not for every row or section.
- Rows that scroll sideways (posters, trailers, chips) keep one height while they scroll: text
  under a tile has a fixed number of lines (two for a title, one for a caption, ending in "…"),
  and a missing line still takes its space. Otherwise the row grows and shrinks as tiles with
  longer titles scroll into view, and everything below it jumps.
- Content keeps a readable width on wide windows: lists, forms, settings and text stay at most
  640dp wide and centred (`Modifier.readableWidth()`). Grids of posters or cards and pictures
  may use the whole width.

## Reordering

Every list the user can put in their own order works the same way (`ui/theme/Reorder.kt`):

- A drag handle (the six-dot grip, `DragHandle`) at the end of each row. Dragging it moves the
  row at once; the dragged row lifts with a shadow on `surfaceContainerHigh`, with a haptic tick
  when the drag starts. The new order is saved when the row is dropped.
- No up and down arrows. Screen readers get Move up and Move down as actions on the handle (or
  on the row in a list with its own drag code).
- A row that can't move (for example a destination locked last) shows what it is instead of a
  handle, such as a lock, and other rows can't pass it.
- Short lists use `ReorderableColumn`. A long or lazy list, or one with sections, may build
  its own drag on the same look and handle.
- Cards in a grid (a staggered grid of notes) are moved with a long press instead, as on a
  home screen: a grid has no row end for a handle.

## Settings screen

Built from `ui/theme/SettingsKit.kt`, so every app's settings look and behave the same. It
follows the Android and Material 3 settings pattern: one scrolling list, grouped by headers.

- `SettingsScaffold`: a large top app bar ("Settings") that collapses on scroll, with a back
  button, over one list, at most 640dp wide.
- `SettingsSection`: a section header (Title Small in the primary colour) above its rows. No
  cards and no collapsing: every setting is visible and one tap away.
- Rows inside a section:
  - `SwitchSetting`: a `ListItem` with a switch; the whole row is the switch and the change
    applies at once (no Save button).
  - `SettingItem`: a `ListItem` that opens something (a page, a dialog, another app), with a
    chevron or "opens elsewhere" icon when it leaves the screen.
  - `ExpandableSetting`: a row that shows longer instructions below it (setup steps most people
    read once).
  - `SettingsBlock`: anything else (a text field, segmented buttons for a single choice among
    two to four options, an explanation, a button), lined up with the rows' text.
- A choice among more than four options, or with long labels, opens a dialog with radio buttons
  from a `SettingItem` that shows the current value.
- Sections in this order, leaving out what an app does not have:
  1. Connection (account, API key, sync)
  2. The app's own features
  3. Shortcuts and integrations
  4. Appearance (`AppearanceSettings`: Theme, then Dynamic color)
  5. Language and formats
  6. `SettingsFooter`: app name and version, plus links such as data sources

## Motion

- Material 3 easing and duration tokens in `Motion`.
- Screens change with the shared-axis X transition (`Motion.sharedAxisX`, 30dp).
- State changes fade through (`Motion.fadeSpec`).
- Decorative animation is skipped when the user turned animations off
  (`rememberReducedMotion()`).

## Loading

- Content that loads over the network shows placeholders while it loads for the first time
  (`ui/theme/Skeleton.kt`), shaped like what will appear: poster tiles, list rows, form fields,
  lines of text. A soft highlight sweeps across them; with animations off they stay still.
- Screen readers read the placeholders out as one "Loading…" element.
- A refresh keeps the content on screen and shows pull-to-refresh or a small progress
  indicator instead; a busy button shows a spinner in place of its icon.
- An app with nothing to load (Snoozer) leaves `Skeleton.kt` out.

## Empty and setup states

Every screen that can be empty uses `EmptyState` (`ui/theme/EmptyState.kt`), so an empty
screen looks the same in every app:

- Centred in the space below the app bar, at most 360dp wide, and scrollable so pull-to-refresh
  keeps working.
- An icon in a 72dp `secondaryContainer` circle that says what is missing (a bookmark for an
  empty watchlist, a key for a missing API key), a title in Title Large that says what the state
  means ("No feeds yet"), and, when it helps, one or two sentences in Body Medium on how to fill
  the screen or what will appear here.
- At most two buttons, for the next useful step: a filled `Button` for the main one, an
  `OutlinedButton` or `TextButton` for an alternative. No button when there is nothing useful
  to do (a list that fills by itself).

The same component covers four situations:

| Situation | Example | Button |
| --- | --- | --- |
| Nothing here yet | "No watched movies" | The way to add something, if there is one |
| Nothing matches | A search or filter found nothing | "Clear filters" when filters are on |
| Couldn't load | The network failed and there is nothing to show | "Retry" |
| Setup needed | Not signed in, no API key, a permission missing | Straight to the place that fixes it ("Open settings") |

- A setup-needed state replaces the screen's content: a screen that can't work shows what to
  do, not a disabled screen with a small banner above it. Apps that need the same setup use the
  same words (Time Tracker for Notion and Noter for Notion both say "Connect to Notion").
- A banner (a `Group`-shaped card at the top) is only for something worth knowing while the
  screen still works. An error from an action on a screen that has content is a snackbar.

## Wording

Shared settings and actions use the same words in every app and language (strings are kept in
English, Dutch, German, French, Spanish and Italian).

| Meaning | en | nl | de | fr | es | it |
| --- | --- | --- | --- | --- | --- | --- |
| Theme: follow the device | System | Systeem | System | Système | Sistema | Sistema |
| Theme: light / dark | Light / Dark | Licht / Donker | Hell / Dunkel | Clair / Sombre | Claro / Oscuro | Chiaro / Scuro |
| Settings section | Appearance | Weergave | Darstellung | Apparence | Apariencia | Aspetto |
| Wallpaper colours | Dynamic color | Dynamische kleuren | Dynamische Farben | Couleurs dynamiques | Color dinámico | Colori dinamici |
| Back | Back | Terug | Zurück | Retour | Atrás | Indietro |
| Cancel / Save | Cancel / Save | Annuleren / Opslaan | Abbrechen / Speichern | Annuler / Enregistrer | Cancelar / Guardar | Annulla / Salva |
| Clear | Clear | Wissen | Löschen | Effacer | Borrar | Cancella |
| Retry | Retry | Opnieuw proberen | Wiederholen | Réessayer | Reintentar | Riprova |
| Dynamic color, supporting text | Use colors from your wallpaper | Kleuren van je achtergrond gebruiken | Farben deines Hintergrundbilds verwenden | Utiliser les couleurs du fond d’écran | Usar los colores del fondo de pantalla | Usa i colori dello sfondo |
| Go to settings | Open settings | Naar instellingen | Zu den Einstellungen | Ouvrir les paramètres | Abrir ajustes | Apri impostazioni |
| Setup needed (Notion apps) | Connect to Notion | Verbind met Notion | Mit Notion verbinden | Connexion à Notion | Conéctate a Notion | Collegati a Notion |
| Close a dialog that only shows something | Close | Sluiten | Schließen | Fermer | Cerrar | Chiudi |
| Finish an editing mode | Done | Klaar | Fertig | OK | Listo | Fine |
| Leave an account | Sign out | Uitloggen | Abmelden | Se déconnecter | Cerrar sesión | Esci |
| Screen reader: reorder handle | Reorder | Volgorde wijzigen | Neu anordnen | Réorganiser | Reordenar | Riordina |
| Screen reader: move a row | Move up / Move down | Omhoog / Omlaag | Nach oben / Nach unten | Monter / Descendre | Subir / Bajar | Sposta su / Sposta giù |

- Prefer the shortest clear wording ("System", not "Follow device (Auto)").
- American spelling in English ("color").
- Address the user informally in Dutch, German, Spanish and Italian (je, du, tú, tu) and with
  "vous" in French.
- Labels and buttons in sentence case.
- Help text only says what the screen doesn't: a consequence ("Your SIMKL lists stay as they
  are"), where data goes, or a term people may not know. It never restates a label ("Hide
  specials" needs no "Leave out special episodes"), describes a standard control (drag handles,
  switches, tapping a row) or explains why the app was built this way. One sentence where
  possible; longer setup steps go in an `ExpandableSetting` or the user guide.
- Buttons name the action as briefly as the language allows; drop "mark as", "markeren als",
  "als … markieren" and the like when the screen already makes it clear ("Gezien", not
  "Markeren als gezien").

## Shared design kit

These files live in each app (the template does not include them). They are identical in every
app apart from their package (and `R` import) lines. Copy a change to the other repositories:

- `ui/theme/Motion.kt`: motion tokens, the shared-axis transition and `rememberReducedMotion()`.
- `ui/theme/Shapes.kt`: `AppShapes`.
- `ui/theme/SystemBars.kt`: `SystemBarsFollowTheme()`.
- `ui/theme/Skeleton.kt`: loading placeholders (`Modifier.skeleton()`, `SkeletonLine`,
  `SkeletonBlock`).
- `ui/theme/EmptyState.kt`: `EmptyState` for empty, no-result, failed and setup-needed screens
  (see "Empty and setup states").
- `ui/theme/SettingsKit.kt`: the settings screen components above. It also needs
  `drawable/ic_arrow_back`, `drawable/ic_expand_more` and the strings `section_expanded` and
  `section_collapsed`.
- `ui/theme/Layout.kt`: `ReadableWidth` (640dp) and `Modifier.readableWidth()`.
- `ui/theme/Reorder.kt`: `DragHandle` and `ReorderableColumn` (see "Reordering"). It also
  needs `drawable/ic_drag_handle` and the strings `action_reorder`, `action_move_up` and
  `action_move_down`. An app without lists to reorder (Snoozer) leaves it out.

## This app

- Icon (owner's sketch, after Notion Calendar's logo): two stacked notes. Of the note behind,
  only the top and left edges show; the note in front has a folded bottom right corner and two
  white text lines, with a white gap between the notes. Gradient `#FFC94D` → `#F5A623` →
  `#E8710A` (amber) from top left to bottom right, on the white background; the folded corner
  shows the paler back of the paper. Themed layer: the same shapes with thin gaps, text lines
  cut out. Both layers are drawn at 64% of their first size, centred on the visible drawing (the
  folded corner and the note behind balance each other), so the notes keep clear padding inside
  round and squircle launcher masks (owner's request).
- Icons: databases and pages show Notion's own icon (emoji or image); for one of Notion's built-in
  icons the closest Material Symbol in its colour, from the same catalog as RSS Reader's folder
  icons. Without an icon: Rounded TableChart (database) or Description (page).
- Seed `#F5A623` (amber): a full Material 3 tonal scheme from the icon's middle stop.
- Signature element: notes in the style of Google Keep (on Home and in a database). Notes are `OutlinedCard`s in a
  staggered grid (cells at least 160dp wide: two columns on a phone) with, when chosen per
  database, the header image on top (96dp high, cropped), the title (Title Medium), up to ten lines of content preview (Body Medium, `onSurfaceVariant`) and the
  properties chosen for cards (up to three; options in Notion's colours, other values in Label
  Medium), or else the filter property's options. "Pinned" and "Others" are Label Large headers spanning
  the grid. A dragged card lifts (shadow, 3% larger, `surfaceContainerHigh`).
- Navigation: no navigation bar; Home is the one main screen and everything else is a child page.
  Home's top is a large search bar (56dp high, fully rounded, `surfaceContainerHigh`, a search
  icon and "Search Notion" in Body Large `onSurfaceVariant`) with the sync and Settings icon
  buttons at its end, as in Material 3's search bar on a main screen; tapping it opens Search.
  Below it one grid: "Pinned" (a Title Small, primary header), the pinned databases as list rows,
  the pinned notes and pages as cards, then "Databases and pages" as list rows (icon, name,
  "Edited …", a small primary pin at the end when pinned) in a tree of two levels: a row with
  pages inside ends in a chevron icon button (pointing right when closed, down when open) and its
  pages follow it indented by 24dp; "Show more" (a text button) below the first 30. A long press on a row opens a menu with
  Pin or Unpin. One FAB (+): a sheet with "New page" (at the top of the workspace), "New database at
  the top" and "New note in" with the databases. New database is a dialog: a name field, then a
  name field per property (its kind as the label, × to remove) and Add property with a menu of
  kinds; a toast after it opens. With tokens for several workspaces, "Databases and pages" becomes one
  part per workspace under its name (Title Small, primary, a chevron; a tap folds it away), rows
  say "Workspace · Edited …", and the + sheet has a part per workspace (its name as the title, with
  a chevron that folds the part away,
  "New page at the top" when its token may, then its databases).
- A database is a child page: back arrow, Notion's icon and name, then sync, Filters, Sort, Cards
  (Tune icon, as a page's properties button) and ⋮ (Pin or Unpin, Open in Notion, Share). A database of several data sources has a primary scrollable tab row, one
  tab per data source. The whole header (top app bar, tab row and rows of chips, `surface`)
  scrolls away with the notes and comes back on the first scroll up (`QuickReturnHeader`); the
  status bar area stays.
- Filters and sort: icon buttons in a database's top app bar. Filters shows a small badge dot
  while filters are on; Sort opens a menu with a check on the current sort and an arrow (up:
  ascending, down: descending) that tapping the current sort again turns around. Each quick
  filter has a row of chips of its own above the notes, starting with the property's name (Label
  Large, `onSurfaceVariant`) and, while something in it is selected, ending in an × icon button
  outside the scrolling chips. The Filters panel is the property arranger: "Shown" and "Hidden"
  sections, a drag handle and an eye on each property, and its values to filter by (filter chips)
  right below it, so it is one list. A chip's count of notes sits in a small
  `surfaceContainerHighest` pill after its label.
- Search, a child page: a back arrow and the query field in the app bar, a row of filter chips
  (Pages, Databases and, with several workspaces, one per workspace; a tap again shows
  everything), then one list of rows, best matches first, without headers.
- Cards panel (the Cards button in a database's top app bar): a bottom sheet with switches for the header image and
  the start of the content, and filter chips for up to three properties.
- The note editor follows Notion's look: headings in SemiBold, quotes with a bar, callouts and
  code on `surfaceContainerHigh` (code in a monospace font), dividers as `outlineVariant` lines.
  Blocks the app can't show sit in an outlined box (`outlineVariant`) with their kind and an
  "Open in Notion" button; nested content still downloading shows skeleton lines instead, so the
  two never look alike.
  Columns are shown one below the other with a thin `outlineVariant` line between them.
- The block toolbar sits above the keyboard on `surfaceContainerHigh`; the selected kind uses
  `secondaryContainer`.
- Select text (⋮ in a note): a full-screen view with × and the note as plain text in one
  selection area, styled like the editor, nothing editable.
- Steps in a guide have numbered circles (32dp, `primary`, the number in
  Label Large `onPrimary`), as in Snoozer's guide.
- A picker (options, people, linked pages) lists what is selected first when it opens and keeps
  its order until it is closed.
