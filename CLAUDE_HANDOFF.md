# Claude handoff: Fucknotes editor and tablet work

## Single shape outlines — 2026-11-13

The owner's next tablet check caught doubled outlines in the default hand-drawn style. The
previous tests asserted two laps and missed that this looked broken at thin widths.
`SketchyShapes` now draws one line/shaft, one rounded-box perimeter, and one closed ellipse.
Arrow wings meet at the exact tip; the first wing's identical samples are retraced to keep a
single branched Ink stroke without creating an offset second line. Endpoints stay fixed and
ellipse perturbations are periodic so joins close without tails. The Clean lines option remains.

Both new native-rendering/perimeter regressions failed on the old installed build, then pass
after the fix. Evidence/backup: `build/tablet-validation-20261113-single-outline/`.
All 127 JVM tests and 17 drawing-tool tablet tests pass, including both widths (0.5 and 2.5),
save/reopen, clean shapes, palm gestures, selection/undo and highlighter/paper regressions.
The corrected release is installed on the Xiaomi, hash-verified and launched. All 34 original
notes match their pre-test contents and timestamps; no scratch notes remain.

## Tablet follow-up — 2026-11-13

The Xiaomi `b86e61d8` is attached again. The follow-up fixes are in `InkSurface`,
`TemporaryHighlighter`, `EditorViewModel.setColor`, and `PaperSheet`:

- A fresh stylus down clears a canceled shape/laser gesture even when fingers from a pinch
  remain down. Compose's consumed synthetic releases mean cancellation: discard shape previews
  and lasso drags rather than committing them as if the pen lifted.
- Temporary red writing stays fully visible while another laser stroke is down. Every finished
  stroke restarts the session's idle timer: hold 1.5 seconds after the latest lift, fade for
  1 second, then remove. Cancellation discards only the active stroke and starts the idle timer
  for the previous writing. It still never enters persisted ink or undo history.
- Choosing a paper/note color, including Default, sets the note to `PageAppearance.LIGHT` so
  dark mode cannot mask the choice. Paper swatches remain available in dark mode. The old blue
  wash over light paper is removed; selected colors now match the page. Swatches have accessible
  color labels, which are also used to exercise both actual overflow color pickers.

Evidence, the previous installed APK, a verified pre-test app-data backup, and a note-content
manifest are under `build/tablet-validation-20261113-followup/`. Do not delete this backup.
The new regressions first failed on the tablet before the fixes. When testing timed overlays,
wait for the selected tool to render before setting `mainClock.autoAdvance = false`; otherwise
the test can start with the previous brush. Restore finger/toolbar preferences after tests.
Final checks: 127 JVM tests, all 68 tablet instrumentation tests, release assembly and debug
lint pass (zero lint errors). After tests, all 34 original notes match the pre-test manifest,
including title, ink, body and edit timestamp; no scratch notes remain. SQLite integrity is `ok`.
The optimized release is installed on the Xiaomi and its installed APK hash matches the local
release artifact. It launches to the original shelf; the tablet is left there.

## 1.2.0 editor release — 2026-11-10

The project is now available in `/Users/admin/Data/Company Project/Projects/Fucknotes`; the empty
workspace was recovered from the complete checkout at `/Users/admin/Data/untitled folder/fucknotes`.
Release notes are in `docs/releases/v1.2.0.md`. Version code is 3.

`PageAppearance.PITCH_BLACK` uses the existing stored string column. Shapes are explicit
(`InkTool.SHAPE`, `EditorUiState.shape`, `ShapeRecognizer.dragPoints`), never automatic changes to
handwriting. `InkTool.LASER` uses `TemporaryHighlighter.kt`'s in-memory overlay and monotonic clock;
it must never enter the note's strokes or undo history. Shapes and lasso finalize on the owning
pointer's release before palm/touch early returns. A new stylus contact chooses its tool even if
the palm stays down. Narrow toolbars use two rows; compact mode keeps Undo visible.
Generated shape markers use Ink's raw input model at creation and decode. The stock marker's
sliding-window smoothing rounds rectangles and shortens arrowheads; preserve the exact geometry.

The Android 15 emulator is available through T3 Code's Device panel. Use the device tools and the
device-bound `agent-device` command for UI actions; the older adb UI examples below are historical.
The signing certificate matches the published 1.1.0 APK. Never clear app data or uninstall.

This file records the state of the current implementation so the next agent can continue without
repeating the investigation. The remote is `https://github.com/PDGamerSG/Fucknotes.git`, branch
`main`. The earlier tablet pass was done on a Mac (`/Users/admin/Data/untitled folder/fucknotes`) with the
Xiaomi tablet attached over USB; earlier passes were done on Windows.

## User requirements handled in the latest pass

- The lock icon at the top-right of an ink note is the single chrome toggle. It cycles
  **Full controls → Pen bar only → Reading mode → Full controls**. Reading mode shows nothing but
  the page, the page counter, and one small unlock button in the top-right corner. The separate
  book icon was removed.
- Page navigation (previous / counter / next) lives in the **bottom-right** corner in every chrome
  state for notebooks. It is a floating panel with 40 dp arrow targets; past the last page the
  arrow adds a sheet.
- The zoom readout / pinch lock moved to the **bottom-left** so it no longer overlaps the
  highlighter swatches. It is hidden in reading mode.
- Dialogs and bottom sheets keep the system bars hidden (`ImmersiveDialogWindow` in
  `ui/common/Chrome.kt`), so opening the pages sheet no longer drops the status bar onto the
  toolbar.
- Settings is grouped into titled cards with a 760 dp max width.
- Home: the grid/list leaves room under the last row for the corner FAB; the rail's bottom rows sit
  on a wash so "Light mode" is legible over the artwork; "Images on this page" has an icon.
- PowerPoint import: `media/pptx/PptxDeck` reads the package (slides → layout → master → theme,
  relationships resolved), `PptxRenderer` paints a slide in PDF points, `PptxImporter` writes a
  `PdfDocument` and hands it to `PdfImporter`. `HomeViewModel.importDocument` and
  `DocumentOpenViewModel` pick PDF or slides by MIME/extension (`DocumentKind`). Unsupported:
  binary `.ppt`, EMF/WMF/SVG media, charts/SmartArt (drawn as labelled frames), custom geometry
  fills, vertical text.
- Finger navigation: `InkSurface` tracks `palmIds` (touch pointers seen while the stylus was in
  contact) and lets every other finger pan in all modes; `FingerInputMode.DISABLED` ("Pen only")
  means fingers never draw or swap tools, not that they are ignored. The zoom lock pins the scale
  and the double-tap swap only.
- Manual shelf order: `NoteEntity.position` (migration 5→6 seeds it from `createdAt`),
  `SortOrder.MANUAL` is the default, `NotesRepository.moveNote/adoptOrder` with
  `NotePositions.between`, and `ui/home/Reorder.kt` (`NoteReorderState`, `Modifier.holdToDrag`,
  `Modifier.reorderItem`). The gesture is two nested `withTimeoutOrNull` phases: `followPress`
  up to the long-press tick (ends on release, ancestor consumption checked on the Final pass, or
  leaving the tile — never on movement), then `followHeldPress` up to the hold, which consumes
  every move so the grid's scroll cannot take the finger and returns `MOVED` past the touch slop
  so a deliberate drag lifts at once. A light tick at the long press, selection on release, strong
  haptic at the lift. Compose UI tests run these
  timeouts on the test clock: drive them with `mainClock.advanceTimeBy`, as `HoldToDragTest` does. `HomeViewModel.moveNote` adopts the displayed order first when a live sort
  is active.
- Release build: `isMinifyEnabled`/`isShrinkResources` with `proguard-rules.pro`, signed with the
  debug key. Install `app/build/outputs/apk/release/app-release.apk` for daily use; install the
  debug APK before running instrumentation (the test APK targets debug classes). `run-as` only
  works while the debug build is installed.
- Editor tab bar: `ui/editor/tabs/EditorTabBar.kt` always renders in `FULL` chrome (one tab or
  many). `OpenTabs` keeps the list as app-scoped snapshot state; `OpenTabs.bind` (called from
  `NotesApp.onCreate`) restores ids from `SettingsRepository.openTabIds` and writes every change
  back. `EditorScreen.leaveNote` keeps the tab; `discardIfEmpty()` now returns whether the note
  was deleted so only then is the tab dropped. Home trash/merge and Trash delete/empty call
  `OpenTabs.forget`.
- `InkSurface(reading = true)` (set by `InkEditor` in `READING`) makes one finger pan regardless
  of the finger-mode preference, lets a locked pinch move but not rescale, and blocks the double-tap
  tool swap. Without this, "Pen only" plus the zoom lock left reading mode unable to scroll.
- The New notebook dialog has only the title; the "Empty pages at start" box was removed.
- `EditorViewModel.saveNow` only writes when the note row would actually change, so viewing a
  note no longer bumps `updatedAt` and reorders the shelf.

Earlier completed work remains: Xiaomi pen button mapping, finger-mode cycling (Hand / Write with
fingers / Pen only), palm rejection, five-swatch toolbar with two highlighters, PDF/image import
and export, first-page previews, dark reader, folder hierarchy, home export.

## Key files for the chrome model

- `ui/editor/EditorChrome.kt`: the `FULL / COMPACT / READING` enum, `next()`, and the per-state
  `lockLabel` used as the lock button's content description (tests key off these strings).
- `ui/editor/EditorScreen.kt`: owns `chrome` (`rememberSaveable` per note). Tab strip and the typed
  note's top bar show only in `FULL`. A typed note's lock is a plain `FULL ↔ COMPACT` toggle.
- Performance method: `adb shell dumpsys gfxinfo com.example.fucknotes framestats` after a
  scripted scroll gives per-stage frame times (parse the CSV after `---PROFILEDATA---`;
  `IssueDrawCommandsStart→SwapBuffers` is RenderThread, `SwapBuffers→FrameCompleted` is GPU
  wait). `perfetto --txt -c` with `gfx view hwui` categories plus
  `setprop debug.hwui.skia_atrace_enabled true` shows Skia op counts; `simpleperf` is blocked on
  this ROM. The shelf fix was `CompositingStrategy.Offscreen` on `Modifier.reorderItem` and the
  rail (`HomeRail`). Do not put Offscreen on the ink surface: its content changes every pan frame.
- Night ink: `Brush.forNight(tool)` (DarkReader.kt) builds the wet brush and records the daylight
  brush in a bounded strong map (never weak — brushes compare by value and a weak key equal to a
  live brush can be collected mid-stroke); `Stroke.daylight()` swaps it back. `InkSurface` builds brushes in the real
  colour, turns them to night for `InProgressStrokes`, and wraps `onStrokesFinished` so committed
  strokes are daylight. Never store a stroke straight from the wet layer while dark mode is on.
- Dark mode for pages: `ui/editor/ink/DarkReader.kt` holds `NightPalette` (`paper` is true black,
  `rule`, `edge` for the outline of a black sheet on the black desk, `imageMatrix` for bitmaps —
  white paper lands on black — and `applyImageMatrix` for JVM tests), `darkReaderArgb(argb, tool)`
  (HSL lightness inversion, hue kept, highlighter exception) and `InkStroke.forDarkReader()`
  (cached night `Stroke` per stroke). Consumers: `InkSurface`, `PageImages`, `PagesSheet`,
  `AttachmentStrip`, `NoteTile`/`NoteRow`, `TypedEditor`, the exporter, and the theme (dark while
  on). There is one dark mode and it is black; the slate and the separate "pitch black" option
  are gone (owner's request, 2026-11-13).
- Light or dark per note: `NoteEntity.appearance` (`PageAppearance.FOLLOW / LIGHT / DARK`, db v7,
  `MIGRATION_6_7`, stored as the enum name; `PageAppearance.fromStored` reads a legacy
  `PITCH_BLACK` as `DARK`). `PageAppearance.isDark(appDarkReader)` resolves it;
  `EditorUiState.darkReader` is a computed property over `appearance` + `appDarkReader`, so every
  editor consumer still reads `state.darkReader`. **The only switch is "Dark mode" in the editor's
  ⋯ menu** (`EditorViewModel.toggleDarkReader` → `setDarkPages`). The New notebook dialog and the
  Paper sheet no longer offer light/dark, `PaperColors` holds light tones only, and every note the
  user creates (`HomeViewModel.createNote/createNotebook`) is `LIGHT` whatever the app-wide
  setting says. Settings' "Dark mode for pages" is still the default for `FOLLOW` notes (old
  notes, imported documents). Shelf, search and trash tiles call
  `note.appearance.isDark(settings.darkReader)`. `PageTheme(dark)` (Theme.kt) wraps the editor so
  its chrome matches the open note; it always nests the same tree, because switching between a
  bare `content()` and a wrapped one recreated the editor and closed its sheets.
- Export follows the note: there is no light/dark question anywhere. `EditorViewModel.export`
  passes `state.darkReader`; "Export as PDF" goes straight to the share sheet; the home export
  (`FolderExporter.save(dark = { note -> … })`, `NoteExporter.exportAll(notes, format, dark)`)
  and Drive uploads (`exportEditablePdf(note, dark)`) resolve each note's own mode, so one
  combined PDF can hold black and light pages. Dark export paper is `Color.BLACK` everywhere.
  Dot paper exports with 1.2 pt dots (`NoteExporter.DOT_RADIUS_PT = 0.6f`); they were 2.6 pt and
  the owner found them too big.
- Night colours stored by mistake: before c4dc023 a stroke written on a dark page could reach the
  note in its *night* colour (the weak-map bug described under "Night ink"). The owner's
  "Untitled Notebook" (id 29) held 66 such strokes out of 6,989; nothing written after the fix
  has any. `daylightOfNightSlip(argb, tool)` (DarkReader.kt) maps a stored colour that is exactly
  the night form of a palette pen back to that pen, and `InkCodec.parse` applies it to every
  stroke it reads, so the page, thumbnails and exports agree; the row is rewritten the next time
  the note is edited. Pens only hold `KnownInks` (today's palette and tray plus the first
  palette), and no palette colour is the night form of another — `NightSlipTest` guards both.
  If the pen colour picker ever allows free colours, revisit this rule.
- Drawing tools (released in 1.2.0): `InkTool.SHAPE` (toolbar "Shapes" menu, drag out a line /
  arrow / rectangle / circle with the pen in hand; `ShapeRecognizer.dragPoints` is the outline
  for both the live preview and the stored stroke, `EditorViewModel.commitShape` keeps it as a
  `MARKER` stroke with `InkStroke.shape`). A shape is drawn and decoded with
  `InkCodec.brushForShape` / `shapeFamily` — the marker with Ink's naive input model — because
  the stock marker smooths by pen speed and turned four sparse corners into a blob with one
  square corner and a curled arrowhead. Any build that draws shapes with plain
  `brushFor(MARKER)` shows that blob; it was seen on the tablet on 2026-11-13 from a stale
  working tree. `InkTool.LASER` is the red "Temporary red highlighter":
  `TemporaryHighlighter.kt`, drawn only in `DrawingOverlayState`; all writing stays visible
  during a laser stroke, holds 1.5 s after the latest lift, and fades over 1 s. Never stored or
  on the undo stack. Both tools consume
  their touches in `InkSurface`'s gesture loop before the wet layer. The lasso bar has a colour
  action (`EditorViewModel.recolorStrokes`); the zoom chip and page arrows fade to
  `FADED_CONTROL_ALPHA` after 2.6 s idle (`InkEditor.wakeControls`).
- The two top bars (owner's reference screenshots, 2026-11-13): the note bar and the pen bar are
  two separate pills **on one row in both orientations** — never stacked on a tablet.
  `InkEditor` puts the note bar at its own width and gives the pen bar the rest of the row
  (stacking survives only below `STACKED_BARS_BELOW`, 560 dp, i.e. phones). `InkToolbar` is always
  one row and asks `ToolbarLayout.fit(availableDp, …)` how to fit it: full 48 dp buttons with
  every tool if there is room; otherwise 44 dp buttons, as many tools as fit in bar order
  (eraser first), and the rest behind one **"More tools"** button whose menu lists them. The pens
  never fold; they scroll if they must. Upright on the tablet (777 dp) that is: eraser, More
  tools, four pens, two highlighters, lock. `ToolbarLayoutTest` pins both orientations' numbers;
  if a button is added to either bar, redo that arithmetic.
- Toolbar items: ⋯ → "Toolbar items" (`ToolbarItemsDialog`, EditorMenus.kt) has a switch for every
  tool (`ToolbarTool`) and every pen and highlighter slot on the bar. The choice is app-wide:
  `Settings.hiddenToolbarItems`, a string set under `toolbar_hidden` holding `ToolbarTool.key`s
  and `ToolbarLayout.penKey(slot)`s — the keys are stored, do not rename them.
  `ToolbarLayout.visible` applies it; a hidden slot closes up (the fifth pen does not slide in),
  the pen in hand is always shown even if hidden, and `canHide` refuses the last pen.
  `EditorViewModel.setToolbarItemHidden` swaps a hidden tool or pen that was in hand for a pen
  still on the bar. Device tests that tap tools go through `tapTool` / `pickShape` in
  `EditorDrawingToolsTest`, which look behind "More tools" when the bar is tight, and
  `withEditor` shows every item and restores the owner's set afterwards.
- Hand-drawn shapes: `SketchyShapes.points` draws one gently bowed outline, a box with soft
  corners, and one closed circle. The earlier doubled passes were removed after tablet feedback.
  `ShapeRecognizer.dragPoints(…, sketchy)` picks it; `Settings.sketchyShapes` (default on)
  is flipped by the "Clean lines" switch at the bottom of the Shapes menu. The wobble is seeded
  by the drag's *start* and every pass takes a fixed count of random values, so the preview
  stretches instead of flickering while the far corner moves, and the stored stroke is the
  preview. `InkSurface` latches `drawingSketchy` with the shape at pointer down. No diamond: the
  owner did not want one.
- The red highlighter is drawn as a lit tube (glow, red body, near-white core). All of its
  writing stays visible while the pen is down, holds `TemporaryTrail.HOLD_MS` (1.5 s) after the
  latest lift, then fades over `FADE_MS` (1 s). A brief 0.9 s total was tried on 2026-11-13 and
  left too little reading time. Its button label is `RED_HIGHLIGHTER_LABEL`.
- Exporting one note from the shelf: hold the note (that selects it), then "Export PDF" in the
  selection bar — a labelled button now, not a bare arrow. One selected note goes straight to
  the save dialog and is exported as the editor exports it (`FolderExporter` COMBINED with a
  single note uses `NoteExporter.export`, so the PDF is editable); several notes still get the
  combined / ZIP / folder question.
- `origin/main` got the 1.2.0 commit (c1eca03, tag `v1.2.0`) from another checkout while this
  folder still held an older uncommitted copy of the same work, which is how the tablet came to
  be tested with a stale build on 2026-11-13. Run `git fetch` and compare with `origin/main`
  before building on a dirty tree here.
- Opening documents from other apps: `AndroidManifest.xml` offers `MainActivity` for VIEW/SEND
  of PDF, PPTX, every legacy PowerPoint type (`application/vnd.ms-powerpoint`, `mspowerpoint`,
  `x-mspowerpoint`, `powerpoint` — file managers label .pptx with these), and `*/*` with
  `pathPattern`s for `.pdf/.pptx/.ppt` so an `application/octet-stream` download still finds us.
  `media/DocumentType.kindOf(type, name)` is the one rule for "PDF, slides or neither"; the
  intent path (`DocumentOpenViewModel` → `DocumentKind.detect`) returns early on neither, the
  picker path (`DocumentKind.of`) falls back to PDF. If the app still does not appear in a
  chooser, another reader holds an "always" default: Settings → "Opening documents"
  (`ui/settings/DocumentHandlers.kt`) resolves the current handler per type with
  `resolveActivity(MATCH_DEFAULT_ONLY)` (needs the `<queries>` block) and offers a Change button
  into that app's info page. Android drops a stale default by itself when the candidate set
  changes, which is what happened on the tablet (Xodo held PDF and .ppt). `DocumentIntentTest`
  queries the resolver on device for every shape.
- Pen buttons: `MainActivity` feeds every key and motion packet into `input/PenPresses.kt`, which
  decides what is a new press; only then `PenInputBus.emit`. Do not emit from the activity
  directly. `InkSurface`'s gesture loop latches `gestureTool` at pointer down and uses it until
  every pointer lifts, so a tool change mid-gesture applies to the next stroke. To emulate the
  Focus Pen's primary switch from adb use `input stylus keyevent KEYCODE_PAGE_DOWN` (what this
  tablet reports for it); `KEYCODE_STYLUS_BUTTON_*` key events never reach the app.
- Device tests that inject stylus events: `InkSurface` overwrites the camera's density from the
  display (2.75 on the tablet), so convert world points to screen through `camera.scale`/`pan`
  (see `LassoDeviceTest.stylus`). A finished stroke is handed over after `waitForIdle`; use
  `waitUntil`. The wet layer is an `InProgressShapesView` (ink 1.0.0), not `InProgressStrokesView`.
- Opening a note: `EditorViewModel` publishes the row first (`loaded = true`), then `loadInk`
  streams strokes in growing batches via `InkCodec.parse` + `decodeStroke` in parallel, appending
  to `strokes`. `inkLoaded` gates ink encoding in `saveNow` and the empty-note discard; never
  encode or discard before it is true. `InkCache` (4 notes) keyed by note id + exact JSON makes a
  reopen instant; `saveNow` refreshes it. `EditorScreen` paints the desk until `state.loaded`.
- Select tool (`InkTool.LASSO`): `ui/editor/ink/Lasso.kt` holds the pure geometry and
  `LassoState` (selection indices, loop, drag — all world units). `InkSurface` takes `lasso` and
  `onMoveStrokes`; its gesture loop has a LASSO branch that consumes every touch (so wet ink never
  sees it), and the dry layer draws selected strokes shifted by `drag` while a move is in
  progress. `InkEditor` owns the state and the floating Duplicate / Delete / Deselect bar.
  Selection indices refer to list positions, so every mutation must keep order (move does) or
  clear the selection (undo/redo via the tool-change effect is not enough — the bar's Delete
  clears explicitly).
- Wet ink: `InProgressStrokes` in `InkSurface` must stay composed whenever `enabled`, whatever
  the tool. Removing it for the eraser/hand and recomposing it for a pen rebuilds the
  front-buffer surface and the first strokes after the switch are invisible until committed.
- Google Drive sync and editable PDFs: see `docs/drive-sync.md` for the design, the file map, the
  one-time Google Cloud setup (Android OAuth client with the debug key's SHA-1), and the first-run
  checklist. Written 2026-10-22, compiled and unit-tested, **not yet run against a Google
  account**. `ServiceLocator.drive()` builds the `DriveSync`; `MainActivity.onStart` calls
  `syncIfDue()`; the shelf's note stamp (`DriveDao.observeNoteStamp`) debounces auto sync.
- Migrations can be tested on device: `room-testing` is on the androidTest classpath and the
  exported schemas are androidTest assets; `AppDatabase.migrationForTests(from, to)` exposes one.
- Back: `EditorScreen`'s `BackHandler` calls `leaveNote()` only in `EditorChrome.FULL` and
  swallows back otherwise; `InkEditor` applies `systemGestureExclusion()` to the surface when the
  chrome is not `FULL` (no longer tied to the zoom lock).
- `ui/editor/InkEditor.kt`: lays out the corners. Top-left note bar (`FULL`), top-right pen bar
  (`FULL`, `COMPACT`) or unlock button (`READING`), bottom-left zoom chip (not `READING`),
  bottom-right page counter (always, notebooks), attachment strip (`FULL`).
- `ui/editor/ink/InkToolbar.kt`: pen bar; the trailing lock calls `onCycleChrome`.
- `ui/editor/ink/InkOverlays.kt`: `ZoomChip` and the floating `PageIndicator`.

## Conflict-file handling

The Windows workstation sync service sometimes creates `*.sync-conflict-*` copies inside
`app/src`. They are ignored by git and excluded from Kotlin compilation in `app/build.gradle.kts`.
Move any that appear into `build/sync-conflict-backups/<date>/` before building. None appeared on
the Mac.

## Commit identity and releases

Commit as `PDGamerSG <177869559+PDGamerSG@users.noreply.github.com>` (this Mac's git config);
GitHub links that address to the account. Commits made with any other address show without the
owner's profile. The 2026-10-22 commits were rewritten to this identity and force-pushed.
Releases: bump `versionCode`/`versionName` in `app/build.gradle.kts`, tag `vX.Y.Z`, build
`:app:assembleRelease`, then `gh release create vX.Y.Z app-release.apk`. v1.1.0 is the first.

## Build on macOS

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties   # gitignored
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon -Dkotlin.incremental=false
adb -s b86e61d8 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s b86e61d8 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s b86e61d8 shell am instrument -w -r com.example.fucknotes.test/androidx.test.runner.AndroidJUnitRunner
```

`platforms;android-36` had to be installed with `sdkmanager`. Install with `-r` so the owner's
notes are kept; never clear app data or uninstall, and never run Gradle's `connected*Test` tasks
against the tablet (their cleanup uninstalls the app). With the debug build installed, take a
backup before any device test run:
`adb exec-out "run-as com.example.fucknotes tar cf - databases files" > build/tablet-backup-<date>/appdata.tar`.

## Validation of the latest pass

See `docs/notes-workflow-validation.md` for the executed commands and counts.

## Tablet notes

The physical display is landscape 3200x2136 at about 2.75 density; the serial is `b86e61d8`.
The owner's notebooks (`Db and caching`, `Tablet_Validation_20261022`, and others) must be
preserved. Do not claim a physical pen-button verification unless the pen was actually pressed.
The owner may be using the tablet while it is plugged in: on 2026-11-13 their pen strokes landed
in the scratch notebook mid-run and injected taps were lost or hit an open menu. Check the screen
(`uiautomator dump`) before every injected tap, and prefer instrumentation tests to driving the
UI. A scratch notebook `ClaudeTest1113` (id 43) was left on the shelf because the owner wrote in
it; they can trash it.

There is a second tablet: a Lenovo TB373FU, serial `HA29MSA9` (Android 16, 1840 x 2944 at
340 dpi), also with the owner's own notes. It ran the published 1.1.0 until 2026-11-13, when a
notebook passed 2 MB and the app crashed at every start (`SQLiteBlobTooBigException`); it now
has the 1.2.0 build. Check `adb devices` before assuming which tablet is attached — a command
aimed at `b86e61d8` simply hangs when the Lenovo is the one plugged in.

Without the tablet: `avdmanager create avd -n <name> -k "system-images;android-35;google_apis_playstore;arm64-v8a" -d pixel_tablet`,
boot it with `emulator -avd <name> -no-window -no-audio -no-snapshot -gpu swiftshader_indirect`,
and `adb shell wm size 1068x1600; wm density 220` gives the tablet's upright size in dp
(777 x 1164). The full suite takes about ten minutes there. Delete the AVD afterwards; `naukri`
is the owner's and belongs to another project. At that override size
`pinchBeforeChoosingShapeDoesNotSwallowItsFirstStroke` fails on any build (see the validation
notes of 2026-11-13); run the suite at the emulator's own size as well.
