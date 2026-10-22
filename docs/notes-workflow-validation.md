# Notes workflow changes

Validated on the connected Xiaomi 2410CRP4CI (Android 16) on 2026-10-22. The checks below
distinguish automated coverage, interactive checks and remaining scenarios.

## Latest pass (Select tool: circle and move) — 2026-10-23, macOS — code only

- New **Select** tool in the pen bar (dashed-box icon, next to the eraser). Draw a loop around
  strokes; the ones with at least half their points inside are selected and boxed. Drag from
  inside the box (plus a 14-unit margin) to move them; the strokes follow the pointer live and
  land on release as one undo step. A tap or a loop elsewhere lets the selection go. A small
  bar floats above the box: Duplicate (copies offset 24 units and become the selection),
  Delete, Deselect. Leaving the tool clears the selection; moving across notebook pages works
  because strokes live in world units.
- Stylus and fingers follow the same rules as the eraser: in Hand mode a finger pans and the
  stylus circles; in Draw mode a finger circles too.
- `ui/editor/ink/Lasso.kt`: geometry (even-odd point-in-polygon, fraction inside, bounds,
  stroke translation that rebuilds the input batch with every pressure/tilt/time kept) and
  `LassoState`. `EditorViewModel.moveStrokes` / `duplicateStrokes`. Gesture branch and overlay in
  `InkSurface`, action bar in `InkEditor`.
- Tests: `LassoTest` (JVM, 6) pass; `LassoDeviceTest` (stylus loop selects, drag reports the
  world shift, tap elsewhere clears; translate keeps inputs) written, not run — tablet detached.

## Previous pass (wet ink after switching from the eraser) — 2026-10-22, macOS

Reported: after switching from the eraser to a pen, the first strokes sometimes showed nothing
while writing and appeared only later. Cause: the wet-ink component was composed only while a
brush tool was selected, so picking the eraser (or the hand) tore down its low-latency front-
buffer surface, and the next pen stroke had to wait for a new one; until the stroke was committed
to the dry layer, it drew nowhere. Fix: the component stays composed for as long as the page
takes ink; the eraser and pan branches already consume their touches in the Initial pass, so
the wet layer never sees them. Regression test `WetLayerSurvivesToolSwitchTest` checks the same
`InProgressStrokesView` instance serves pen → eraser → hand → pen. Built (unit tests pass);
the device test has not run yet because the tablet was detached at the time.

## Previous pass (Google Drive sync, editable PDFs) — 2026-10-22, macOS — code only

Written without the tablet, at the owner's request. See `docs/drive-sync.md`.

- Unit tests (JVM): `EditablePdfTest` (round trip, well-formed incremental update, declines
  cross-reference streams, name rules, deflate), `NotePayloadTest` (JSON round trip, restore
  decision rule), `SyncPlannerTest` (upload / move / rename / trash rules, unique names, query
  escaping, folder order, relative times). 61 unit tests pass.
- Device tests written, not run: `EditablePdfDeviceTest` (export → `PdfRenderer` opens it →
  import returns the same note on the same install → a foreign copy restores with the same ink,
  paper, appearance, pages and tags; a plain PDF still imports as pages; migration 7 → 8).
- Debug, androidTest and release APKs build. The release build is R8-minified with Play services
  auth and kotlinx.serialization on the classpath; the first device run should watch logcat for
  a stripped serializer.
- Not verified: the Google consent flow, Drive uploads, folder mirroring, restore. These need the
  OAuth client registered in Google Cloud first.

## Previous pass (open PDF and PowerPoint files from other apps) — 2026-10-22, macOS

Problem on the tablet: Fucknotes never appeared when a PDF or deck was opened. The manifest
already offered PDF and PPTX, but Xodo held an "always" default for `application/pdf` and
`application/vnd.ms-powerpoint`, and file managers hand .pptx over with that legacy type, which
the app did not list.

Changes:

- The app offers itself for every PowerPoint type (modern and legacy aliases), for a share of
  those, and for `application/octet-stream` or untyped files whose name ends in `.pdf`, `.pptx`
  or `.ppt`. The type-or-name rule lives in one place and is unit-tested.
- A real `.ppt` (binary) deck is accepted into the chooser and answers with the existing message
  to save it as `.pptx`; nothing else is pretended.
- Settings → "Opening documents" shows, per type, whether Fucknotes is the default, Android asks,
  or another app holds the default, with a Change button into that app's info page where
  "Open by default → Clear defaults" lives. It re-checks on every resume.

Verified on the tablet:

- Before: the resolver returned Xodo for PDF and .ppt. After installing, Android discarded the
  stale defaults on its own (candidate set changed) and `cmd package resolve-activity` returns the
  system resolver for both.
- A deck from Download launched with the legacy type shows the system "Open with" sheet with
  Fucknotes first (screenshot checked). Settings reports "Android asks which app to use" for PDF
  and PowerPoint.
- `DocumentIntentTest` (device): the activity is offered for PDF, PPTX, legacy PPT, an alias,
  octet-stream by name (content and file URIs), and both shares; not for a JPEG, a ZIP, or an
  image share. `DocumentTypeTest` (JVM) covers the type-or-name rule.
- Not exercised on device: the "another app holds the default" row state (would need picking
  Always for another reader; the code path is the same resolver call).

## Previous pass (light or dark per notebook) — 2026-10-22, macOS

Changes:

- The New notebook sheet has a Mode row: Light or Dark. It starts from the app-wide "Dark mode
  for pages" setting. The preview and the template cards show the night palette while Dark is
  chosen, and the paper-colour row steps aside (a night page is always the slate).
- The choice is stored with the note (`appearance` column, database version 7). Existing notes
  and imported documents keep following the app-wide setting, as before.
- The editor's ⋯ "Dark mode" switch and a Mode row at the top of the Paper sheet change the open
  note only; the Settings switch is now the default for notes that have not chosen.
- Shelf, search and trash thumbnails follow each note's own choice.
- The editor's chrome follows the open note (dark bars on a dark notebook even in a light app).

Verified on the tablet:

- The real database migrated 6 → 7 in place on first launch: 23 notes, all `FOLLOW`, no error in
  logcat, every note opened as before. A run-as snapshot was taken first
  (`build/tablet-backup-20261022-212403/appdata.tar`, also `~/fucknotes-tablet-appdata-backup.tar`).
- New notebook → Dark → Create: the page opens on the night slate with night swatches while the
  app-wide setting is off. ⋯ → Dark mode off turns that one note light (`LIGHT` in the row); the
  Paper sheet's Mode row turns it back (`DARK`), live, with the sheet staying open. The app-wide
  preference never changed. The shelf tile for that notebook is dark; its neighbours are not.
- Automated: `PageAppearanceTest` (JVM: resolution, editor state, draft default, converter) and
  `NotebookAppearanceTest` (device: the sheet's choice reaches the draft from both starting
  states, the paper-colour row hides on Dark, the value is stored and duplicated with the note,
  and a version-6 database with a row migrates to 7 with `FOLLOW`). Full device suite: 31 tests.

Known: a light page inside the dark app theme keeps the existing cool veil (`InkSurface`
`nightPaper`), so a "Light" notebook reads as it did before this change rather than pure white.

## Previous pass (chrome lock, page navigation, immersive sheets) — 2026-10-22, macOS

Changes:

- The pen bar's lock cycles the editor chrome: Full controls → Pen bar only → Reading mode → Full.
  Reading mode shows only the page, the bottom-right page counter and one unlock button. The old
  book icon is gone.
- Page navigation is a floating panel in the bottom-right corner in every chrome state.
- The zoom readout / pinch lock moved to the bottom-left; it no longer covers the highlighter
  swatches.
- Bottom sheets and dialogs keep the system bars hidden (the pages sheet used to drop the status
  bar over the toolbar).
- Settings is grouped into cards at a 760 dp maximum width. The home grid leaves room under the
  last row for the corner button; the rail's bottom rows sit on a wash so "Light mode" is legible.
- Night ink is stored in daylight: with dark mode on, the wet stroke is drawn in night colour so
  it matches the page, but the brush remembers the daylight brush it was made from and a finished
  stroke is handed back in that brush before it reaches the note. Before, the night colour was
  what got saved, the dry layer inverted it again, and the stroke changed shade on pen lift; it
  would also have shown inverted once dark mode was turned off. Pen swatches, the colour popup and
  the nib preview show each colour as it will appear on a night page (black reads as off-white).
  A canvas (endless sheet) also paints its background from the night palette now; before, only
  notebook sheets did, and a canvas stayed white at night. Strokes written in dark mode before
  this fix were saved with night colours and will look light with dark mode off; undo or erase
  and redraw them. Verified on the tablet by pixel: wet and dry stroke cores identical
  (220,222,229) on the slate (25,28,35); with night off the same stroke reads (24,27,34), the
  pen's stored black.
- Chrome alignment: every tool button draws its selected disc inside its touch target (disc =
  target − 8 dp), so the eraser, the lock and the reading-mode unlock all sit the same 8 dp in
  from a bar's rounded ends instead of a 48 dp disc filling the bar edge-to-edge at one end. A
  selected pen keeps its 30 dp colour disc and gains a 38 dp ring with a hairline of bar tone
  inside it, rather than shrinking. The note bar and the pen bar are both 56 dp tall with 48 dp
  targets; the zoom readout and the page counter share one height at the bottom.
- Performance, measured on the tablet with `dumpsys gfxinfo framestats` and a Perfetto trace of a
  shelf scroll (release build, now `profileable` so shell tools can trace it). Before: shelf frames
  20 ms median / 38 ms p99 — 0.2 ms on the UI thread, 11.6 ms on the RenderThread issuing GPU
  work, 5.7 ms waiting on the GPU; every frame re-recorded and re-drew the whole 3200×2136 screen
  although only positions change while scrolling. Fix: each tile and the rail are composited into
  an offscreen layer (`CompositingStrategy.Offscreen`), so a scroll moves textured quads. After:
  8 ms median / 23 ms p99, RenderThread 1.3 ms, GPU wait 2.6 ms. Also: paper patterns are one
  `drawPoints` call instead of a circle per dot (tiles and the editor), cover thumbnails are
  decoded at 512 px instead of full PDF resolution, one app-wide Coil loader with a quarter-heap
  memory cache and no crossfade, tile elevation shadows removed (HWUI re-tessellates spot shadows
  every frame a node moves), and two redundant full-screen sheet fills dropped. Editor scroll
  stays at 11–13 ms median; its 95th-percentile spikes are page-image texture uploads. Language
  is not the limit: the UI thread is idle; the cost was draw commands.
- "Light mode" in the rail also switches night colours off, since night mode kept the whole app
  dark and the toggle looked broken while it was on.
- Dark mode for pages: "Dark mode" with a switch at the top of a note's ⋯ menu (same setting as
  Settings → "Dark mode for pages", app-wide, persistent). Night colours are hue-preserving:
  lightness is inverted and capped at 88 %, saturation eased, so black ink reads off-white, blue
  stays blue, red stays red, and highlighters keep their colour (lifted if dark) instead of
  becoming smears. Paper is a dark slate (#181C24) with dimmed rules. Imported PDF and slide
  pages, home and search/trash thumbnails, the pages sheet thumbnails and attached pictures all go
  through the same colour matrix (invert + half-turn hue rotation, eased towards the slate). Night
  strokes are built once per stroke and cached (the old inversion re-tessellated every stroke
  every frame). Export and stored data are untouched.
- Back from a note: the system back gesture and back button leave the note only while every
  control is out (full chrome). In pen-bar-only and reading modes the writing surface opts out of
  the system's edge gestures and the back handler swallows a back press, so an edge swipe or a
  stray press cannot leave the page; the lock button is the way out. The zoom lock no longer
  excludes system gestures, which is what had been blocking the back swipe with the lock on.
- PowerPoint import: "Import PowerPoint" in the create menu (and Open with / Share for `.pptx`)
  converts the deck to a PDF on the device — backgrounds, master and layout decoration, inherited
  placeholder geometry and text styles, runs with bullets, alignment and auto-fit, pictures with
  cropping, preset shapes with theme fills and outlines, groups, connectors, tables; charts,
  SmartArt and embedded objects become labelled frames — then imports that PDF as a notebook, so
  slides are pages with ink above them and "Export as PDF" returns the deck. Legacy binary `.ppt`
  is refused with advice to save as `.pptx`. Code: `media/pptx/` (`OoxmlXml`, `PptxColors`,
  `PptxDeck`, `PptxRenderer`) and `media/PptxImporter.kt`.
- Polish: import and export show a progress dialog with a ring and the current step; the shelf
  grid has room between tiles and a count under the title; search and trash use the same row as the shelf list; the editor's bars fade and slide as
  the lock cycles.
- Finger navigation in every chrome state: one finger scrolls the page in "Pen only" and under
  the zoom lock (the lock now pins only the scale and the double-tap swap). Fingers that were on
  the glass while the stylus wrote are tracked as the palm until they lift, so a resting hand
  cannot drift the page or mark it after the pen comes up, in any finger mode.
- Manual shelf order (new default sort "Manual order"): a note keeps the place it was created in;
  editing never moves it. Press and hold a note still for 2 s to lift it, then drag it over
  another note to take that place, in the grid or the list; the shelf auto-scrolls near the
  edges. A long press still selects (applied on release). Before the long-press tick the shelf's
  scroll can take the finger as usual; from the tick on, the tile owns it — moves are consumed
  so a finger that rolls a millimetre cannot hand the gesture to the scroll — and a deliberate
  move after the tick lifts the note at once instead of waiting out the hold. Jitter never
  cancels. (The first version cancelled on drift and let the scroll steal the hold, which is why
  a real finger could not move a note.) Positions live in a new `position` column (database
  version 6, seeded from `createdAt`); moves land between neighbours and renumber only when two
  neighbours are adjacent. Dragging while a live sort is shown first adopts the visible order as
  the manual order so nothing jumps. Backups carry the position.
- Performance: the release build is now R8-minified and resource-shrunk (8.5 MB APK against
  30 MB debug) and signed with the debug key so it installs over the tablet's build; page image
  visibility is derived state with a one-viewport prefetch band and a heap-scaled bitmap cache,
  so scrolling an imported PDF no longer recomposes per frame or re-decodes pages; the eraser
  rejects strokes by cached bounds before the native coverage test; shelf tiles draw in their own
  layers so scrolling moves rendered tiles instead of repainting every thumbnail.
- A tab bar along the top of the editor (full-controls state) lists every open note, like a
  browser: the active tab is highlighted, each tab has a close cross, a plus returns to the shelf
  to open another note, and a chevron lists all tabs when more than one is open. Going back to the
  shelf no longer closes the tab; only its cross (or trashing / deleting the note, or backing out
  of a never-written note) does. Open tabs are saved in settings and restored after a restart,
  skipping notes that were trashed meanwhile. Up to 12 tabs are kept.
- Reading mode scrolls with a finger whatever the finger-mode preference or zoom lock says: one
  finger pans, a pinch moves but cannot change the scale while locked, a double tap never swaps
  tools, and a finger never draws. The stylus still writes.
- The New notebook dialog no longer asks for a number of empty pages; a notebook starts with one
  sheet and grows from the page arrow.
- Opening a note and leaving it no longer rewrites the row: `EditorViewModel.saveNow` skips the
  write when nothing differs, so "last edited" and the shelf order only change on real edits.

Executed:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon -Dkotlin.incremental=false
adb -s b86e61d8 shell am instrument -w -r com.example.fucknotes.test/androidx.test.runner.AndroidJUnitRunner
```

- 36 JVM tests passed (14 existing, `EditorChromeTest`, `OpenTabsTest`, `NotePositionsTest`, `PptxDeckTest`,
  `DarkReaderTest`, `PaperPatternTest`).
- 27 instrumented tests passed on the physical tablet, including `NightInkTest` (night brush
  round-trips to its daylight brush; a stylus stroke under dark mode is committed in the chosen
  colour), `HoldToDragTest` (jittery,
  still and slowly drifting two-second holds lift and drop; a move after the tick lifts at once; a
  swipe before the tick scrolls; long press selects on release; a tap opens), `PptxImportTest` (a hand-built
  deck renders its inherited title, a filled shape and a picture; a `.ppt` is refused), `NoteOrderTest` (manual order,
  moves, renumbering, adoption), the rewritten finger tests, `EditorTabBarTest`,
  `FingerInputTest.readingModeLetsFingersScrollWithPenOnlyAndZoomLockOn`,
  `EditorChromeTest.lockCyclesControlsPenBarAndReadingWithPageCounterAlwaysPresent` (each state
  shows exactly its controls; the arrow past the last page adds a sheet) and the locked-surface
  test that the previous pass had not yet run on the device.
- Screenshots on the tablet confirmed all three chrome states, page 1 → 2 via the bottom-right
  arrow, the pages sheet without a status bar, the overflow menu, settings cards, the New notebook
  dialog, and light mode.

Install note: the tablet build had been signed with the Windows debug keystore, so this Mac's
build could not update it. The app data (`databases`, `files`) was snapshotted with `run-as`,
verified locally with `sqlite3` (11 live notes, 10 in trash, 23 attachments), the package was
reinstalled, and the snapshot was restored before the first launch. All notes, folders and
settings were present afterwards; the snapshot is kept in `build/tablet-backup-20261022-164709/`
and `~/fucknotes-tablet-appdata-backup.tar` on the Mac.

## Earlier passes

## Implemented

- Compact editor controls, circular ink colors, custom eraser icon, and paper settings in the overflow menu.
- Right-aligned toolbar in both compact and expanded modes: three pen swatches, two highlighter presets, eraser, a finger-input cycle button and the control toggle. Standalone Pen/Highlighter buttons are removed. Swatches retain their saved colors and nib widths; tapping the selected swatch opens its editor.
- Each tap on the finger-input button cycles Hand / move page -> Write with fingers -> Pen only -> Hand, without opening a dropdown. Pen only is the previous Disabled mode: normal stylus writing with palm/finger touches on the page ignored, including drawing, scrolling, pinch and double-taps. The icon and accessible state describe the current mode. The preference persists across restarts and migrates the previous Draw with finger switch.
- Existing pen trays retain their slot indices and settings; missing pen/highlighter slots are supplemented for older builds.
- Removed note locking, pinning, reminders, type-conversion and note-info controls; removed Stationery and the duplicate home create button.
- Plus menu contains notebook, canvas, and PDF import.
- Collection/folder hierarchy with visible menus, recursive export, and a home light/dark toggle.
- Cooler page tones and display-only night shading, with stronger grid lines and larger paper dots. Night tint is below both live and committed ink, preventing the color from darkening after pen lift.
- Translucent highlighting on new and reopened strokes.
- Typed-note annotations remain visible while typing, scroll with their content, and have the same pen/highlighter/eraser controls as notebooks.
- PDF import from the picker, Open with, and Share; pages become image backgrounds with editable ink above them. PDF text and links are rasterized during import. Mixed PDF page sizes are fitted proportionally into the notebook's first-page size.
- Page images retain their position in notes, thumbnails, backups and PDF exports; their page menu supports repositioning, resizing and removal.
- Camera photos use the same validated, orientation-corrected import as gallery images; their pending destination survives rotation while the system camera is open.
- Combined PDFs, ZIPs of PDFs, and separate PDFs in a selected folder; nested exports preserve folder paths and disambiguate duplicate names.
- Export filenames also avoid conflicts with child folders, so a folder named `Lecture.pdf` cannot overwrite or block the note `Lecture`.
- Notebook exports use the page's point dimensions. Image exports show the first notebook page; use PDF for all pages.
- Adding a page keeps the camera still. Initial blank-page counts persist. Scroll bounds follow created pages.
- Older stored page counts are preserved during database upgrade, backup restore, editing and PDF export.
- Pending note saves complete when navigating away; saving and export share the persisted ink representation.
- Pen motion buttons track held switches across tip lifts and hover, ignore release packets, and reset after focus loss. Complete `ACTION_MULTIPLE` key events do not remain latched. Only the resumed editor consumes shortcuts.

## Executed checks

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon "-Dkotlin.incremental=false"
& $adb -s b86e61d8 shell am instrument -w com.example.fucknotes.test/androidx.test.runner.AndroidJUnitRunner
git diff --check
```

- 13 JVM tests passed: page/highlight rules, export names/paths, pen-motion buttons and camera bounds.
- 9 instrumented tests passed on the physical Xiaomi tablet (not the connected emulator).
- PDF workflow tests verify original imported title, page count/backgrounds, exported page dimensions and background pixels, including legacy page counts.
- Notebook rendering tests verify persisted ink and translucent highlighting over original PDF content, transparent PNG import, JPEG EXIF rotation, attachment preservation across duplicate-note removal, and readable nested ZIP PDFs with correct names/page counts.
- Finger-input tests exercise the real Compose surface: hand touch pans without ink; pen-only touch ignores swipe, double-tap and pinch; a stylus MotionEvent stream still creates ink with touch disabled even when a palm pointer lands first and remains down; finger-writing mode creates ink without panning.
- Toolbar tests verify exactly three pen swatches and two highlighters, absence of standalone Pen/Highlighter buttons, selection of both highlighters and a pen, and repeated one-tap cycling through all three modes in compact and expanded layouts without a menu or a pen-selection change.
- These Android tests use isolated surfaces or in-memory databases and do not edit the owner's notebooks.

## Interactive tablet checks

- Existing notes retained after each APK update. A pre-update databases/files snapshot is stored locally under the ignored `build/tablet-validation-20261022/` directory.
- Created the isolated `Tablet_Validation_20261022` notebook with three pages. Adding a page kept the current page in view. The owner subsequently added handwriting/pages to this notebook; it is retained.
- Plus menu shows New notebook, New canvas and Import PDF. Removed editor menu actions are absent; Paper is in the overflow menu.
- Confirmed compact controls, right-aligned toolbar, fully visible swatches and increased dot size in tablet screenshots.
- Selected pen-only touch behavior, left the editor, restarted the app and reopened the notebook: the preference was retained.
- The owner confirmed the physical Xiaomi pen buttons work. Primary press/release was observed as KEY_PAGEDOWN on the Xiaomi Focus Pen Keyboard device.
- The owner confirmed stroke color stays the same after pen lift following the night-tint fix.
- Physical pen input and Android-injected stylus streams are recorded separately; synthetic input alone does not validate the Xiaomi hardware buttons.

## Remaining manual coverage

These scenarios are not implied by passing compilation or the automated tests:

1. End-to-end system picker flows for PDF/image import and all three folder export modes, including cloud storage providers, cancellation and unwritable destinations.
2. Large, damaged/password-protected and mixed-size PDFs; imported PDF text/links remain rasterized and mixed pages fit the first page's notebook dimensions.
3. Image placement/resizing/removal from the page menu and camera rotation during capture.
4. Long writing sessions, stylus-button holds/repeated presses across focus changes, and live highlight appearance with every paper/theme combination.
5. Full phone/split-screen, large-text, portrait, and light-mode layout coverage. Current interactive screenshots are from the landscape Xiaomi tablet in dark mode.
6. Backup restoration of older notebooks, and multi-page typed-note annotations while scrolling/editing text.
