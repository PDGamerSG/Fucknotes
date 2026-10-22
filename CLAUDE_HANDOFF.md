# Claude handoff: Fucknotes editor and tablet work

This file records the complete state of the current implementation so the next agent can continue
without repeating the investigation. The repository is `E:\Data\Projects\Fucknotes`, on branch
`main`, with remote `https://github.com/PDGamerSG/Fucknotes.git`.

## User requirements being handled

The current work covers the latest editor/home requests:

- Show the first page of an imported PDF as the note preview on the home shelf.
- Keep highlighter ink translucent so the original PDF/text remains readable.
- Keep page navigation visible when the corner zoom lock is enabled.
- While zoom lock is enabled, consume finger/palm gestures on the writing surface so they cannot
  pan, pinch, or double-tap the page; stylus writing must remain active.
- Promote page navigation to a top-center counter with previous/next controls.
- Add read mode: hide editor controls and tabs, leave only the book icon and page navigation, and
  leave the stylus surface usable.
- Add a persistent Dark reader setting that renders pages, imported PDF images, home thumbnails,
  typed-note paper, and stored ink with a soothing dark/inverted presentation without changing
  the saved note/export data.

Earlier completed work remains part of this branch: Xiaomi pen button mapping, direct finger-mode
cycling (Hand / Write with fingers / Pen only), palm rejection, compact five-color toolbar with
two highlighters, PDF/image import and export, first-page/page-count handling, folder hierarchy,
home export, and the requested removal of obsolete note actions.

## Files changed in the current pass

- `data/db/Daos.kt`, `data/repo/NotesRepository.kt`: observe page-zero image attachments.
- `ui/home/HomeViewModel.kt`, `HomeShelf.kt`, `NoteTile.kt`, `NoteRow.kt`: carry the first-page
  image path into previews; render the imported image; apply Dark reader inversion to thumbnails.
- `data/ink/InkCodec.kt`: normalize highlighter alpha to a maximum of 64 (old saved opaque
  highlights are normalized when decoded); `PageAndHighlightTest` expects 64.
- `data/repo/SettingsRepository.kt`: add the persistent `darkReader` preference and setter.
- `ui/settings/SettingsScreen.kt`: add the Dark reader switch.
- `ui/theme/Theme.kt`, `MainActivity.kt`: pass Dark reader through the app theme.
- `ui/editor/EditorViewModel.kt`: mirror the setting in `EditorUiState`.
- `ui/editor/ink/DarkReader.kt`: invert RGB while preserving alpha and provide a dark-reader
  `Stroke` copy helper.
- `ui/editor/ink/PageImages.kt`: invert imported page/background images with a color matrix.
- `ui/editor/ink/InkSurface.kt`: consume non-stylus gestures while zoom locked; dark page/rule
  colors, dark imported images, dark rendered strokes, and dark active brush colors.
- `ui/editor/TypedEditor.kt`: use the dark paper/rule palette for typed notes.
- `ui/editor/InkEditor.kt`: add top page navigation, lock-surface gesture exclusion, read-mode
  state, and the book button. The page counter remains visible in read mode and when locked.
- `ui/editor/EditorScreen.kt`: own `readMode`; hide the tab strip while reading and pass the state
  into `InkEditor`.
- `app/build.gradle.kts`: exclude timestamped sync-conflict files from Kotlin compilation.
- `.gitignore`: ignore `*.sync-conflict-*` files.
- `ui/editor/ink/DarkReaderTest.kt`: pure JVM inversion/alpha test.
- `androidTest/.../FingerInputTest.kt`: locked-surface finger blocking plus stylus-writing test.

## Important behavior details

`readMode` is view state remembered per note, not persisted as note content. In read mode the ink
surface remains enabled, so a pen can write; controls and attachment strip are hidden. The book
button in the top-right exits read mode. The page counter is top-center for notebooks and is
independent of compact mode or zoom lock.

`zoomLocked` remains the existing app-wide preference. `InkSurface` consumes every non-stylus
pointer packet early while the lock is on. `Modifier.systemGestureExclusion()` is applied to the
surface while locked. Android system bars remain immersive/transient by swipe in `MainActivity`;
system-level notification shade/gesture interception is controlled by Android and cannot be
guaranteed by Compose alone.

Dark reader is display-only. It does not rewrite `drawingJson`, attachment files, database colors,
or PDF export data. The Material theme also switches to dark while the setting is enabled so home
and settings remain readable.

The first-page DAO currently selects all `IMAGE` attachments with `pageIndex = 0`. PDF page
backgrounds have page geometry, while standalone images normally have a null page index. If future
work introduces other page-zero image types, add a geometry predicate or an explicit attachment
subtype.

## Conflict-file handling

The workstation sync service sometimes creates files such as `*.sync-conflict-20261022-*` inside
`app/src`. They are stale duplicate source files and cause Kotlin redeclaration failures. During
this work they were moved (never deleted) to the ignored backup tree:

`build/sync-conflict-backups/20261022/`

Before a build, move any current conflict copies there. The Gradle Kotlin task exclusion is also in
place as a race-safe fallback. Do not commit the backup tree.

PowerShell cleanup command:

```powershell
$workspace=(Get-Location).Path
$backup=Join-Path $workspace 'build/sync-conflict-backups/20261022'
$files=Get-ChildItem -LiteralPath (Join-Path $workspace 'app') -Recurse -File -Filter '*.sync-conflict-*' -ErrorAction SilentlyContinue
foreach($file in $files) {
  $relative=$file.FullName.Substring($workspace.Length).TrimStart('\')
  $destination=Join-Path $backup $relative
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination)|Out-Null
  Move-Item -LiteralPath $file.FullName -Destination $destination -Force
}
```

## Validation already completed

- `:app:compileDebugKotlin` passed after the current changes.
- `:app:testDebugUnitTest` passed. The current report contains 14 tests (including the new
  `DarkReaderTest`); all failures/errors are zero.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` produced APKs before the final test
  addition; rerun them after the final commit so the APK definitely contains the new locked-surface
  test.
- Before this pass, the physical Xiaomi tablet (`b86e61d8`, model `2410CRP4CI`, Android 16) passed
  9 instrumentation tests. It was installed with `adb install -r` without clearing data.
- The tablet screenshot check showed the first-page PDF/ink preview on home, the top page counter,
  compact toolbar, and read mode with only the book icon plus page navigation. Existing notebook
  data was preserved.
- The last attempted install/instrumentation command was interrupted by the user after starting;
  rerun it and report the actual result rather than assuming it completed.

## Required final verification and push

1. Move any new conflict files out of `app/src`; run `git diff --check`.
2. Run:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon "-Dkotlin.incremental=false"
   ```

3. Install without clearing tablet data and run all instrumentation tests:

   ```powershell
   $adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   & $adb -s b86e61d8 install -r app\build\outputs\apk\debug\app-debug.apk
   & $adb -s b86e61d8 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
   & $adb -s b86e61d8 shell am instrument -w -r com.example.fucknotes.test/androidx.test.runner.AndroidJUnitRunner
   ```

   Expected baseline is 9 tests, plus the new locked-surface test makes the total 10 if it is
   included in the rebuilt test APK.

4. Review `git status --short`; only intended source, test, Gradle, ignore, and this handoff file
   should be tracked. Commit everything:

   ```powershell
   git add .gitignore CLAUDE_HANDOFF.md app/build.gradle.kts app/src
   git commit -m "feat: add PDF previews, read mode, and dark reader"
   git push origin main
   git rev-parse HEAD
   git ls-remote origin refs/heads/main
   ```

   The two final hashes must match. Do not clear app data, uninstall, or reset the tablet.

## Tablet/test notebook

The existing test notebook is `Tablet_Validation_20261022`; preserve it. The physical display is
landscape 3200x2136 at roughly 2.75 density. The tablet serial is `b86e61d8`; an emulator may also
be listed by ADB, so always pass `-s b86e61d8`.

Useful coordinates from prior checks (may shift with toolbar state): back near `(95,90)`, eraser
near `(2100,90)`, finger mode near `(2230,90)`, pen/highlighter swatches around `(2390,90)` through
`(2940,90)`, and the new page counter at the top center. Read mode's book icon is top-right.

Do not claim a physical Xiaomi pen-button verification in the final handoff unless the pen is
actually pressed on the tablet during this continuation. The prior user-confirmed mapping is
working and must not be regressed.
