# Native PDF completeness verification

This uses only the synthetic 35-student fixture in `PdfExportInstrumentedTest`, not real student records.

1. Build `:app:assembleDebug :app:assembleDebugAndroidTest` and install both APKs on an API 29+ emulator.
2. Run `com.gynda.fridaystm.ui.PdfExportInstrumentedTest` using the Android instrumentation runner. The test opens the generated file in Android's real `PdfRenderer`, asserts exactly three pages, renders each page, and saves a fixture manifest.
3. Pull `/sdcard/Android/data/com.gynda.fridaystm/files/ui-evidence/` **before stopping the emulator**.
4. In an isolated Python environment, install `tests/pdf/requirements.txt` and run:

   ```sh
   python tests/pdf/verify_native_pdf.py /path/to/pulled/ui-evidence
   ```

The parser checks each complete student row exactly once, including every numeric cell and lifecycle detail. Missing, duplicated or changed rows fail. It does not merely inspect a `%PDF` header. The manifest comes from the test inputs, not from extracted output.

The verifier's own negative controls run without third-party dependencies:

```sh
python3 -m unittest discover -s tests/pdf -v
```

CI runs those negative controls; the real native PDF fixture and independent parse are executed locally on the emulator, not by the JVM/Robolectric tests. After layout changes, also inspect all page renders. Counts/text extraction alone cannot establish visual readability.
