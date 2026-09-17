# Perioperative Planner — Android preview

Native Kotlin / Jetpack Compose Android app. The default **Anesthesia advisor**
turns patient findings into source-linked recommendations. Version 0.2.0-preview.

## Use

Choose **Neuraxial, Airway, Extubation, or Analgesia**, enter the relevant
findings, then select **Show recommendations**. **Edit inputs** recalculates
from the changed findings. **Example** creates a clearly labeled fictional
case showing all four modules, including residual neuromuscular block that
appropriately defers extubation. Change its TOF ratio to see the response.

**Full planner** retains the five-screen case, assessment, plan, OR preparation
and recovery workflow. Generated recommendations appear in the plan and the
exported handover, with reasons, missing inputs and source links.

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
- ASRA 5th-edition rules cover 19 explicitly listed regimens across 11 drugs.
  Spinal insertion, epidural insertion, catheter removal and restart are separate
  outputs. Dose **and indication** determine the selected regimen: AF apixaban
  2.5 mg twice daily remains in the high-dose pathway.
- Drug-specific withholding is calculated from the last actual administration;
  renal status, other antithrombotics, bleeding concerns, current INR and heparin
  monitoring are applied when relevant. Unsupported combinations, severe renal
  impairment and unimplemented catheter exposures do not receive guessed times.
- Restart uses the latest applicable needle, catheter-removal and surgery-end
  interval. It requires hemostasis, puncture status, thrombosis planning and
  consistent event order. Conservative upper ends of guideline ranges are named.
  Initial prophylaxis and bridging are outside this resumption calculator.
- Airway advice combines examination, previous difficulty, oxygenation / rescue
  risks and surgical access. It explains awake-intubation considerations and
  conditional device choices; it is not a validated difficulty score.
- Extubation checks readiness and quantitative neuromuscular recovery, highlights
  unmet criteria and proposes a reintubation / respiratory-support plan.
- Analgesia includes PROSPECT knee, hip (2026), elective cesarean and sternotomy
  guidance, ERAS open Whipple options, and baseline opioid / antagonist branches.
  Other operations receive clearly identified clinical planning suggestions.
  Medication strategies do not prescribe doses or perform opioid conversions.
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

- Domain.kt: case/decision models, source registry and basic calculations.
- Anticoagulation.kt: drug-specific withholding, removal and resumption engine.
- DecisionSupport.kt: airway, extubation and analgesia recommendations.
- Advisor.kt: input-to-recommendation interface.
- Content.kt: source-linked assessment cards and procedure checklists.
- Storage.kt: encrypted casebook and view model.
- MainActivity.kt / Screens.kt: native Android interface.
- Report.kt: handover and multi-page PDF export.
- EngineTest.kt / AnticoagulationTest.kt / DecisionSupportTest.kt: scope, clinical-rule boundaries, missing inputs, chronology and source mapping.
- WorkflowTest.kt: all four advisors, changing TOF, five-screen flow, encrypted save, recommendation provenance,
  handover PDF and activity recreation on an Android emulator.

## Verification

Run unit tests and lint/build before distributing an APK. The included
GitHub Actions workflow performs those checks, runs the workflow test on Android 15,
and uploads the APK, device screenshots and reports.
A compiled APK is a development preview, not clinical validation.

Gradle wrapper components retain their upstream Apache-2.0 licensing.
Clinical sources remain the property of their publishers; links are provided
in the app. Review questionnaire licensing before clinical distribution.
