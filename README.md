# MathPaper BD

Offline Android app (Kotlin + Compose) for editable maths question papers. No INTERNET permission, no accounts, no telemetry.

## Build
Open in Android Studio (Ladybug+), sync, Run. Put Noto Bengali fonts in `app/src/main/res/font/` (see README there).
Unit tests: `./gradlew testDebugUnitTest`

## Code map (~1300 lines)
| File | Role |
|---|---|
| Model.kt | document model (one sealed `El`), versioned JSON, atomic save |
| VM.kt | state + commands, undo/redo snapshots, debounced autosave |
| Geo.kt | all shape logic + the single geometry renderer |
| Eq.kt | formula parser (one tree -> editor layout and DOCX OMML) |
| Els.kt | text/table/image rendering, fonts, bounds; `drawEl` is used by editor AND PDF |
| Export.kt / Docx.kt | PDF (PdfDocument) / DOCX (hand-written OOXML) |
| Ui.kt | all screens and dialogs |

## Formula syntax
`\frac{a}{b}  x^2  x_1  \sqrt{x}  \mat{1&2;3&4}  \pi \theta \angle \deg \times \le \ge ...`

## DOCX mapping
Text -> paragraphs (Bengali runs flagged complex-script, Nirmala UI) | tables -> real tables | images -> pictures with crop |
equations -> Office Math | geometry (+ labels inside it) -> one PNG per diagram. The app warns about the diagram fallback and about overlapping objects.
Free-form positions become indents/spacing in reading order; DOCX is an editable copy, the project file is the source of truth.

## Release checklist (manual, on real devices)
1. **AC-01 offline**: airplane mode; create, edit, save, reopen, export PDF + DOCX.
   Audit: `aapt dump permissions app-release.apk` must not list INTERNET.
2. **AC-06 Bengali**: type the regression strings (SRS 9.1) + `ত্রিভুজ ABC-এর ∠A = ৩০° এবং AB = ৫ সেমি।`.
   Check on screen, in the exported PDF (open with a viewer on another device) and DOCX (Word + LibreOffice).
   If PDF conjuncts are wrong on some device, switch export to a shaping-aware renderer (see SRS 10.1).
3. **AC-02/03/04**: drag triangle vertex A; place a label inside/outside; angle slider through 0/90/180/270/360.
4. **AC-05/09**: save/reopen; undo/redo each edit type; force-stop mid-edit and relaunch.
5. **AC-07/08**: A4 PDF size, margins, no clipping, print preview; DOCX opens, text/tables editable.
6. Failure tests: low storage, invalid export destination, corrupt project file, missing image.
7. Phone + tablet, portrait + landscape, stylus.
8. Enable minification only after testing: `isMinifyEnabled = true` (needs keep rules for serialization).

## Known limitations
- Text does not auto-flow between pages; objects are free-positioned (move to next page by hand).
- No header/footer text (page numbers only), no multi-select/grouping, no zoom/pan, no rotated-text in DOCX.
- Objects overlapping in DOCX are stacked in reading order.
- Not compiled or device-tested by the author of this scaffold: run the checklist above.
