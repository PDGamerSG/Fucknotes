# Claude handoff: Fucknotes editor and tablet work

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
  `TemporaryHighlighter.kt`, drawn only in `DrawingOverlayState`, each trail holds 1.5 s after
  the pen lifts and fades over 1 s, never stored, never on the undo stack. Both tools consume
  their touches in `InkSurface`'s gesture loop before the wet layer. The lasso bar has a colour
  action (`EditorViewModel.recolorStrokes`); the zoom chip and page arrows fade to
  `FADED_CONTROL_ALPHA` after 2.6 s idle (`InkEditor.wakeControls`).
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
