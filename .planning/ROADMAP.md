# Roadmap: Goose

## Milestones

- ✅ **v1.0 Remote Server + Upstream PRs** — Phases 1-5 (shipped 2026-06-03)
- ✅ **v2.0 Multi-Device & Platform Foundations** — Phases 6-8+8.1 (shipped 2026-06-04)
- ✅ **v3.0 Wearable UX, CI Hardening & RTC Sync** — Phases 9-15 (shipped 2026-06-05)
- ✅ **v4.0 Security, Performance & Coach Expansion** — Phases 16-19 (shipped 2026-06-06)
- ✅ **v5.0 Metrics Accuracy, IMU & Upstream Fixes** — Phases 20-35 (shipped 2026-06-08)
- ✅ **v6.0 UI Wiring, Algorithm Alignment & Parity Validation** — Phases 36-45 (shipped 2026-06-09)
- ✅ **v7.0 Sync Correctness, Async & Sleep Sync** — Phases 46-50 (shipped 2026-06-10)
- ✅ **v8.0 Quality, Completeness & Backlog Clearance** — Phases 51-59+60 (shipped 2026-06-11)
- ✅ **v9.0 BLE Reliability & Protocol Parity** — Phases 61-65+66 (shipped 2026-06-11)
- **v10.0 Protocol Parity, Haptics & Feature Completeness** — Phases 67-73 (active)

## Phases

<details>
<summary>✅ v1.0 Remote Server + Upstream PRs (Phases 1-5) — SHIPPED 2026-06-03</summary>

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details>
<summary>✅ v2.0 Multi-Device & Platform Foundations (Phases 6-8+8.1) — SHIPPED 2026-06-04</summary>

Full details: `.planning/milestones/v2.0-ROADMAP.md`

Known deferred: WEAR-02 scan UI (v3.0), CR-02 per-row filter (v3.0), hardware BLE tests (no device)

</details>

<details>
<summary>✅ v3.0 Wearable UX, CI Hardening & RTC Sync (Phases 9-15) — SHIPPED 2026-06-05</summary>

Full details: `.planning/milestones/v3.0-ROADMAP.md`

</details>

<details>
<summary>✅ v4.0 Security, Performance & Coach Expansion (Phases 16-19) — SHIPPED 2026-06-06</summary>

Full details: `.planning/milestones/v4.0-ROADMAP.md`

Known deferred: COACH-06 device migration test, 4 streaming provider runtime tests, 3 localisation device tests

</details>

<details>
<summary>✅ v5.0 Metrics Accuracy, IMU & Upstream Fixes (Phases 20-35) — SHIPPED 2026-06-08</summary>

Full details: `.planning/milestones/v5.0-ROADMAP.md`

Key: HRV accuracy, Sleep staging (Cole-Kripke + 4-class), Strain/Calories (Ghidra-confirmed coefficients), V24 biometric decode, Exercise detection, Upload sync infrastructure, Readiness engine, Protocol corrections, Codebase audit (9 HIGH fixed), Cross-project review.

Known deferred: ALG-HRV-04, ALG-SLP-04, VAL-01 (human gates — require real WHOOP device data)

</details>

<details>
<summary>✅ v6.0 UI Wiring, Algorithm Alignment & Parity Validation (Phases 36-45) — SHIPPED 2026-06-09</summary>

Full details: `.planning/milestones/v6.0-ROADMAP.md`

Known deferred: ALG-HRV-04 real overnight cross-validation (v7.0), ALG-SLP-04 real overnight concordance (v7.0)

</details>

<details>
<summary>✅ v7.0 Sync Correctness, Async & Sleep Sync (Phases 46-50) — SHIPPED 2026-06-10</summary>

Full details: `.planning/milestones/v7.0-ROADMAP.md`

Key: Upload round-trip (POST /v1/ingest-frames + GET export), device_uuid end-to-end, upload sync race fix, HealthDataStore full async migration (60+ calls), morning band sleep sync (gravity K18/K24 → Cole-Kripke → external_sleep_sessions).

Known deferred: Phase 51 (VAL-HRV-01, VAL-SLP-01, SLP-SYNC real-device validation) — hardware gate, requires WHOOP + ≥5 overnight sessions

</details>

<details>
<summary>✅ v8.0 Quality, Completeness & Backlog Clearance (Phases 51-59+60) — SHIPPED 2026-06-11</summary>

Full details: `.planning/milestones/v8.0-ROADMAP.md`

- [x] Phase 51: Bug Audit — 3 HIGH + 6 MEDIUM bugs fixed in v6.0–v7.0 code
- [x] Phase 52: Quick Tasks & Surface Cleanup — BT Settings, CodeQL CI, HealthKit importer, #if DEBUG gating
- [x] Phase 53: Home Dashboard Completion — Device Status Card, Tools Grid, Evidence Footer
- [x] Phase 54: Coach Score Summaries & Journal — metric highlight grid, daily journal with persistence
- [x] Phase 55: Coach Routes — Sleep Coach, Recovery Insights, Strain Guidance, Stress Guidance views
- [x] Phase 56: Biometrics & Activity — fabricated 55.0 bpm eliminated; exercise-masked stress
- [x] Phase 57: Persistence & Calibration — energy_daily_rollup to SQLite; real train/holdout calibration
- [x] Phase 58: More Tab, Previews & Health Algorithms — MorePrivacyView, #Preview macros, algorithmPreferences
- [x] Phase 59: Band Sleep Import — bandSleepImportStatus replaces static "not available" UI
- [x] Phase 60: Band-First Sync — overnight poll loop removed; foreground-trigger + BGAppRefreshTask

Known deferred: ble-api-misuse-state-restore debug session (awaiting_human_verify); hardware gates (VAL-HRV-01, VAL-SLP-01, SLP-SYNC)

</details>

<details>
<summary>✅ v9.0 BLE Reliability & Protocol Parity (Phases 61-65+66) — SHIPPED 2026-06-11</summary>

Full details: `.planning/milestones/v9.0-ROADMAP.md`

- [x] Phase 61: BLE Bonding State Machine — 5-state GooseBLEBondingManager (WHPBLEBondingManager parity)
- [x] Phase 62: Upload Watermark per Sensor — per-type watermarks (rawFrames + decodedStreams)
- [x] Phase 63: Network Monitor & Upload Gating — NWPathMonitor gate + exponential backoff
- [x] Phase 64: HR Data Sanitizer — 25-220 BPM filter at BLE parsing chokepoint
- [x] Phase 65: Generic BLE State Machine — StateMachine<State: Hashable, Event> struct
- [ ] Phase 66: Cap Sense / On-Wrist Detection — DEFERRED (GATT UUID hardware-gated; 11500X series candidates documented)

Known deferred: CAPSENSE-01 hardware gate (requires real WHOOP 5.x device for UUID identification)

</details>

### v10.0 Protocol Parity, Haptics & Feature Completeness

- [ ] **Phase 67: WHOOP 5.0 Protocol Fixes** — R22 realtime packet parsing + v18 per-second historical decode (pure Rust, highest user impact)
- [ ] **Phase 68: BLE Manager Refactor + Data Validator** — GooseBLEHistoricalManager dedicated class + GooseBLEDataValidator Swift struct
- [ ] **Phase 69: Data Foundation** — 4 new SQLite tables (schema v20) + realtime strain accumulator
- [ ] **Phase 70: Haptic Primitive + Breathe Screen** — buzz(loops:) cmd 0x13 + Breathe UI with paced haptic cues
- [ ] **Phase 71: Coach VOW + NoopApp Features + Notifications + HR Decimation** — contextual nudges, Interval Timer, Metric Explorer, iOS local notifications, HR chart performance
- [ ] **Phase 72: Screens on New Foundation + Service Layer** — Stress/ANS view, Trends dashboard, Manual Workout Entry, protocol-based service layer + mocks
- [ ] **Phase 73: Smart Alarm + Wake-Window Engine** — HAP-03 single-shot alarm UI + HAP-04 RE-gated wake-window engine

## Phase Details

### Phase 51: Bug Audit

**Goal**: Known bugs and correctness issues from v6.0–v7.0 (phases 36–50) are identified, documented, and fixed
**Depends on**: Phase 50
**Requirements**: AUDIT-01
**Success Criteria** (what must be TRUE):

  1. Every phase 36–50 is reviewed and a written audit report lists findings by severity (HIGH / MEDIUM / LOW)
  2. All HIGH findings are fixed and verified before this phase closes
  3. No data race or crash-class finding remains open
  4. MEDIUM findings are either fixed or explicitly deferred with a rationale

**Plans**: TBD

### Phase 52: Quick Tasks & Surface Cleanup

**Goal**: Three long-deferred quick tasks ship and debug-only preview strings are removed from production builds
**Depends on**: Phase 51
**Requirements**: QT-01, QT-02, QT-03, SURF-01
**Success Criteria** (what must be TRUE):

  1. Tapping the BT button in the app opens iOS Bluetooth Settings directly
  2. A CodeQL workflow runs automatically on every PR and push via GitHub Actions and reports findings
  3. The user can trigger a HealthKit full import from the app and data appears in local storage
  4. A production build contains no fabricated preview values visible to the user (previewMissingData is #if DEBUG-gated)

**Plans**: TBD
**UI hint**: yes

### Phase 53: Home Dashboard Completion

**Goal**: HomeDashboardView shows a complete live Device Status Card, a Tools Grid of shortcuts, and an Evidence Footer
**Depends on**: Phase 52
**Requirements**: HOME-01, HOME-02, HOME-03
**Success Criteria** (what must be TRUE):

  1. The Home tab shows a Device Status Card with live device name, connection state, battery percent, current HR, last sync time, and a reconnect action when disconnected — never static text
  2. The Home tab shows a Tools Grid with shortcuts to Sleep Coach, Activity, Journal, and Calibration, each reflecting its bridge readiness state
  3. The Home tab shows an Evidence Footer with Rust core version, local store path, data mode, and provenance per metric family — tapping opens More > Debug

**Plans**: TBD
**UI hint**: yes

### Phase 54: Coach Score Summaries & Journal

**Goal**: Coach tab shows score summaries for all four metrics and users can write and persist a daily journal entry
**Depends on**: Phase 53
**Requirements**: COACH-07, COACH-08
**Success Criteria** (what must be TRUE):

  1. The Coach tab displays score summaries for sleep, recovery, strain, and stress — each populated from live bridge data
  2. The user can open a daily journal entry, write a text note, add optional tags, and save it — the entry persists across app restarts
  3. The most recent journal entry for a given date is recoverable after relaunching the app

**Plans**: TBD
**UI hint**: yes

### Phase 55: Coach Routes

**Goal**: Coach tab has four dedicated child route views — Sleep Coach, Recovery Insights, Strain Guidance, and Stress Guidance — each populated from bridge data
**Depends on**: Phase 54
**Requirements**: COACH-09, COACH-10, COACH-11, COACH-12
**Success Criteria** (what must be TRUE):

  1. Sleep Coach route shows wind-down time, target bedtime, wake time, and sleep debt/fulfillment from local data
  2. Recovery Insights route shows recovery score, HRV, RHR, respiratory rate, skin temp delta, and a deterministic recommendation
  3. Strain Guidance route shows strain score, target strain, exercise duration, daytime HR, and under/in/over-target guidance
  4. Stress Guidance route shows stress score, last HRV/HR, breakdown by level, and non-activity stress when available

**Plans**: TBD
**UI hint**: yes

### Phase 56: Biometrics & Activity

**Goal**: Recovery score uses real resting HR derived from V24 packet data, and non-activity stress only uses HR samples outside detected exercise sessions
**Depends on**: Phase 51
**Requirements**: BIO-05, ACT-01
**Success Criteria** (what must be TRUE):

  1. The recovery score computation uses z_rhr calculated from real SpO2/resp/wrist-temp V24 packet data — the fabricated 55.0 bpm baseline is removed
  2. Non-activity stress is computed and displayed (no longer shows "non-activity stress requires HR samples and activity masks")
  3. Stress windows exclude HR samples that fall within detected exercise session boundaries

**Plans**: TBD

### Phase 57: Persistence & Calibration

**Goal**: Daily stress history and Energy Bank state are persisted in SQLite, and the calibration pipeline runs real train/holdout splits from local metric history
**Depends on**: Phase 56
**Requirements**: ENB-01, CAL-01
**Success Criteria** (what must be TRUE):

  1. Daily stress windows and Energy Bank state are written to SQLite and survive app restarts — long-range trend data is available after multiple days
  2. The calibration pipeline runs against local historical metrics, producing real train/holdout split results
  3. Calibration output values are derived from actual data — the hardcoded "4 train / 2 holdout | improved" string is removed
  4. Calibration results are gated on a completed run; no results are shown if calibration has not run

**Plans**: TBD

### Phase 58: More Tab, Previews & Health Algorithms

**Goal**: More tab actions are fully backed by Swift bridge, SwiftUI previews exist for Home/Coach/More with simulator screenshots, and algorithm preference properties are wired in HealthDataStore
**Depends on**: Phase 55
**Requirements**: MORE-01, PREV-01, HALG-01
**Success Criteria** (what must be TRUE):

  1. More tab capture import, backfill, raw export, and privacy actions are enabled and functional
  2. SwiftUI previews exist for HomeDashboardView, CoachView, and More views covering connected/populated, disconnected, and no-data states — each verified with a simulator screenshot
  3. HealthDataStore exposes algorithmPreferences and referenceAlgorithmDefinitions properties wired to the bridge catalog — the Health > Algorithms section can display primary algorithm selection and reference definitions

**Plans**: TBD
**UI hint**: yes

### Phase 59: Band Sleep Import

**Goal**: Sleep records are ingested directly from BLE band packets — the "band sleep import not available" message is gone and real sleep data appears
**Depends on**: Phase 57
**Requirements**: BAND-01
**Success Criteria** (what must be TRUE):

  1. After a BLE connection, sleep records from band packets are persisted locally via the band sleep import path
  2. The Sleep tab no longer shows "band sleep import not available" when band data is present
  3. Sleep data imported via band packets is consistent with data imported via the server path for the same session

**Plans**: TBD

### Phase 67: WHOOP 5.0 Protocol Fixes

**Goal**: WHOOP 5.0 users receive realtime metrics and full per-second historical data — the two silent protocol gaps (R22 type 0x10 unhandled, v18 historical frames silently discarded) are fixed in Rust with no Swift changes required
**Depends on**: Phase 65
**Requirements**: BLE5-01, BLE5-02
**Success Criteria** (what must be TRUE):

  1. A WHOOP 5.0 device streaming R22 packets produces non-zero realtime HRV, HR, and recovery metrics in the Health tab (R22 type 0x10 parsed and routed)
  2. Historical sync for a WHOOP 5.0 device imports per-second rows into SQLite without duplicates (v18 decode + sequence_id dedup active)
  3. `cargo test -- protocol_tests` passes in full, including new round-trip tests for R22 type 0x10 and v18 per-second decode
  4. No Swift files are changed — the fix is entirely within `Rust/core/src/protocol.rs` and its test suite

**Plans**: TBD

### Phase 68: BLE Manager Refactor + Data Validator

**Goal**: Historical sync logic is decoupled from GooseBLEClient into a dedicated GooseBLEHistoricalManager, and a GooseBLEDataValidator struct gates structurally invalid BLE frames before they reach the Rust bridge
**Depends on**: Phase 67
**Requirements**: BLE5-03, BLE5-04
**Success Criteria** (what must be TRUE):

  1. A `GooseBLEHistoricalManager` class exists and owns the historical sync state machine; `GooseBLEClient` delegates to it via a proxy computed property that preserves all existing call sites
  2. A `GooseDataValidator` (or `GooseBLEDataValidator`) struct exists and rejects frames that fail structural invariants (minimum length, non-nil device ID, non-empty payload) before the Rust bridge is called
  3. Invalid frames are counted in a debug counter visible in More > Debug and logged via OSLog without crashing
  4. All existing historical sync behaviour is preserved — no regression in sync correctness

**Plans**: TBD

### Phase 69: Data Foundation

**Goal**: Four new SQLite tables (journal, workout, appleDaily, metricSeries) are migrated into the Rust store and a realtime strain accumulator publishes live strain during active workout sessions
**Depends on**: Phase 67
**Requirements**: DATA-01, DATA-02
**Success Criteria** (what must be TRUE):

  1. Schema version advances to v20; four new tables (journal, workout, appleDaily, metricSeries) are created on first launch after upgrade without data loss
  2. The strain tile on the workout screen updates at most every few seconds during an active session, driven by `GooseStrainAccumulator` receiving HR samples from `WhoopDataSignalPipeline`
  3. `cargo test` passes in full, including migration tests verifying the v19→v20 migration arm is idempotent
  4. Multiple concurrent GooseRustBridge instances writing to metricSeries produce no duplicate rows (idempotent insert pattern)

**Plans**: TBD

### Phase 70: Haptic Primitive + Breathe Screen

**Goal**: The app can command the WHOOP 5.0 strap to vibrate via BLE cmd 0x13, and the Breathe screen delivers a paced haptic session using that primitive
**Depends on**: Phase 65
**Requirements**: HAP-01, HAP-02
**Success Criteria** (what must be TRUE):

  1. A `buzz(loops:)` method exists on `GooseBLEClient` (via a new `GooseBLEClient+Haptics.swift` extension file) and issues cmd 0x13 over the established command characteristic — verified by OSLog output showing the write
  2. The Breathe screen is accessible from the app, completes a full breath cycle (inhale/hold/exhale), and calls `buzz(loops:)` at each phase transition to pace the user via strap vibration
  3. The Breathe session can be started and stopped by the user; stopping mid-session does not leave the BLE command characteristic in an undefined state
  4. No buzz is attempted when no WHOOP device is connected — the UI shows an appropriate disabled state

**Plans**: TBD
**UI hint**: yes

### Phase 71: Coach VOW + NoopApp Features + Notifications + HR Decimation

**Goal**: The Coach tab shows locally-computed contextual nudges, three NoopApp-derived features (Breathe UI access, Interval Timer, Metric Explorer) are reachable, iOS local notifications fire at the right moments, and the HR chart loads without lag on long sessions
**Depends on**: Phase 70
**Requirements**: FEAT-01, FEAT-02, FEAT-03, DATA-04
**Success Criteria** (what must be TRUE):

  1. The Coach tab displays at least one VOW (Voice of WHOOP) nudge computed locally from bridge data — nudges are contextual (e.g. low recovery warning, high strain alert) and require no server call
  2. Breathe UI, Interval Timer, and Metric Explorer are each reachable from the app and functional
  3. A local notification fires after a detected sleep cycle completes, after a workout session is detected, and when WHOOP battery drops below 20% — all using the existing `UNUserNotificationCenter` permission granted in onboarding
  4. The HR chart for a session longer than 60 minutes renders without visible lag; the in-memory sample count is reduced via stride/LTTB decimation while local extrema are preserved

**Plans**: TBD
**UI hint**: yes

### Phase 72: Screens on New Foundation + Service Layer

**Goal**: Three new SwiftUI screens (Stress/ANS view, Trends dashboard, Manual Workout Entry sheet) are delivered on the Phase 69 data tables, and GooseBLEClient/GooseRustBridge/HealthDataStore gain Swift protocols with corresponding mocks in the test target
**Depends on**: Phase 69
**Requirements**: DATA-03, ARCH-01
**Success Criteria** (what must be TRUE):

  1. A Stress/ANS view is accessible and shows ANS-derived tiles (HRV, RHR, stress level) populated from bridge data
  2. A Trends dashboard shows long-range metric history (≥7 days) sourced from the metricSeries table added in Phase 69
  3. A Manual Workout Entry sheet allows the user to log a workout with sport tag, duration, and perceived effort — the entry is persisted to the workout table
  4. `GooseBLEManaging`, `GooseRustBridging`, and `HealthDataStoring` protocols exist; mock implementations exist in the test target; at least 2 unit tests use the mocks and pass with `cargo test` or Swift test runner

**Plans**: TBD
**UI hint**: yes

### Phase 73: Smart Alarm + Wake-Window Engine

**Goal**: The user can schedule a single-shot strap vibration alarm at a fixed time (HAP-03), and the wake-window engine fires the alarm at the lightest-sleep moment within the user's alarm window (HAP-04, RE-gated)
**Depends on**: Phase 70
**Requirements**: HAP-03, HAP-04
**Note**: HAP-04 is RE-gated — implementation of the wake-window engine must not begin until BTSnoop capture of `STRAP_DRIVEN_ALARM_EXECUTED` and Ghidra decompilation of `SetAlarmInfoCommandPacketRev4` are both completed and documented in `.planning/research/whoop-re/`
**Success Criteria** (what must be TRUE):

  1. The Sleep Coach screen shows an alarm arm/cancel control; arming it schedules a single-shot vibration at the user-specified time via `AlarmCommandKind` + `writeAlarmCommand()` + `buzz(loops:)` confirmation
  2. Cancelling a scheduled alarm disarms it and updates the UI to reflect the cancelled state
  3. (HAP-04 — RE-gated) `GooseWakeWindowManager` exists and monitors sleep state via bridge polling; it fires `buzz(loops:)` at the lightest-sleep moment within the user's alarm window
  4. (HAP-04 — RE-gated) The wake-window wire format is derived from confirmed `SetAlarmInfoCommandPacketRev4` field layout — no speculative byte assignments

**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1–45 | v1.0–v6.0 | ✓ | Complete | 2026-06-03 to 2026-06-09 |
| 46–50 | v7.0 | 18/18 | Complete | 2026-06-10 |
| 51. Bug Audit | v8.0 | 0/TBD | Complete | 2026-06-11 |
| 52. Quick Tasks & Surface Cleanup | v8.0 | 0/TBD | Complete | 2026-06-11 |
| 53. Home Dashboard Completion | v8.0 | 0/TBD | Complete | 2026-06-11 |
| 54. Coach Score Summaries & Journal | v8.0 | 0/TBD | Complete | 2026-06-11 |
| 55. Coach Routes | v8.0 | 0/TBD | Complete | 2026-06-11 |
| 56. Biometrics & Activity | v8.0 | 0/TBD | Complete | 2026-06-11 |
| 57. Persistence & Calibration | v8.0 | 0/TBD | Complete | 2026-06-11 |
| 58. More Tab, Previews & Health Algorithms | v8.0 | 0/TBD | Complete | 2026-06-11 |
| 59. Band Sleep Import | v8.0 | 0/TBD | Complete | 2026-06-11 |
| 60. Band-First Sync | v8.0–v9.0 | 3/3 | Complete | 2026-06-11 |
| 61. BLE Bonding State Machine | v9.0 | 3/3 | Complete | 2026-06-11 |
| 62. Upload Watermark per Sensor | v9.0 | 2/2 | Complete | 2026-06-11 |
| 63. Network Monitor & Upload Gating | v9.0 | 2/2 | Complete | 2026-06-11 |
| 64. HR Data Sanitizer | v9.0 | 2/2 | Complete | 2026-06-11 |
| 65. Generic BLE State Machine | v9.0 | 1/1 | Complete | 2026-06-11 |
| 66. Cap Sense / On-Wrist Detection | v9.0 | 0/TBD | Not started | - |
| 67. WHOOP 5.0 Protocol Fixes | v10.0 | 0/TBD | Not started | - |
| 68. BLE Manager Refactor + Data Validator | v10.0 | 0/TBD | Not started | - |
| 69. Data Foundation | v10.0 | 0/TBD | Not started | - |
| 70. Haptic Primitive + Breathe Screen | v10.0 | 0/TBD | Not started | - |
| 71. Coach VOW + NoopApp Features + Notifications + HR Decimation | v10.0 | 0/TBD | Not started | - |
| 72. Screens on New Foundation + Service Layer | v10.0 | 0/TBD | Not started | - |
| 73. Smart Alarm + Wake-Window Engine | v10.0 | 0/TBD | Not started | - |

## Backlog

### Phase 999.5: GooseAppModel @Observable Migration (promoted to Phase 17 — v4.0)

Promoted to Phase 17: @Observable Migration.

---

### Phase 999.4: Recovery V2 Completion (promoted to Phase 13 — v3.0)

Promoted to Phase 13: Recovery V2 Dashboard.

---

### Phase 999.3: Apply upstream PR #15 (promoted to Phase 16 — v4.0)

Promoted to Phase 16: Deep Link Security.

---

### Phase 999.2: Multi-Language Support (promoted to Phase 14 — v3.0)

Promoted to Phase 14: pt-PT Localisation.

---

### Phase 999.1: Coach Multi-Provider & Custom Endpoint (promoted to Phase 18 — v4.0)

Promoted to Phase 18: Coach Multi-Provider.

### Phase 60: Band-First Sync

**Goal:** Align Goose's BLE sync architecture with the WHOOP app's band-first model, eliminating the need for continuous overnight BLE capture. The band stores data onboard; the app fetches it opportunistically on foreground and via silent push, exactly as WHOOP does.

**Depends on:** Phase 59
**Plans:** 3/3 plans complete
Plans:
**Wave 1**

- [x] 60-01-PLAN.md — Delete overnight guard subsystem core (3 files + GooseAppModel state + overnight struct types)
- [x] 60-02-PLAN.md — Add band-first sync: BandFirstSync.swift (foreground trigger + BGAppRefreshTask handler), BGTask registration, Info.plist keys

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 60-03-PLAN.md — Wire foreground trigger + clean secondary overnight references; build clean (wave 2)

### Phase 61: BLE Bonding State Machine

**Goal:** Replace the implicit OS bonding path with a formal bonding manager that tracks bond state through distinct steps, matching the `WHPBLEBondingManager` pattern from WHOOP (NotStarted → Started → Subscribed → Completed/Cancelled).

**Depends on:** Phase 60
**Requirements:** BLE-BOND-01
**WHOOP reference:** `WHPBLEBondingManager`, `WHPBLEBondingManagerProtocol`, states: `WHPBLEBondingNotStartedState`, `WHPBLEBondingStartedState`, `WHPBLEBondingSubscribedState`, `WHPBLEBondingCompletedState`, `WHPBLEBondingCancelledState`
**Success Criteria** (what must be TRUE):

1. A `GooseBLEBondingManager` type exists with the 5 formal states; bonding progress is observable from `GooseAppModel`
2. On BT reset or iOS reboot, the app detects bond loss, re-enters the bonding flow, and reconnects without user action
3. Bonding state is persisted across app restarts so the app can resume from the last known state
4. The existing string-based `connectionState` is replaced with the formal state machine output for the bonding portion

**Plans:** 3/3 plans complete
Plans:

**Wave 1**

- [x] 61-01-PLAN.md — Foundation: GooseBLEBondingState enum + GooseBLEBondingManager class + localized strings

**Wave 2** *(blocked on Wave 1)*

- [x] 61-02-PLAN.md — Integration: wire bonding manager into GooseBLEClient/delegate/commands + GooseAppModel observability + bond-loss detection

**Wave 3** *(blocked on Wave 2)*

- [x] 61-03-PLAN.md — Human-verify checkpoint: bond-loss recovery + persistence across restart

---

### Phase 62: Upload Watermark per Sensor

**Goal:** Track the last successfully uploaded timestamp per data type (raw frames, processed metrics) so restarts and partial uploads never re-send data already in TimescaleDB, matching WHOOP's `WHPStrapLatestUploadedMetricDateKey` / per-sensor high-water-mark pattern.

**Depends on:** Phase 61
**Requirements:** UPLOAD-WM-01
**WHOOP reference:** `WHPStrapLatestUploadedMetricDateKey`, `WHPStrapHighWaterMarkDateKey`, `WatermarksInteractor`, `StoredWatermarksAtHistoryComplete`, watermark shape `[Int: Date]` keyed by revision
**Success Criteria** (what must be TRUE):

1. A watermark is persisted (UserDefaults or SQLite) for each upload type (raw frames, daily metrics) and updated atomically on upload success
2. After a crash mid-upload, the next launch resumes from the watermark — no duplicate rows appear in TimescaleDB
3. The server-side `POST /v1/ingest-frames` endpoint rejects (or deduplicates) frames below the committed watermark
4. A reset path exists (`clearAllWatermarks`) for logout / device swap

**Plans:** 2/2 plans complete

- [x] 62-01-PLAN.md — Create GooseUploadWatermark store (UserDefaults, per-type Date, clearAllWatermarks)
- [x] 62-02-PLAN.md — Wire watermark into upload pipeline (gated sinceTimestamp, atomic write on 2xx, reset path)

---

### Phase 63: Network Monitor & Upload Gating

**Goal:** Gate all outbound uploads on network reachability, matching WHOOP's `WHPNetworkMonitor` pattern, and implement exponential-backoff retry so uploads fail visibly rather than silently when offline.

**Depends on:** Phase 62
**Requirements:** NET-MON-01
**WHOOP reference:** `WHPNetworkMonitor`, `WHPAccountCanUploadDataStatusChanged` notification (WHOOP also gates on account authorisation)
**Success Criteria** (what must be TRUE):

1. A `GooseNetworkMonitor` wraps `NWPathMonitor` and publishes a `isReachable: Bool` to `GooseAppModel`
2. Upload is not attempted when `isReachable == false`; queued work is retried automatically when connectivity returns
3. Upload failures due to server error (5xx) use exponential backoff (1s, 2s, 4s, max 60s) with a visible error state in the UI
4. Upload is gated on a non-empty device token (APNs registration must have succeeded at least once)

**Plans:** 2/2 plans complete
Plans:

**Wave 1**

- [x] 63-01-PLAN.md — Create GooseNetworkMonitor (NWPathMonitor wrapper) + wire isNetworkReachable into GooseAppModel + register in project.pbxproj

**Wave 2** *(blocked on Wave 1)*

- [x] 63-02-PLAN.md — Reachability + APNs-token upload gating, connectivity-return retry, 5xx exponential backoff (1/2/4s, max 60s) with visible error state, APNs registration AppDelegate

---

### Phase 64: HR Data Sanitizer

**Goal:** Add a Swift-side heart rate sanitization step between raw BLE notification bytes and `HeartRateSeriesStore`, matching WHOOP's `WHPHeartRateDataSanitizer`, to suppress physiologically impossible spikes before they reach the UI or Rust algorithms.

**Depends on:** Phase 60
**Requirements:** HR-SAN-01
**WHOOP reference:** `WHPHeartRateDataSanitizer`, `WHPHeartRateDecimator2` (decimation is a stretch goal)
**Success Criteria** (what must be TRUE):

1. A `GooseHRSanitizer` type filters HR samples outside a configurable valid range (e.g. 25–220 BPM) before they enter `HeartRateSeriesStore`
2. Spike samples are logged (OSLog) and counted in a debug counter visible in More > Debug
3. The live HR display never shows a value outside the valid range during normal wear
4. Sanitizer thresholds are constants (`static let`) not hard-coded literals

**Plans:** 2/2 plans complete
Plans:

**Wave 1**

- [x] 64-01-PLAN.md — GooseHRSanitizer (struct + static let 25/220 thresholds), wire at recordLiveHeartRate chokepoint to gate liveHeartRateBPM + HeartRateSeriesStore, hrSpikeCount + More > Debug counter, build clean

**Wave 2** *(blocked on Wave 1)*

- [x] 64-02-PLAN.md — Human-verify checkpoint: live HR stays in-range during normal wear + debug spike counter increments

---

### Phase 65: Generic BLE State Machine

**Goal:** Extract a lightweight reusable `StateMachine<State, Event>` type (matching `WHPStateMachine` + `WHPStateMachineState` + `WHPStateMachineEventDefinition`) and migrate the BLE connection and bonding state into it, replacing the ad-hoc string status scattered across `GooseBLEClient`.

**Depends on:** Phase 61
**Requirements:** SM-01
**WHOOP reference:** `WHPStateMachine`, `WHPStateMachineState`, `WHPStateMachineEventDefinition`
**Note:** Previously flagged as over-engineering for the codebase's current size — added at user request. Scope is deliberately minimal: one generic type + migration of BLE connection/bonding states only. No broader adoption beyond BLE layer unless a future phase warrants it.
**Success Criteria** (what must be TRUE):

1. A `StateMachine<State: Hashable, Event>` struct exists in `GooseBLETypes.swift` or a new `GooseStateMachine.swift`
2. BLE connection states (from Phase 61's bonding manager + existing connection states) are expressed as `StateMachine` instances
3. Invalid state transitions are asserted in DEBUG builds; in RELEASE they are no-ops that log an OSLog error
4. No reduction in observable behaviour — existing UI reflecting connection state continues to work

**Plans:** 1/1 plans complete

Plans:

- [x] 65-01-PLAN.md — Add generic StateMachine<State, Event> struct + migrate GooseBLEBondingManager onto it (SM-01)

---

### Phase 66: Cap Sense / On-Wrist Detection

**Goal:** Identify the GATT characteristic for WHOOP's capacitive skin-contact sensor (cap sense) via Ghidra and implement on-wrist detection in Goose, matching `WHPWhoopStrapCapSenseSuccessNotification` / `CapSenseFailed`, so physiological data is only trusted when the band is being worn.

**Depends on:** Phase 60
**Requirements:** CAPSENSE-01
**WHOOP reference:** `WHPWhoopStrapCapSenseSuccessNotification`, `WHPWhoopStrapCapSenseFailed`, `WHPWhoopStrapOnWrist`, `WHPWhoopStrapOffWrist`, `WHPWhoopStrapSensorsStatusLiveChangedNotification`
**Note:** GATT characteristic UUID for cap sense is not yet identified — this phase begins with a Ghidra investigation step. If the characteristic cannot be identified from the binary, the phase is blocked and deferred.
**Success Criteria** (what must be TRUE):

1. The cap sense GATT characteristic UUID is identified via Ghidra static analysis and documented in `.planning/research/whoop-re/`
2. `GooseBLEClient` subscribes to the cap sense characteristic and publishes `isOnWrist: Bool` to `GooseAppModel`
3. HR and HRV samples acquired while `isOnWrist == false` are flagged in SQLite (`on_wrist = 0`) and excluded from strain/recovery computations
4. The Device Status Card (Phase 53) shows an off-wrist indicator when detected

**Plans:** 0 plans

---

### Phase 999.6: body_hex Storage Optimization (absorbed into Phase 20 — v5.0)

Absorbed into Phase 20: Upstream Fixes & Storage (as PERF-05).
