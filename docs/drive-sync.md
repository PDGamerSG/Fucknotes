# Google Drive sync

Written on 2026-10-22. The code is complete and compiles; it has **not** been run against a
real Google account yet, because the OAuth client has to be registered first (below) and the
tablet was not to be used that day.

## What it does

- **Log in with Drive** (top of the shelf, or Settings → Google Drive) asks the Google account for
  the `drive.file` scope — the app sees only files it created. Nothing else in the Drive is
  visible to it.
- The app keeps a **`Fucknotes/`** folder in that Drive. Inside it, one folder per collection,
  nested the way the rail is nested, plus **`Unfiled/`** and **`Archive/`**. In each folder,
  one **PDF per note**, named after the note.
- Every PDF is an **editable export**: a normal PDF for any reader, with the note's own data
  attached as embedded files (`note.json` and the page images). Opening such a PDF in Fucknotes —
  from Drive, a share, a file manager — restores the note with its ink, paper, pages, images and
  tags, so strokes can be erased and the note changed, then synced again.
- Sync is shelf → Drive, incremental: a note is uploaded again only when it changed since its
  last upload; a moved note is moved, a renamed note renamed, a trashed note trashed on Drive.
  It runs after the shelf has been quiet for 30 s (auto sync), when the app comes to the
  foreground and the last sync is older than 15 min, and on **Sync now**.
- **Restore notes from Drive** walks the folder and brings in every note that is not on this
  device, filed under the same collection names. A plain PDF someone dropped into the folder by
  hand is imported as pages with ink on top.
- **Log out** forgets the account and the bookkeeping; the files stay in the Drive.

## One-time setup in Google Cloud

1. Create a project at https://console.cloud.google.com (any name).
2. **APIs & Services → Library → Google Drive API → Enable.**
3. **APIs & Services → OAuth consent screen**: External, app name "Fucknotes", your email as
   support and developer contact. Add the scope `.../auth/drive.file`. While the app is in
   *Testing*, add the Google accounts that may log in under **Test users** (up to 100).
4. **APIs & Services → Credentials → Create credentials → OAuth client ID → Android**:
   - Package name: `com.example.fucknotes`
   - SHA-1: the signing certificate of the build on the device. Both the debug and the release
     build on the tablet are signed with this Mac's debug key, whose fingerprint is
     `9E:B3:E0:CB:95:44:FC:8D:42:ED:14:3B:0A:72:5C:67:36:B3:21:F7`. To print it again:
     ```
     /opt/homebrew/opt/openjdk@21/bin/keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1
     ```
   A Web client ID is not needed: the app asks Play services for an access token
   (`AuthorizationClient`) and calls the Drive REST API itself.
5. Nothing to paste into the code. Install the build, tap **Log in with Drive**, pick the
   account, accept the consent sheet.

If the consent sheet says the app is blocked or unverified, the SHA-1 or package name does not
match the registered client, or the account is not in the test users list.

## Where things are

- `drive/DriveAuth.kt` — Play services `AuthorizationClient`; returns a token or a consent
  `PendingIntent` the screen launches. Tokens live in memory only.
- `drive/DriveApi.kt` — the Drive v3 calls over `HttpURLConnection`: about, get, list, folder
  create, update (name / parents / trashed / appProperties), resumable upload, download, delete,
  token revoke. One 401 is retried with a fresh token.
- `drive/SyncPlanner.kt` — pure: shelf + bookkeeping → Upload / Move / Rename / Trash actions,
  and unique file names per folder. Unit-tested.
- `drive/DriveSync.kt` — the engine: folder tree, actions, restore, triggers, status flow.
- `export/EditablePdf.kt` — attaches / reads embedded files in a PDF by appending an incremental
  update (classic xref tables only, which is what `PdfDocument` writes). Unit-tested on a
  hand-built PDF; the device test checks `PdfRenderer` still opens the result.
- `export/NotePayload.kt`, `export/EditableNotes.kt` — the note data that travels in the PDF.
- `media/NoteRestorer.kt` — PDF parts → note. Same install + same note id: the existing note is
  opened, and overwritten only if the file is newer. Otherwise a new note.
- DB: `drive_files` (note ↔ Drive file, state at last upload) and `drive_folders`
  (folder key ↔ Drive folder), version 8.
- Settings: `driveEmail`, `driveName`, `driveRootId`, `driveAutoSync`, `driveLastSyncAt`,
  `driveLastError`, `editablePdfExports`, `installId`.
- UI: `ui/home/DriveMenu.kt` (`DriveAction`), wired through `SheetTopBar`'s `drive` slot;
  Settings → "Google Drive".

## Limits and choices

- Voice recordings are not embedded in the PDF (large, not on the page).
- Page images travel as JPEG with a 2048 px long edge; the PDF's own pages keep full resolution.
- Multi-note PDFs (a whole collection in one file) are not made editable; per-note files are.
- A PDF re-saved by another app may lose the attachment; it then imports as a flat PDF.
- Folders deleted on the shelf keep their Drive folder (the notes inside are moved out).
- No conflict handling across devices beyond "the newer file wins on import".

## First run on the tablet (to do)

1. Register the OAuth client (above), install the debug build, run
   `adb shell am instrument -w -r -e class com.example.fucknotes.EditablePdfDeviceTest com.example.fucknotes.test/androidx.test.runner.AndroidJUnitRunner`.
2. Log in with Drive, let the first sync run, open drive.google.com and check the folder tree.
3. Edit a note, wait ~30 s, check the PDF changed. Move a note between collections, check it moved.
4. Download a PDF from Drive on the tablet, open it with Fucknotes: it should open the same note.
5. Log out, "Restore notes from Drive" after logging in again on a clean install.
