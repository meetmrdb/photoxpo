---
name: Photoxpo
description: A calm teal Material 3 photo library that lives on a USB-C drive, with big plain buttons and honest status.
colors:
  primary: "#0B5C63"
  on-primary: "#FFFFFF"
  primary-container: "#C4EBEA"
  on-primary-container: "#00201F"
  secondary-container: "#CCE8E5"
  on-secondary-container: "#05201E"
  background: "#F6FAF9"
  surface: "#EAF1F0"
  surface-high: "#DDE7E6"
  on-surface: "#161D1D"
  on-surface-variant: "#3F4948"
  outline: "#6F7978"
  outline-variant: "#BEC9C8"
  error: "#B3261E"
  error-container: "#F9DEDC"
  on-error-container: "#410E0B"
  warning-container: "#FFDEA1"
  on-warning-container: "#3E2D00"
  scrim: "#0A1010 at 62%"
  dark-primary: "#7BD5DA"
  dark-on-primary: "#003739"
  dark-primary-container: "#004F53"
  dark-on-primary-container: "#C4EBEA"
  dark-secondary-container: "#2F4B49"
  dark-on-secondary-container: "#CCE8E5"
  dark-background: "#0E1514"
  dark-surface: "#192020"
  dark-surface-high: "#252D2D"
  dark-on-surface: "#DDE4E3"
  dark-on-surface-variant: "#BEC9C8"
  dark-outline: "#889392"
  dark-outline-variant: "#3F4948"
  dark-error: "#FFB4AB"
  dark-error-container: "#93000A"
  dark-on-error-container: "#FFDAD6"
  dark-warning-container: "#5A4300"
  dark-on-warning-container: "#FFDEA1"
typography:
  headline-small:
    fontFamily: "Instrument Serif, serif"
    fontSize: "34sp"
    fontWeight: 400
    lineHeight: 1.12
    letterSpacing: "0"
  headline-medium:
    fontFamily: "Instrument Serif, serif"
    fontSize: "26sp"
    fontWeight: 400
    lineHeight: 1.15
    letterSpacing: "0"
  title-large:
    fontFamily: "Instrument Serif, serif"
    fontSize: "22sp"
    fontWeight: 400
    lineHeight: 1.27
    letterSpacing: "0"
  title-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: 1.4
  body-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: 1.45
  body-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: 1.4
  label-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: 1.3
    letterSpacing: "0.02em"
rounded:
  tile: "2dp"
  toast: "8dp"
  chip: "10dp"
  note: "14dp"
  card: "20dp"
  segmented: "20dp"
  button: "28dp"
  sheet: "28dp"
spacing:
  grid-gap: "3dp"
  xs: "8dp"
  sm: "12dp"
  md: "16dp"
  lg: "20dp"
  xl: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.button}"
    height: "56dp"
    padding: "0 24dp"
  button-tonal:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.button}"
    height: "56dp"
  button-disabled:
    backgroundColor: "{colors.surface-high}"
    textColor: "{colors.on-surface-variant}"
    rounded: "{rounded.button}"
    height: "56dp"
  button-error:
    backgroundColor: "{colors.error}"
    textColor: "{colors.background}"
    rounded: "{rounded.button}"
    height: "56dp"
  button-text:
    textColor: "{colors.primary}"
    typography: "{typography.title-medium}"
    height: "48dp"
    padding: "0 16dp"
  chip-status:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-medium}"
    rounded: "{rounded.chip}"
    height: "36dp"
  chip-ok:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    rounded: "{rounded.chip}"
    height: "36dp"
  chip-warn:
    backgroundColor: "{colors.warning-container}"
    textColor: "{colors.on-warning-container}"
    rounded: "{rounded.chip}"
    height: "36dp"
  card-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.card}"
    padding: "14dp 16dp"
    height: "64dp"
  card-info:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.card}"
    padding: "20dp"
  navigation-bar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-medium}"
    height: "80dp"
  navigation-indicator:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    rounded: "16dp"
    size: "64dp x 32dp"
  tile:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.tile}"
  history-note:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.note}"
    padding: "12dp 14dp"
---

# Design System: Photoxpo

> **Status: agreed target, not yet shipped.** This file records the design agreed on the Home / Photos / Load and onboarding prototypes (branches `prototype/home-gallery-load`, layout A, and `prototype/onboarding-quality-chooser`). The Build 1 app currently uses the stock Material baseline theme (`ui/theme/Theme.kt`), so none of these tokens are wired in yet. The UI audit tracks that gap. When the theme lands, re-run `/impeccable document` to refresh this file from code.

## Overview

**Creative North Star: "The Quiet Shelf"**

A shelf in a quiet room: everything has a place, you can see at a glance that it is all there, and nothing asks for attention until you reach for it. The app is a library that lives in a drive in your pocket, so it never behaves like a dashboard or a cloud service. One sentence of status, one count, one obvious button per screen.

The system is native Material 3 in a single teal voice, tuned for someone who is not comfortable with phones: large pill buttons, big type, soft tonal surfaces, and plain words. Teal carries the "safe and in place" meaning. Warm amber says "waiting", and red appears only when something genuinely failed. A missing drive is never red. Photos are the only rich content; the chrome around them stays pale and calm, and the viewer goes dark so the photo takes the whole screen.

Brand lives in color roles, the type scale and the shape scale, as Material 3 intends. It is not carried by custom components.

**Key Characteristics:**
- Native Material 3 structure (top app bar, bottom sheets, system dialogs) with a floating navigation pill, single teal brand hue.
- Tonal layering instead of shadows: surfaces step up in tone, never in drop shadow.
- Pill buttons 56 dp tall; every touch target at least 48 dp.
- Large type: 26 sp headline status, 16 sp body minimum for anything read.
- Status is never colour alone: every state pairs an icon and words with its tone.
- Dark theme is a full scheme, not an invert. The photo viewer is always dark.

## Colors

A cool, pale teal-white field with one deep teal accent, warm amber for "waiting" and Material red held in reserve for real failures.

### Primary
- **Deep Harbour Teal** (#0B5C63): Primary buttons, progress fill, the "Backed up" state text, focus rings, text buttons. In dark theme this becomes **Lagoon Mint** (#7BD5DA) on #003739.
- **Sea Glass** (#C4EBEA, on #00201F): Selected navigation indicator, "backed up / ok" chips, the circular icon in history rows, text selection. Dark: #004F53 on #C4EBEA.

### Secondary
- **Quiet Tonal Teal** (#CCE8E5, on #05201E): Tonal buttons (Pause, Cancel), the selected side of the Months/Days toggle, the target rings in the Share sheet. Dark: #2F4B49.

### Tertiary
- **Waiting Amber** (#FFDEA1, on #3E2D00): Drive-out and not-yet-backed-up states ("Plug in your drive", month waiting for a backup). Calm, not alarming. Dark: #5A4300 on #FFDEA1.

### Neutral
- **Pale Reef** (#F6FAF9): Screen background. Dark: Night Reef #0E1514.
- **Sea Mist** (#EAF1F0): Cards, row cards, chips, navigation bar, empty tiles. One step above background. Dark: #192020.
- **Mist High** (#DDE7E6): Progress track, disabled button fill. Dark: #252D2D.
- **Ink Teal** (#161D1D): Primary text. Dark: #DDE4E3.
- **Slate Teal** (#3F4948): Secondary text, month headers, unselected nav labels. Dark: #BEC9C8.
- **Outline** (#6F7978) and **Outline Variant** (#BEC9C8): Segmented-control border and dividers. Dark: #889392 and #3F4948.
- **Photo Scrim** (#0A1010 at 62%): The drive badge, video chip and Preview tag on top of photos, and the dim behind sheets.

### Error (reserved)
- **Signal Red** (#B3261E), container #F9DEDC on #410E0B. Used only for "didn't verify" and failed Loads. Never for a missing drive. Dark: #FFB4AB, container #93000A.

### Named Rules
**The Calm Drive Rule.** A missing Archive Drive is amber or neutral, never red. Red means a file did not verify or a copy failed.

**The One Voice Rule.** Teal is the only brand hue. Primary teal fills at most one button per screen; everything else is tonal or text.

**The Words Beside Colour Rule.** A state is never signalled by colour alone. Every chip, badge and pill carries an icon and a plain-word label.

## Typography

**Display Font:** Instrument Serif (the wordmark's own face, bundled, SIL OFL; text of 20 sp and up only, single weight)
**Body Font:** Roboto
**Label/Mono Font:** none

**Character:** Plain, confident and large. Medium weights for status and actions, regular for explanation. Instrument Serif carries the voice wherever text is big, short and not tappable: the headline sentence, top bar titles, month names, dialog titles and empty-state headlines. Its flagless "1" reads as a lowercase L, so every figure (counts, sizes, years) inside serif text is set in the device sans at regular weight, a little smaller than the line (`figuresInSans`). No other decorative faces, nothing smaller than 12 sp, and all sizes in `sp` so the system font-size setting works (the prototype has a 130% large-text mode that must hold without clipping).

### Hierarchy
- **Headline Small** (Instrument Serif 400, 34 sp, 1.12 line height): The one status sentence on Home ("All 1,204 photos are on your drive"). Balanced wrapping.
- **Headline Medium** (Instrument Serif 400, 26 sp): Dialog titles, set explicitly because Material's own default for dialogs is the larger Headline Small.
- **Title Large** (Instrument Serif 400, 22 sp, 1.27): Top app bar titles and month names.
- **Title Medium** (500, 16 sp, 1.4): Button labels, row card titles, the photo viewer title.
- **Body Large** (400, 16 sp, 1.45): Explanations, onboarding copy, the load card's hint.
- **Body Medium** (400, 14 sp, 1.4): Chips, supporting lines, month headers (500), history counts.
- **Label Medium** (500, 12 sp, 0.02em): Navigation labels, ring-action labels, the Preview tag.

### Named Rules
**The Sentence First Rule.** Each screen opens with one complete plain sentence as its headline, never a label or a number alone.

**The Serif Floor Rule.** Instrument Serif is for text of 20 sp or more and never on a button, chip, control or paragraph. Anything smaller, tappable or full of figures stays in the sans.

## Layout

Single-column phone, compact width. Content padding is 20 dp on Home, 16 dp elsewhere, with 16 dp vertical rhythm between stacked blocks (`stack` gap). Home scrolls: the logo with a small drive status beside it, the headline sentence plus at most one supporting line, one state card (what is waiting, or the progress, pace and warnings of a run), one 56 dp primary button, then one Backup history card that also carries when it last ran and the free space. Photos is the bottom pill's other destination, so Home has no link to it.

The Photos tab is a serif title with a compact Months / Days control, one status strip (how many are on the Archive Drive and how many new ones wait; its drive mark is the legend for the mark on each tile), then a 3-column grid with 3 dp gutters, grouped by sticky serif month headers. Sample a month, never render everything. History is a list of 88 dp month rows, each with a 4-photo thumbnail strip, month, count, and a state pill.

Onboarding is four screens: Welcome, Photos, Archive Drive, Ready. Each has a back arrow and a thin four-part progress bar on top (never a "Step n of m" label), its content in the middle, and its buttons at the bottom where the thumb is. Welcome opens with the one idea as a picture, the Original going to the drive and a Preview staying on the phone, then one serif sentence. Photos names what is asked for in one card and shows what the system dialog will offer, with Allow all marked. Archive Drive is two numbered rows, plug in then choose its folder. Ready is a two-row summary (Archive Drive, Previews) with a Choose or Change action on each; Preview quality is not a screen but a bottom sheet opened by Change on Previews, with Balanced as the default.

Edge-to-edge with status, navigation and IME insets applied. A floating navigation pill with two destinations (Home, Photos) on compact width; the history screen is a pushed child with a back arrow. On expanded widths, use a navigation rail rather than stretching the phone layout.

### Named Rules
**The One Button Rule.** One filled primary button per screen. A second action is tonal or text.

## Elevation & Depth

Tonal, not shadowed. Depth comes from stepping the surface tone: Background → Surface (cards, chips, nav bar) → Surface High (tracks, disabled). No drop shadows on app chrome. Scrim at 62% dims behind sheets and dialogs and sits behind overlays on photos. In the photo viewer, depth is a pair of black gradients (top ~#000 at 70% fading out, bottom fading in over 72 dp) so text and actions stay readable on any photo.

### Named Rules
**The Flat Chrome Rule.** App chrome has no shadows; the floating pill is lifted by a brighter tone and a 1 dp outline instead. Only the photo viewer uses gradients, and only to keep text legible.

## Shapes

Soft and rounded, with a clear size hierarchy: pill buttons (28 dp radius at 56 dp height), large cards and row cards (20 dp), bottom sheets (28 dp top corners), small chips (10 dp), notes (14 dp), segmented control (20 dp), toasts (8 dp). Photo tiles are nearly square-cornered (2 dp) so the grid reads as a wall of photos. Badges, ring actions and the history dot are full circles. The progress bar is 8 dp tall with 4 dp radius.

## Components

### Buttons
- **Shape:** full pill (28 dp radius), 56 dp tall, full width on screens with a single action; compact variants are 48 dp tall and auto width.
- **Primary:** Deep Harbour Teal fill, white label in Title Medium. Scales to 98% on press.
- **Tonal:** Quiet Tonal Teal fill; for Pause, Cancel, secondary choices.
- **Text:** no fill, teal label, 48 dp min height; for low-priority actions.
- **Disabled:** Mist High fill, Slate Teal label. Used for "Plug in your drive" when the drive is out.
- **Error:** Signal Red fill, only in failure dialogs.
- **Focus:** 3 dp teal outline at 2 dp offset.

### Chips
- **Style:** 36 dp tall, 10 dp radius, Sea Mist fill, icon plus 14 sp label.
- **Ok:** Sea Glass fill ("Drive connected"). **Warn:** Waiting Amber fill ("Drive not connected" is neutral, not red).
- On photos: scrim fill, white text ("On your drive"), or teal at 80% when the Original is on the phone.

### Cards / Containers
- **Corner Style:** 20 dp.
- **Background:** Sea Mist, no border, no shadow.
- **Row card:** 64 dp min height, 14 dp gap, leading icon, title plus one supporting line, trailing chevron.
- **Internal Padding:** 14 × 16 dp (row), 20 dp (info).

### Navigation
- A floating pill, 64 dp tall and centred 16 dp above the bottom edge (plus the system inset), on Mist High with a 1 dp outline and no shadow. Two destinations, each 48 dp tall and at least 104 dp wide, with an icon and an always-visible 14 sp label. The selected item fills with Sea Glass. Lists pad their end by 96 dp so the last row clears it and the rest scrolls under it. On windows 600 dp and wider it is a navigation rail instead.
- Top app bar: 64 dp, Title Large, 48 dp icon buttons; back arrow variant has 4 dp left padding. System Back always works.
- **Segmented control** (Months / Days; By month / By backup): 1 dp outline border, 20 dp radius, 40 dp segments, selected segment in Quiet Tonal Teal.

### Photo grid
- 3 columns, 3 dp gutters, square tiles. Each tile whose Original lives only on the drive carries a 24 dp circular scrim badge with a 15 dp drive glyph, bottom-right. Videos carry a 22 dp scrim chip with a play glyph and duration, bottom-left. The status strip above the grid shows the same mark and says what it means. Month header sticky, Instrument Serif 22 sp, with its figures in the sans.

### Progress bar
- 8 dp, Mist High track, teal fill, 4 dp radius, 0.3 s linear width transition. Shown only while a copy or Load is running, never at rest.

### Signature: Photo viewer
Full-bleed, always dark. A Preview renders with a gentle 3 px blur (a Preview is honest about being smaller) and sharpens over 0.9 s when the Original is loaded (skipped with Reduce Motion). Top gradient holds back arrow, "Photo · date" in Title Medium and a "Preview" tag. Bottom gradient holds a "where is it" chip ("On your drive" / "On your phone") and three ring actions: Share, Info, Load · size. Rings are 56 dp circles at white 15%; the Load ring is mint (#7BD5DA with #003739). Load progress and calm errors appear in a rounded Load card (20 dp, #1D2424 at 93%; error variant uses the error container).

### Sheets, dialogs, toasts
- Bottom sheets: background fill, 28 dp top corners, 24 dp top padding, scrim behind.
- Dialogs are the system's: the delete confirmation and the folder picker are never restyled.
- Toasts are Material snackbars (8 dp radius, inverse surface), placed at the top while the viewer is open.

### History month row
88 dp tall: a 112 × 56 dp strip of four photos with 2 dp gaps and 10 dp corners, month and count in the middle, and a state line below. State is pill-shaped text with an icon: teal text for "Backed up and checked", amber pill for "Waiting for next backup", red-container pill for "Didn't match". A note card at the top of History states "as of" the last time the drive was seen when the drive is out.

## Do's and Don'ts

### Do:
- **Do** open every screen with one plain sentence, one count and one obvious button.
- **Do** say where the Original is in plain words ("On your drive", "On your phone"), always beside an icon.
- **Do** keep 48 dp minimum targets and 56 dp for the primary action; leave 8 dp between neighbours.
- **Do** theme through Material roles (`primary`, `surface`, `secondaryContainer`, …) so light, dark and large-text all resolve; never raw hex in a composable.
- **Do** treat a missing drive as a calm, ordinary state: amber or neutral chip, disabled button, an explanation of what to do.
- **Do** let the photo viewer go fully dark and let the gradients hold the text.
- **Do** honour Reduce Motion: drop the sharpen and rise animations to an instant cut.

### Don't:
- **Don't** use the stock Material purple baseline or any second brand hue.
- **Don't** use red for a missing drive, nor teal for a state that has not been verified. Teal means Backed up and verified.
- **Don't** show a full progress bar at rest, or repeat the same count twice on one screen.
- **Don't** use the words cloud, thumbnail, synced, or restore. Say Archive Drive, Preview, Backed up, Load.
- **Don't** put shadows on app chrome, nest cards, or use side-stripe accents.
- **Don't** hide the "where is it" message inside a sheet; it belongs on the photo.
- **Don't** make a phone layout stretch on wide windows; switch to a navigation rail.
