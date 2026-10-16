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
- Button labels are in sentence case.
- Icons come from Material's Rounded set; direction-dependent icons use `AutoMirrored`.
- Cards and grouped surfaces use the `surfaceContainer*` roles.
- Components keep Material's shape scale (`MaterialTheme.shapes`). App-level containers use
  `AppShapes`: `Group` (24dp) for banners and grouped content, `Item` (20dp) for tiles and
  highlighted rows.

## Navigation and structure

Follows Material 3's split between destinations and actions:

- Top-level destinations (two to five) sit in Material's `NavigationBar`, docked at the bottom
  edge. On wide windows an app may switch to a `NavigationRail` with the same destinations in
  the same order. Floating bars (M3 Expressive's floating toolbar) are for actions on the
  current screen, never for switching sections.
- Tabs are only for peer views of the same content. Chips filter or sort a collection; they are
  not navigation and not general buttons.
- Every other page is a child page: a top app bar with a back arrow, no navigation bar.
- One FAB at most, for the screen's main action (add, start). Destructive actions never sit in
  a FAB or next to routine actions without separation, and ask for confirmation or offer Undo.
- Lists are lists: `ListItem` rows (or rows with the same paddings), separated by spacing or
  dividers. A card is for a unit that stands on its own (a poster, a banner, a running timer),
  not for every row or section.
- Content keeps a readable width on wide windows (about 640dp for lists and settings).

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

- Prefer the shortest clear wording ("System", not "Follow device (Auto)").
- American spelling in English ("color").
- Address the user informally in Dutch, German, Spanish and Italian (je, du, tú, tu) and with
  "vous" in French.
- Labels and buttons in sentence case.

## Shared design kit

These files live in each app (the template does not include them). They are identical in every
app apart from their package (and `R` import) lines. Copy a change to the other repositories:

- `ui/theme/Motion.kt`: motion tokens, the shared-axis transition and `rememberReducedMotion()`.
- `ui/theme/Shapes.kt`: `AppShapes`.
- `ui/theme/SystemBars.kt`: `SystemBarsFollowTheme()`.
- `ui/theme/Skeleton.kt`: loading placeholders (`Modifier.skeleton()`, `SkeletonLine`,
  `SkeletonBlock`).
- `ui/theme/SettingsKit.kt`: the settings screen components above. It also needs
  `drawable/ic_arrow_back`, `drawable/ic_expand_more` and the strings `section_expanded` and
  `section_collapsed`.

## This app

- Icon (owner's sketch, after Notion Calendar's logo): two stacked notes. Of the note behind,
  only the top and left edges show; the note in front has a folded bottom right corner and two
  white text lines, with a white gap between the notes. Gradient `#FFC94D` → `#F5A623` →
  `#E8710A` (amber) from top left to bottom right, on the white background; the folded corner
  shows the paler back of the paper. Themed layer: the same shapes with thin gaps, text lines
  cut out. Both layers are drawn at 78% of their first size, centred on the visible drawing (the
  folded corner and the note behind balance each other), so the notes keep clear padding inside
  round and squircle launcher masks (owner's request).
- View icons: the whole Material Symbols set (outlined, rounded), searchable by English name and
  topic, the same catalog as RSS Reader's folder icons.
- Seed `#F5A623` (amber): a full Material 3 tonal scheme from the icon's middle stop.
- Signature element: the overview in the style of Google Keep. Notes are `OutlinedCard`s in a
  staggered grid (cells at least 160dp wide: two columns on a phone) with, when chosen per
  database, the header image on top (96dp high, cropped), the title (Title Medium), up to ten lines of content preview (Body Medium, `onSurfaceVariant`) and the
  properties chosen for cards (up to three; options in Notion's colours, other values in Label
  Medium), or else the filter property's options. "Pinned" and "Others" are Label Large headers spanning
  the grid. A dragged card lifts (shadow, 3% larger, `surfaceContainerHigh`).
- Navigation: Pinned, Browse and the user's views are the top-level destinations, in that order.
  All are in a modal navigation drawer (menu button; it doesn't open with a swipe), with the views
  under a "Views" header, then Manage views and Settings. The navigation bar shows Pinned, Browse
  and the views pinned to it, as many as fit (an item per 80dp; owner's choice, see decisions). Browse is one list with
  a pill-shaped search field and "Databases" and "Pages" headers (Title Small, primary). A
  database opened from Browse is a child page with an "Add view" text button in its app bar,
  "Edit view" once it is a view.
- The note editor follows Notion's look: headings in SemiBold, quotes with a bar, callouts and
  code on `surfaceContainerHigh` (code in a monospace font), dividers as `outlineVariant` lines.
  Read-only blocks sit in a `surfaceContainer` box with "(edit in Notion)".
- The block toolbar sits above the keyboard on `surfaceContainerHigh`; the selected kind uses
  `secondaryContainer`.
