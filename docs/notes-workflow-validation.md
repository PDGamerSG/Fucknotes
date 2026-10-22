# Notes workflow changes

Validated on the connected Xiaomi 2410CRP4CI (Android 16) on 2026-10-22. The checks below
distinguish automated coverage, interactive checks and remaining scenarios.

## Latest pass (chrome lock, page navigation, immersive sheets) — 2026-10-22, macOS

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
- PowerPoint import: "Import PowerPoint" in the create menu (and Open with / Share for `.pptx`)
  converts the deck to a PDF on the device — backgrounds, master and layout decoration, inherited
  placeholder geometry and text styles, runs with bullets, alignment and auto-fit, pictures with
  cropping, preset shapes with theme fills and outlines, groups, connectors, tables; charts,
  SmartArt and embedded objects become labelled frames — then imports that PDF as a notebook, so
  slides are pages with ink above them and "Export as PDF" returns the deck. Legacy binary `.ppt`
  is refused with advice to save as `.pptx`. Code: `media/pptx/` (`OoxmlXml`, `PptxColors`,
  `PptxDeck`, `PptxRenderer`) and `media/PptxImporter.kt`.
- Polish: import and export show a progress dialog with a ring and the current step; the shelf
  grid has room between tiles and a count under the title (with the hold-to-move hint in manual
  order); search and trash use the same row as the shelf list; the editor's bars fade and slide as
  the lock cycles.
- Finger navigation in every chrome state: one finger scrolls the page in "Pen only" and under
  the zoom lock (the lock now pins only the scale and the double-tap swap). Fingers that were on
  the glass while the stylus wrote are tracked as the palm until they lift, so a resting hand
  cannot drift the page or mark it after the pen comes up, in any finger mode.
- Manual shelf order (new default sort "Manual order"): a note keeps the place it was created in;
  editing never moves it. Press and hold a note still for 2 s to lift it, then drag it over
  another note to take that place, in the grid or the list; the shelf auto-scrolls near the
  edges. A long press still selects (applied on release). The hold is cancelled only when the
  shelf's scroll takes the finger or the finger leaves the tile — never by jitter, which is what
  made the first version fail under a real finger. Positions live in a new `position` column (database
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

- 28 JVM tests passed (14 existing, `EditorChromeTest`, `OpenTabsTest`, `NotePositionsTest`, `PptxDeckTest`).
- 23 instrumented tests passed on the physical tablet, including `HoldToDragTest` (jittery and
  still two-second holds lift and drop; a swipe during the hold scrolls; long press selects on
  release; a tap opens), `PptxImportTest` (a hand-built
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
