# Perioperative Planner — Android preview

Native Kotlin / Jetpack Compose application with five workflow screens:
Case, Assessment, Anesthesia plan, OR preparation, and Recovery.

## Use

Open **Cases → Add demo** to load a fictional knee-replacement example.
Select conditions on Case to reveal their assessment panels.
Add recommendation cards to the editable plan, complete the checklist,
then preview and export the handover as PDF or text.

Cases save in an AES-GCM encrypted file with an Android Keystore key.
Android backup is disabled. No Internet permission, analytics, backend,
login or automatic patient-data upload is included.
Exported PDF/text files are readable documents; choose their destination deliberately.

## Clinical preview scope

This development preview is for evaluation with fictional cases.
Clinical content has not received independent clinical validation.
Software tests check implementation behavior, not clinical safety.

- Unanswered inputs remain unknown, including incomplete RCRI/STOP-Bang factors.
- ASA, RCRI and functional capacity stay separate. No individual mortality,
  morbidity or ICU-admission percentage is invented.
- RCRI is suppressed for CABG, transplantation, obstetrics, trauma and incomplete
  adult cases in this preview.
- Limited apixaban time constraints follow ASRA 5th edition. Actual dose
  classification must be verified against the source table; it is not
  inferred from mg alone. Automatic branches require CrCl >=30 mL/min,
  no additional antithrombotics, no active bleeding concern and thrombosis review.
- Other drugs use clinician-entered, source-verified interval arithmetic.
  This is not a full implementation of ASRA.
- Restart requires a surgical/bleeding time restriction, hemostasis confirmation,
  puncture status and consistent event order. No result authorizes a procedure
  or medication administration.
- Regional entries distinguish route, concentration, dose, volume, baricity,
  formulation and adjuvants. Arithmetic volume = entered mg / entered mg/mL.
  The app does not choose dose, safe maximum, duration or dermatome.
- IV insulin calculations await a named validated institutional protocol.
- Dedicated procedure packs are planning checklists, not complete clinical protocols.
- Cards show source/edition and pending clinical review. Changed findings
  flag accepted actions for review.

## Build in Android Studio

1. Clone the repository and check out **codex/perioperative-planner**.
2. Open its root folder containing settings.gradle.kts.
3. Use JDK 17 and install Android SDK Platform 35.
4. Sync Gradle, select the **app** run configuration, and run on Android 8.0+.

The full Gradle wrapper is included; a separate Gradle installation is unnecessary.

Windows PowerShell:

    .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

macOS / Linux:

    ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk.
Build versions: AGP 8.10.1, Gradle 8.11.1, Kotlin 2.1.20, JDK 17,
Compose BOM 2025.05.01; min SDK 26 and target/compile SDK 35.

## Source layout

- Domain.kt: case/decision models, calculations and medication time constraints.
- Content.kt: source-linked assessment cards and procedure checklists.
- Storage.kt: encrypted casebook and view model.
- MainActivity.kt / Screens.kt: native Android interface.
- Report.kt: handover and multi-page PDF export.
- EngineTest.kt: unknown states, boundaries, scope and revision tests.
- WorkflowTest.kt: five-screen flow, encrypted save, recommendation provenance,
  handover PDF and activity recreation on an Android emulator.

## Verification

Run unit tests and lint/build before distributing an APK. The included
GitHub Actions workflow performs those checks, runs the workflow test on Android 15,
and uploads the APK, device screenshots and reports.
A compiled APK is a development preview, not clinical validation.

Gradle wrapper components retain their upstream Apache-2.0 licensing.
Clinical sources remain the property of their publishers; links are provided
in the app. Review questionnaire licensing before clinical distribution.
