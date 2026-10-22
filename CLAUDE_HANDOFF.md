# Claude handoff: Fucknotes editor and tablet work

This file records the state of the current implementation so the next agent can continue without
repeating the investigation. The remote is `https://github.com/PDGamerSG/Fucknotes.git`, branch
`main`. The latest pass was done on a Mac (`/Users/admin/Data/untitled folder/fucknotes`) with the
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
  `Modifier.reorderItem`). The gesture is two nested `withTimeoutOrNull` phases around
  `followPress`, which only ends on release, ancestor consumption (checked on the Final pass) or
  leaving the tile — never on movement, so a fidgety finger still lifts the note. A light tick at
  the long-press timeout, selection on release, strong haptic at 2 s. Compose UI tests run these
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
notes are kept; never clear app data or uninstall.

## Validation of the latest pass

See `docs/notes-workflow-validation.md` for the executed commands and counts.

## Tablet notes

The physical display is landscape 3200x2136 at about 2.75 density; the serial is `b86e61d8`.
The owner's notebooks (`Db and caching`, `Tablet_Validation_20261022`, and others) must be
preserved. Do not claim a physical pen-button verification unless the pen was actually pressed.
