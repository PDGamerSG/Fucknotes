# Notes workflow changes

## Doubled shape outlines fixed — 2026-11-13, Xiaomi tablet

The owner reported shapes looking like two lines. The default hand-drawn generator deliberately
retraced lines/shafts with an offset and went round boxes/circles twice. The tablet's open page
visibly showed doubled circle contours. Earlier tests enforced those double passes, so passing
them did not establish the visual requirement.

Changed only the hand-drawn shape geometry and its regression checks: one line/shaft, one
rounded rectangle perimeter, and one closed periodic ellipse. Shared endpoints remove gaps at
corners; an arrow returns along the identical wing samples before drawing the other wing,
keeping the shape one editable stroke without a second offset contour. Clean shapes and
existing saved ink keep their stored geometry.

Evidence in `build/tablet-validation-20261113-single-outline/`:

- Before the fix, four updated geometry tests failed (`red-unit.txt`). Two tablet regressions
  failed (`red-device.txt`): a 120 × 100 rectangle had 832 units of outline, and the native
  renderer showed two arrow-shaft crossings at width 0.5.
- After the fix, all **127 JVM tests** pass, and **17 of 17 drawing-tool tablet tests** pass
  (`green-build.txt`, `device-final.txt`). Native rendering checks all four hand-drawn shapes
  before/after storage at widths 0.5 and 2.5; the existing tests cover clean corners/arrowheads,
  selecting/moving/undoing shapes, palm/pinch ownership, canceled shapes, highlighter and paper.
- The optimized release builds successfully (`release-build.txt`).
- An updated app-data backup and original installed APK were saved before tests. Test notes are
  disposable and the owner's preferences are restored afterwards.
- Final SQLite integrity is `ok`. All 34 original notes retain the same titles, body/ink hashes
  and edit timestamps, with no extra/missing notes (`preservation-check.json`). The corrected
  optimized release was installed in place, its installed SHA-256 matches (`installed-build.json`),
  and it launches successfully through the Device panel.

## Attached-tablet drawing and paper follow-up — 2026-11-13, Xiaomi 2410CRP4CI

Continued on the physical Xiaomi `b86e61d8` (Android 16, portrait 2136 × 3200). Existing
uncommitted toolbar and hand-drawn shape work was retained. Evidence lives in
`build/tablet-validation-20261113-followup/`.

Reproduced before changing production code:

- A canceled shape was committed instead of discarded.
- A pinch left shape drawing canceled while the hand remained down; a new stylus contact
  could not draw until all fingers lifted.
- Earlier temporary red strokes vanished during the next stroke. The existing 400 ms hold
  plus 500 ms fade also left too little reading time.
- Choosing paper in a dark note left the page black. Light pages also carried a blue wash
  that changed the selected color.

`red-device-tests.txt` records four failing tablet regressions. `red-build.txt` records both
highlighter timing tests failing. The fixes reset gesture ownership on a new stylus contact,
discard Compose cancellation events (including selection drags), pause fading while writing,
restart all remaining trails from the latest pen lift, and show picked paper colors immediately
by choosing light appearance. The Paper sheet offers color swatches in dark mode as well.
The temporary highlighter now holds for 1.5 s after writing stops, then fades over 1 s.

The actual editor's ⋯ → Note colour and ⋯ → Paper flows are exercised by
`bothOverflowColorPickersRepaintTheNotebookImmediately`, including rendered pixels and stored
appearance. The tests use disposable scratch notes and restore the owner's preferences.
Synthetic stylus/palm streams exercise the tablet's input path; they do not verify physical
pen buttons or the feel of a human writing session.

Before tests, the installed APK was saved and the app was updated in place without uninstalling
or clearing data. `appdata-before-tests.tar` contains 65 entries and 35,595,661 uncompressed
bytes. Its SQLite database passed `integrity_check`; `notes-before.json` records all 34 existing
notes, including trash, with ink/body hashes for the final preservation check.

Final checks:

- **127 JVM tests passed** (`green-build.txt`).
- **68 of 68 instrumentation tests passed on the physical tablet** (`instrumentation-final.txt`).
  The first full run was 67 of 68 because the new long-stroke test froze the Compose clock
  before tool selection rendered. Waiting for the selection fixed the test; its isolated
  recheck and the complete final run both passed. No production workaround was added for it.
- The optimized release APK and `:app:lintDebug` built successfully (`release-and-lint.txt`);
  lint reports zero errors. Existing toolchain deprecation/R8 metadata warnings remain.
- After testing, SQLite integrity is `ok`; all 34 original notes have exactly the same title,
  body/ink hashes, ink lengths and `updatedAt` as before, with no extra or missing notes
  (`preservation-check.json`).
- Installed `app/build/outputs/apk/release/app-release.apk` in place and relaunched through
  the Device panel's agent command. Its SHA-256 matches the installed APK (`installed-build.json`),
  and the shelf opens with the original 21 live notes. The tablet is left on the shelf.

Commands used Gradle's assemble tasks and explicit `adb install -r` / `am instrument`; no
`connected*Test`, uninstall, or data clearing. Version remains 1.2.0 (code 3); no release was
published.

## The app would not start on the second tablet — 2026-11-13, macOS, Lenovo TB373FU

The owner reported the app "not working" on a tablet. The tablet plugged in was not the Xiaomi
but a Lenovo TB373FU (serial `HA29MSA9`, Android 16, 1840 x 2944 at 340 dpi) carrying the
published **1.1.0**, installed from its APK on 2026-11-10.

- Its crash buffer held the same failure three times that morning, each at start:
  `android.database.sqlite.SQLiteBlobTooBigException: Row too big to fit into CursorWindow
  requiredPos=0, totalRows=1`. A notebook's ink had passed the roughly 2 MB a cursor window
  holds, and 1.1.0 reads the row through one. This is the crash described under "Startup crash
  from a large notebook" below; the fix (`BundledSQLiteDriver`) shipped in 1.2.0 and this
  tablet had never been given it.
- The current release build (1.2.0 with the day's uncommitted work, same signing certificate
  `a889393c…`) was installed over it with `adb install -r`. First install time is unchanged, so
  the data stayed; the database went from schema 6 to 8 through the two additive migrations.
- It then started in 201 ms into the notebook it had been crashing on — 13 pages, handwriting
  on screen — and the shelf listed both notes and both collections. No crash entry after the
  install. The owner took the tablet back at that point, so nothing more was driven.

Not checked: no copy of this tablet's data could be taken first (a release build cannot be read
with `run-as`), the shelf tiles were plain black two seconds after a cold start and were not
looked at again, and the second notebook was not opened by the tester.

## Two bars on one row, toolbar items, hand-drawn shapes, lit highlighter, hold-to-export — 2026-11-13, macOS, emulator

The owner's requests after the tablet run, built once the tablet had been unplugged. **None of
this has been on the tablet.** Evidence is in `build/emulator-validation-20261113/`.

**What changed**

- The note bar and the pen bar are two pills on one row in both orientations, after the
  owner's reference screenshots. Upright on the tablet the pen bar is: eraser, a "More tools"
  button holding Select / Finger input / Shapes / Red highlighter, four pens, two highlighters,
  lock. Lying down, every tool is on the bar. (`portrait-two-bars-one-row.png`,
  `landscape-two-bars-one-row.png`.)
- ⋯ → "Toolbar items": a switch for every tool and every pen and highlighter on the bar, kept
  for all notes. The last pen cannot be switched off (`toolbar-items-dialog.png`).
- Shapes come out hand-drawn — each edge twice over, soft box corners, a circle that goes round
  twice — unless "Clean lines" is switched on in the Shapes menu (`hand-drawn-shapes.png`,
  `shapes-menu-clean-lines.png`). No diamond, at the owner's word.
- The red highlighter is a lit tube with a pale core and is gone 0.9 s after the pen lifts; it
  was 2.5 s (`red-highlighter-lit-tube.png`).
- Holding a note on the shelf shows "Export PDF" by name in the selection bar, and one note
  goes straight to the save dialog (`hold-note-export-button.png`).

**Checks**

- Red before green: `ToolbarLayoutTest`, `SketchyShapesTest` and the new highlighter timings
  failed against stubs (16 of 22), then passed. **127 JVM tests pass.**
- A temporary API 35 tablet emulator, since deleted. At its own size (1280 x 800 dp, lying
  down): **63 of 63 instrumentation tests** (`instrumentation-default-size.txt`). With the
  display set to the tablet's upright size (777 x 1164 dp): **62 of 63**
  (`instrumentation-tablet-portrait-size.txt`).
- The one failure, `pinchBeforeChoosingShapeDoesNotSwallowItsFirstStroke`, is not from this
  work: it fails the same way on the commit before it (bb2a73a) at that display size. The
  injected pinch zooms the canvas 6.25 times instead of 1.2 (logged scale 1.375 → 8.596), so
  the stroke that follows lands off screen. It passes at the emulator's own density (2.0) and
  passed on the tablet (2.75). Whether a real pinch misbehaves on a low-density screen was not
  looked into.
- By hand on the emulator, with taps and stylus strokes over adb: the two bars share a row
  upright and lying down; "More tools" opens Shapes; a box, circle, arrow and line drawn
  hand-drawn; the highlighter showed 1,485 red pixels mid-stroke and none 1.6 s later; two
  items switched off in "Toolbar items" left the bar; holding a note and tapping "Export PDF"
  opened the system save dialog and wrote a 52,565-byte PDF ("Saved 1 note").
- Found on the emulator and fixed: a pen put away while the red highlighter was in hand moved
  to the end of the bar instead of leaving it (the bar always shows the tray's selected slot).
  `aToolPutAwayLeavesTheBarAndComesBack` covers it.
- The optimized release APK builds: `app/build/outputs/apk/release/app-release.apk`.

**Not checked**

- Anything on the tablet, or with the physical pen. The tablet still runs the build of
  bb2a73a. Install with `adb -s b86e61d8 install -r app/build/outputs/apk/release/app-release.apk`.
- How the hand-drawn shapes and the 44 dp buttons feel under a real pen at 2.75 density.
- The last tablet test run left one unreferenced 24 KB image in `files/media` and four empty
  `large-drawing-*.db.lck` files; they can only be removed while a debug build is installed.
- Version is still 1.2.0 (versionCode 3), the same as the published release.

## One black dark mode, exports that follow the note, ink colour repair — 2026-11-13, macOS, tablet

First run of the 1.2.0 work on the owner's tablet (it had 1.1.0), plus the owner's requests of
the day. Evidence — screenshots, the exported PDF, the instrumentation logs — is in
`build/tablet-validation-20261113/`; the data backup taken before any test ran is
`build/tablet-backup-20261113-pre-test/appdata.tar` (56 files, checksums matched the device).

**What changed**

- Dark mode is one switch, "Dark mode" in the editor's ⋯ menu, and its pages are true black
  (`NightPalette.paper`). The separate "Pitch black pages" switch, the slate, the Light / Dark /
  Pitch black row in the New notebook dialog and in the Paper sheet, and the dark paper swatch
  are gone. A notebook, canvas or text note the user creates is light.
- Exports ask nothing about light or dark: a note in dark mode exports black pages with night
  ink, any other note exports light. "Export as PDF" goes straight to the share sheet. Home
  exports and Drive uploads resolve each note on its own.
- Ink that changed shade partway through a line: one notebook (id 29) held 66 strokes of 6,989
  stored in their *night* colour, left by the pen-lift bug fixed in c4dc023 — 51 blue, 11 red,
  4 black. They were inverted a second time on a dark page and nearly vanished in any export.
  `InkCodec.parse` now reads such a stroke back in the pen it was written with. Nothing written
  since the fix carries one: 3,829 strokes in the four later notebooks were checked.
- Dot paper exported 2.6 pt dots; they are 1.2 pt now.

**A stale working tree, and what it cost**

This folder still held an older, uncommitted copy of the 1.2.0 work when the day started;
`origin/main` already had the released commit (c1eca03, tag `v1.2.0`). The build first put on
the tablet therefore lacked the release's raw-input-model fix for shapes, and a dragged box came
out as a blob with one square corner (`shapes-before-fix.png`). An interim fix written here —
replaying the outline slowly — was dropped when the day's work was moved onto `origin/main`:
the released fix (`InkCodec.brushForShape`) does the same job and also repairs shapes on decode.
`shapes-after-fix.png` shows the interim build, not the final one; the final build's shapes are
covered on the tablet by `renderedShapesKeepTheirCornersAndArrowWingsAfterReopening`.

**Checks**

- Red before green: `NightSlipTest` (4 of 6 failed against a stub), its black-ink case (failed
  after `NotebookRenderingTest` caught pure black being read as the night form of white), and
  `PdfWorkflowTest.dottedPaperExportsFineDots` ("was 2.5").
- **107 JVM tests passed.** **60 instrumentation tests passed on the tablet** on the final tree
  (`instrumentation-merged.txt`). Earlier runs of the day, on the stale tree: 57 of 58 with the
  black-ink failure (`instrumentation-first-run.txt`), then 60 of 60 (`instrumentation-final.txt`).
- The repair was run over the real ink of notebooks 29, 31, 32, 33 and 34 on the JVM: 66 strokes
  changed in 29 (`2A85D5→2386DC` ×51, `B71E33→E84158` ×11, `DBDEE6→171B23` ×4), none in the
  others (`night-colour-repair-report.txt`).
- On the tablet, with taps and stylus strokes injected over adb into a scratch notebook
  (`ClaudeTest1113`), on the interim build: the New notebook dialog shows no mode row and four
  light papers; the notebook opens light with the app in dark theme; the red highlighter shows
  at once and is gone six seconds later with nothing stored (9,938 red pixels, then 0); the ⋯
  menu has the single Dark mode switch; "Export as PDF" opened the system share sheet with no
  dialog and the PDF is a black page with fine dots and night ink (`export-dark.pdf`).
- After the first runs the database passed `integrity_check`, the 32 earlier notes held the same
  8,945,865 characters of ink as in the backup, and the three images and eight empty lock files
  the tests left behind were removed.

**Not checked**

- Nothing was done with the physical pen by the tester. The owner was writing in the scratch
  notebook during the run — their strokes are in it, and it has been left in place — which also
  swallowed some injected taps: the switch back to light followed by a second export was not
  observed by hand. `EditorDrawingToolsTest.exportComesOutInTheNotesOwnMode` covers it.
- The final tree was not driven by hand on the tablet, only by the instrumentation suite.
- A light page still carries the editor's cool wash while the app theme is dark; that is how it
  was and it was not part of this request.
- Home export and Drive upload were not driven on the tablet; Drive has never been signed in.
- `svc power stayon usb` was set for the test run and then turned off; whatever "Stay awake"
  was before is not known.
- The version is still 1.2.0 (versionCode 3), the same as the published release, although the
  app now differs from it. Bump it before the next release.

## Pitch-black pages and drawing tools — 2026-11-10

- Saved pitch-black appearance is available in notebook creation, Paper, and the editor menu.
  Paper previews, typed notes, imported pages, page thumbnails, and dark exports use the choice.
- Shapes are an explicit tool with line, arrow, rectangle, and circle choices. Drag previews and
  saved vectors share geometry; ordinary freehand strokes are preserved. Shape metadata survives
  storage, selection, moving, duplication, recolouring, undo/redo, and editable PDF export.
- Temporary red highlights remain entirely in memory, hold for 1.5 seconds after pen lift and
  fade over one second. Each completed trail has its own timer and never changes saved ink,
  exports, dirty state, or undo history.
- Compact mode includes Undo. Narrow windows use separate tool and pen rows so Select, the
  finger control, Shapes, the red highlighter, and the lock stay reachable after swatch scrolling.
- Device regressions cover real toolbar/pointer/ViewModel/storage flows, pixel checks for black
  paper and disappearing red ink, shape selection and movement, compact Undo, a pinch followed by
  a shape, two pen strokes with a continuously resting palm, and pitch-black editable PDF output.
- The old shape-recognition notes below describe an earlier draft. The released behaviour uses
  the explicit Shapes tool instead of rewriting handwriting automatically.
- A release-build smoke check caught the stock marker smoothing sparse shape vertices. A pixel
  regression failed at the rectangle's upper-right corner before generated shapes were switched
  to Ink's raw input model. It now checks rectangle corners, both arrow wings, circle extrema,
  and line endpoints in freshly drawn and reopened vectors.
- The final optimized APK was installed over the existing app without clearing its data. An
  interactive smoke check confirmed pitch-black paper, a square-cornered rectangle, visible
  Select and red-highlighter controls, and working Undo in the compact toolbar.
- Final workspace verification: **98 JVM tests, 0 failures; 60 Android 15 emulator tests,
  0 failures**. Debug, instrumentation, and optimized release builds succeeded. Release lint
  completed with 0 errors (85 existing/toolchain warnings). `git diff --check` passed.
- `apksigner` verified APK Signature Scheme v2. The certificate SHA-256 matches published 1.1.0:
  `a889393c409dcdbe1980a4e332772b1392ef3618b034a9a7fb66e994c6cec8c0`.
  The APK declares `com.example.fucknotes`, version code 3, version name 1.2.0, minimum SDK 26.
  The physical Xiaomi tablet and pen were not connected during this pass.

Validated on the connected Xiaomi 2410CRP4CI (Android 16) on 2026-10-22 through 2026-10-24. The checks below
distinguish automated coverage, interactive checks and remaining scenarios.

## Editor controls, rough shapes and editable recolouring — 2026-10-25, macOS, emulator

- Zoom percentage/lock and page arrows fade to 18% opacity after 2.6 seconds idle. Page gestures
  and control taps restore them with a short fade; their full touch targets remain available.
- Rough single strokes are recognised as lines, arrows, rectangles or circles and replaced with
  compact vector paths. Rounded box corners are supported. Unsupported triangles, diamonds and
  retraced scribbles keep their geometry. Arrow recognition does not depend on sample counts,
  and cleanup retains both complete arrowhead wings.
- Ink editors offer **Quick highlighter** in the overflow menu. It reveals a top-left toggle;
  switching it off restores the previous pen slot, including its colour and width.
- Lasso selections have a colour action using the ink palette. The page palette's transparent
  default is excluded. Recolouring supports undo and retains vector inputs, brush properties,
  tool tags and optional shape metadata; moving/duplicating shapes retains their metadata too.
- Notes continue storing compressed vector inputs and editable brush/shape metadata. Default
  JSON fields and absent shape tags are omitted. Existing notes remain readable; no schema
  migration or flattened page images are introduced by these features. Storage still grows
  with stroke count and attached images.
- Regression proof: four geometry tests failed before the boundary/area fix, the dense-head arrow
  test failed before removing its sample-count restriction, and an Android cleanup test showed
  a wing ending at x=98 instead of x=82 before preserving the whole wing. All now pass.
- Final verification: **92 JVM tests passed**, plus **11 Android tests passed** on an isolated
  API 35 tablet emulator: `InkDecodeTest`, `EditorViewportRestorationTest`, `LassoDeviceTest`,
  `EditorChromeTest` and `NightInkTest`. These cover vector metadata/recolour round trips, arrow
  cleanup, page-6/zoom restoration, selection movement, editor chrome and dark-ink handling.
  Debug, instrumentation and optimized release APKs built successfully; `git diff --check`
  passed. Build/test logs are in `build/editor-validation-20261025/`.
- The tablet remains disconnected and untouched. The installable release is
  `app/build/outputs/apk/release/app-release.apk`. The new idle timing, shortcut interaction and
  colour-picker flow have not been interactively checked with the owner's physical pen/tablet.

## Keep the PDF page through rotation — 2026-10-24, macOS, emulator

The editor's camera used ordinary `remember`, so activity recreation reset the PDF/notebook to
page 1. It now saves the world-space viewport centre and zoom per editor entry. Restoration waits
for valid layout bounds, and resizing keeps the same document position. This also preserves canvas
pan/zoom and handles density changes without storing view state in the note's content.

- The resize regression failed against the old camera before the fix. All 81 JVM tests now pass,
  including recreation in both orientations, canvas pan/zoom, repeated recreation before layout,
  page-count growth, and initial zero-size layout.
- Seven editor instrumentation tests passed on a temporary emulator at tablet dimensions: saved
  viewport restoration, editor chrome, finger/stylus input, and tab navigation. The existing tab
  visibility test assumes tablet width; its initial phone-width run failed before rerunning wide.
- Debug, test and optimized release APKs built successfully. In the release app, imported a
  ten-page PDF, navigated to page 6, rotated landscape → portrait → landscape, and confirmed page
  6 and 213% zoom throughout. Screenshots and UI hierarchies are in
  `build/rotation-validation-20261024/`. The app stayed alive with an empty crash buffer.
- The physical tablet was disconnected. Per the owner's instruction, verification continued on
  the emulator; this build has **not** been installed on the tablet. The update APK is
  `app/build/outputs/apk/release/app-release.apk`.

## Startup crash from a large notebook — 2026-10-24, macOS, tablet

The release repeatedly crashed with `SQLiteBlobTooBigException`. Note 29 carried 2,096,971
characters of ink JSON (2,618 strokes); reading the full row exceeded Android's roughly 2 MB
`CursorWindow`. Restoring the saved tab list (`29,23`) read that row immediately at startup;
shelf, editor and export queries were also affected.

Room now uses `BundledSQLiteDriver`, which reads SQLite statements directly without a cursor
window. The schema remains v8 and existing ink JSON is unchanged. Repository transactions use
writer connections, and every existing migration supports both native and legacy connections.

- The isolated 3 MB notebook regression failed with the original driver and passes with the fix.
- Four new device tests cover full ink reads/saves/duplication/reopen, migration from v1 to v8,
  notebook import/replacement/reorder, and rollback when an attachment insert fails.
- All 75 JVM tests and 18 existing tablet import/export/appearance/order tests passed.
- The first Gradle-managed test run crashed during application initialization and its cleanup
  removed the installed package. Recovered all 27 note rows and settings from the current
  snapshot, and all 33 referenced images from existing local backups. The complete recovery
  archive is `build/tablet-backup-20261024-crash-recovery/recovery.tar`.
- The optimized release APK built and installed successfully over the restored debug build.
  A cold launch showed all 14 active notes; opening the affected 16-page notebook rendered its
  original handwriting. The release process remained alive and the crash buffer stayed empty.
  Screenshots are `home-release.png` and `notebook-release.png` in the recovery directory.

Run isolated database checks with a plain test application so the owner's tabs and shelf never
start. Assemble and install directly on a tablet with user data; the Gradle connected-test task
can uninstall the application during cleanup.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  -PandroidTestRunner=com.example.fucknotes.DatabaseTestRunner
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.example.fucknotes.LargeDrawingCursorTest \
  com.example.fucknotes.test/com.example.fucknotes.DatabaseTestRunner
```

## Latest pass (tablet verification of the 2026-10-22/23 work) — 2026-10-23, macOS, tablet

The tablet had run the 2026-10-22 22:22 build (1.1.0) through all of yesterday's and today's
fixes; everything below is the first run of that code on the device. Data was snapshotted with
`run-as` before the first launch (`build/tablet-backup-20261023-181348/appdata.tar`); the DB
migrated to v8 and all 14 notebooks are intact.

- Full instrumentation suite: 44 tests, 4 failed on first run, all four were test bugs, fixed in
  `bb00e80` and green afterwards: the lasso test assumed screen pixels were world units (the
  surface sets the camera's density, 2.75 here); the wet-layer test looked for
  `InProgressStrokesView` but ink 1.0.0's compose wrapper hosts `InProgressShapesView`; the
  mid-stroke test asserted before the asynchronous stroke handoff; the toolbar test counted three
  pen swatches. The app code did not change.
- Reported again today: "switch from eraser to blue, the pen output shows only after some time".
  Scripted on the real editor with a scratch notebook: draw, erase (checked: the row is paper),
  switch back to blue, write a 2.5 s stroke and screenshot while the pen is still moving. Wet ink
  was on the page mid-stroke in 24 of 24 cycles — 12 switching through the toolbar, 12 through a
  stylus-sourced `KEYCODE_PAGE_DOWN` key, which is what this tablet makes of the Focus Pen's
  primary switch. Not reproducible on this build; the report matches the wet-layer rebuild fixed
  in `8fe1373`, which the tablet did not have until today.
- Rename sits in ⋯; Export as PDF asks Light/Dark; both PDFs pulled from the app cache and
  rendered: dots are faint and sheet-sized, the dark page is night slate with night-blue ink.
- Cold open of "Db and caching" (23 pages of ink plus an image) recorded with `screenrecord`:
  home → blank desk at +0 ms → full page at +230 ms from the tap landing. No title / "Start
  writing" frame.
- Select tool on the real UI: the dashed box and the Duplicate/Delete/Deselect bar appear after
  circling a line; dragging inside the box moved the stroke (pixels checked before and after).
- Release build installed over the debug one at the end (`app-release.apk`, 18:55), launched,
  home shows 14 notes. The scratch notebook "Claude repro" was moved to Trash.

Driving notes for next time: `input stylus keyevent KEYCODE_STYLUS_BUTTON_PRIMARY` never
reaches the app (the system keeps stylus button keycodes); `input stylus keyevent KEYCODE_PAGE_DOWN`
does and toggles the eraser once. `input stylus motionevent DOWN/MOVE/UP` works for a lasso loop
but each command is its own process and later finger taps on the toolbar were sometimes dropped
afterwards; for a mid-stroke screenshot use a long `input stylus swipe … 2500` in the background
instead.

## Previous pass (pen button toggles, tool change mid-stroke) — 2026-10-23, macOS — code only

Reported: switching eraser ↔ pen with the Xiaomi Pen's button sometimes leaves the pen "not
working"; toolbar taps are fine. Two causes addressed, neither reproducible without the pen:

- One physical press can arrive as several packets — a key down from the HID endpoint and the
  key up from the paired keyboard endpoint, a key and a stylus-button motion packet, a repeat
  while held. The old bookkeeping emitted again when the up did not match the down's identity,
  so the eraser was toggled on and straight off. `input/PenPresses.kt` now reduces every pattern
  to one press (held per button across endpoints and streams, a 300 ms grace for a second up,
  a 1.5 s stale window so a lost up never kills the button). JVM-tested (8 cases).
- A button pressed while the pen is on the glass used to switch the tool for the stroke in
  progress: the eraser branch started consuming the stream and the wet stroke was cancelled, or
  the pen branch stopped consuming and the ink layer got moves without a down. Now a gesture
  keeps the tool it started with; the new tool takes the next stroke
  (`ToolSwitchMidStrokeTest`, device, not run).

The "colour changes after 5–10 minutes" report matches the weak-map bug fixed in `c4dc023`
(collection timing); that build is not on the tablet yet.

## Previous pass (export: subtler dots, real dark export, light/dark choice) — 2026-10-23, macOS — code only

- Notebook sheets in a PDF drew dots 4 pt across and rules 1.5 pt wide, opaque — twice what the
  screen shows. Now 2.6 pt dots at 78 % alpha and 0.9 pt rules, scaled with the sheet.
- The dark export drew the stored (black) ink onto a dark sheet. It now renders the page as dark
  mode shows it: night slate, night rules, every page image through the night matrix, every
  stroke in its night colour — for notebook sheets and canvas pages alike.
- ⋯ → Export as PDF asks Light page / Dark page (preselected from how the note is being read),
  then shares. Export as… keeps its checkbox, also preselected. The shelf's export dialog gains a
  "Dark pages" checkbox that applies to combined, ZIP and folder exports.
- Not run on the tablet (detached). Unit tests pass.

## Previous pass (rename, no typed-note flash, faster open) — 2026-10-23, macOS — code only

- ⋯ → **Rename**: dialog with the current name selected; Done or the Rename button saves it, and
  the tab strip follows.
- The typed-note frame (title field, "Start writing…") no longer shows while a notebook loads:
  the editor paints the desk until the row is read, then the right editor.
- Opening: the page shows as soon as the row is read; the ink is built off the main thread in
  batches (24 strokes first, doubling to 256) across the Default dispatcher's threads and appended
  as each batch lands, so the first lines are on the page within a few frames. A save while the
  ink is still arriving keeps the stored JSON (never writes a half-built list), and a note left
  mid-load is never discarded as empty. The last four notes' decoded strokes are kept
  (`InkCache`), so coming back through the tab strip is instant.
- Tests: `InkDecodeTest` (device): batched parallel decode equals the whole decode and re-encodes
  to the same JSON; the cache answers only for the JSON it was built from. Not run: tablet
  detached. Unit tests 67 pass.

## Previous pass (pen colour still changing on lift, now and then) — 2026-10-23, macOS — code only

Cause: the night-brush → daylight-brush map was a `WeakHashMap`, and brushes compare by value.
The second stroke with the same pen made an equal brush; the map kept the *first* brush object
as the key; nothing held that object any more; the collector dropped the entry while the second
stroke was still being written; the lookup missed; the night colour was stored and the dry
layer inverted it again. Whether it happened depended on when a collection fell — hence
"sometimes". Fix: both brush maps (`daylightBrushes` in DarkReader.kt, `brushTools` in
InkCodec.kt) are bounded, strongly-keyed LRU caches, and the tool is also recoverable from the
brush family. Device test added to `NightInkTest` (forces collections between two equal night
brushes). Not run: tablet detached.

## Previous pass (Select tool: circle and move) — 2026-10-23, macOS — code only

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
