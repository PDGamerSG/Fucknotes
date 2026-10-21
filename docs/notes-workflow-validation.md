# Notes workflow changes

Code and test sources compile. Runtime tests and tablet installation are deferred at the user's request.

## Implemented

- Compact editor controls, circular ink colors, custom eraser icon, and paper settings in the overflow menu.
- Compact mode retains only colors, eraser, hand and the control toggle; expand it to change pen/highlighter families.
- Removed note locking, pinning, reminders, type-conversion and note-info controls; removed Stationery and the duplicate home create button.
- Plus menu contains notebook, canvas, and PDF import.
- Collection/folder hierarchy with visible menus, recursive export, and a home light/dark toggle.
- Cooler page tones and display-only night shading, with stronger dotted-paper contrast.
- Translucent highlighting on new and reopened strokes.
- Typed-note annotations remain visible while typing, scroll with their content, and have the same pen/highlighter/eraser controls as notebooks.
- PDF import from the picker, Open with, and Share; pages become image backgrounds with editable ink above them. PDF text and links are rasterized during import. Mixed PDF page sizes are fitted proportionally into the notebook's first-page size.
- Page images retain their position in notes, thumbnails, backups and PDF exports; their page menu supports repositioning, resizing and removal.
- Combined PDFs, ZIPs of PDFs, and separate PDFs in a selected folder; nested exports preserve folder paths and disambiguate duplicate names.
- Notebook exports use the page's point dimensions. Image exports show the first notebook page; use PDF for all pages.
- Adding a page keeps the camera still. Initial blank-page counts persist. Scroll bounds follow created pages.
- Older stored page counts are preserved during database upgrade, backup restore, editing and PDF export.
- Pending note saves complete when navigating away; saving and export share the persisted ink representation.
- Pen motion buttons track held switches across tip lifts and hover, ignore release packets, and reset after focus loss. Complete `ACTION_MULTIPLE` key events do not remain latched. Only the resumed editor consumes shortcuts.

## Compile checks

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon "-Dkotlin.incremental=false"
git diff --check
```

## Deferred execution

1. Run `:app:testDebugUnitTest` and `:app:connectedDebugAndroidTest`. The added PDF workflow test checks title preservation, imported page count, saved page backgrounds, exported dimensions and rendered background pixels.
2. On the Xiaomi tablet, map Primary to Toggle eraser. Check single press, hold, release, repeat press, hover, pen contact, switching notes and returning from settings. Each physical press must produce exactly one action. A changing diagnostic keycode alone does not prove this works.
3. Highlight typed text, handwriting, an image and a PDF page; save, reopen and export. Compare visibility while drawing, after the stroke finishes and in the PDF.
4. Import portrait/landscape/mixed-page PDFs, a large PDF and a damaged/password-protected PDF. Check the progress/error states and annotate later pages.
5. Import rotated camera images, PNGs with transparency and cloud-provider images. Move, resize, remove, duplicate and back up the note; removing one copy must not damage another.
6. Export a collection with nested folders, same-name notes, Unicode names, empty pages, images and ink in each output mode. Reopen every output, compare page count/content, and verify cancellation and unwritable destinations.
7. Add pages while viewing a middle page, reopen the notebook, and immediately leave after a final stroke. Confirm the camera, page count and final stroke are retained.
8. Check compact/expanded controls, night shading, folder menus and all removed actions on narrow and wide windows.
9. In an existing typed note, annotate several lines, scroll using a finger or the hand tool, switch back to typing, and reopen. Confirm the ink stays visible, moves with the text, and does not intercept typing or text selection while annotation mode is off.
10. Upgrade/restore an older multi-page notebook and confirm every stored page remains accessible and exports, including notes that predate the explicit-page flag.

No runtime result is claimed by the compile checks above.
