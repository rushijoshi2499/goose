---
phase: 50-morning-band-sleep-sync
plan: 02
subsystem: swift-coordinator
tags: [swift, sleep, ble, morning-sync, healthdatastore, userdefaults]

# Dependency graph
requires:
  - phase: 50-morning-band-sleep-sync
    plan: 01
    provides: gravity table populated by V24History frames; goose_ble in ALLOWED_EXTERNAL_SLEEP_PLATFORMS
  - GooseAppModel+Lifecycle.swift handleBLEConnectionStateChange (injection point for trigger)
  - HealthDataStore.markBandSleepSyncRequested / markBandSleepSyncFailed / refreshSleepAfterBandSync
  - store.gravity_rows_between bridge (SQLite-first check)
  - metrics.sleep_staging bridge (Cole-Kripke via gravity table)
  - sleep.import_external_history bridge (idempotent external session insert)

provides:
  - maybeScheduleMorningSleepSync() called from handleBLEConnectionStateChange when state==ready and overnightGuardActive==false
  - syncBandSleepHistory() full async flow: UserDefaults guard, SQLite-first check, BLE poll, staging, insert
  - Deterministic sleep_id band_ble.{deviceId}.{yyyy-MM-dd} prevents duplicate inserts
  - bandSleepImportStatus initial value "A aguardar sincronização" displayed in SleepV2BandSyncCard
  - GooseAppModel.healthStore weak var set by AppShellView for SleepSync coordination

affects:
  - SleepV2BandSyncCard (reads bandSleepImportStatus — now shows pt-PT strings on first load)
  - AppShellView.swift (sets model.healthStore in onAppear/onDisappear)
  - Every WHOOP morning reconnection triggers the sleep sync gate check

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "historicalSyncStatus polling (1s intervals, max 120 attempts) instead of onHistoricalSyncCompleted to avoid single-slot callback conflict"
    - "weak var healthStore on GooseAppModel set by AppShellView — avoids strong reference cycle, follows existing onHistoricalSyncCompleted pattern"
    - "UserDefaults written BEFORE first await in syncBandSleepHistory to prevent retry loop on drop+reconnect"
    - "Deterministic sleep_id: band_ble.{deviceId}.{yyyy-MM-dd} for idempotency at Rust UNIQUE constraint level"

key-files:
  created:
    - GooseSwift/GooseAppModel+SleepSync.swift
  modified:
    - GooseSwift/GooseAppModel+Lifecycle.swift
    - GooseSwift/GooseAppModel.swift
    - GooseSwift/AppShellView.swift
    - GooseSwift/HealthDataStore.swift
    - GooseSwift.xcodeproj/project.pbxproj

key-decisions:
  - "historicalSyncStatus polling chosen over onHistoricalSyncCompleted to avoid Pitfall #3 (AppShellView single-slot callback conflict) — per plan constraint and RESEARCH.md Open Question #2 resolution"
  - "weak var healthStore: HealthDataStore? added to GooseAppModel (set by AppShellView in onAppear) — GooseAppModel extensions have no direct healthStore access; weak reference avoids retain cycle"
  - "sleep_id format: band_ble.{deviceId}.{yyyy-MM-dd} deterministic, combined with UNIQUE(platform, platform_record_id) in Rust for real idempotency"
  - "UserDefaults written synchronously at top of syncBandSleepHistory before any await — prevents reconnect retry loops even if sync fails mid-flow"

# Metrics
duration: 30min
completed: 2026-06-10
---

# Phase 50 Plan 02: Morning Band Sleep Sync — Swift Coordinator Summary

**Morning sync trigger wired into handleBLEConnectionStateChange with full syncBandSleepHistory() flow: SQLite-first gravity check, BLE historical sync via historicalSyncStatus polling, Cole-Kripke sleep staging, and idempotent external_sleep_sessions insert**

## Performance

- **Duration:** 30 min
- **Started:** 2026-06-10T19:25:00Z
- **Completed:** 2026-06-10T19:55:00Z
- **Tasks:** 2
- **Files modified:** 5 (+ 1 created)

## Accomplishments

- GooseAppModel+SleepSync.swift created (182 lines): maybeScheduleMorningSleepSync(), syncBandSleepHistory() async, overnightWindow(), bandSleepId()
- maybeScheduleMorningSleepSync() called from handleBLEConnectionStateChange at end of state=="ready" branch when overnightGuardActive==false
- UserDefaults key goose.swift.last_band_sleep_sync_date written BEFORE first await (retry loop prevention)
- SQLite-first check via store.gravity_rows_between (threshold: 100 rows); BLE historical sync only triggered when gravity rows < 100
- BLE sync coordination via historicalSyncStatus polling (1s intervals, max 120 attempts = 2 min timeout); avoids AppShellView callback slot conflict
- staging_method "no_imu_data" guard: sets "A aguardar sincronização" and returns without inserting session
- On success: calls refreshSleepAfterBandSync(packetCount: 0) then sets "Sincronizado da pulseira"
- bandSleepImportStatus initial value changed from "No band sync yet" to "A aguardar sincronização" (pt-PT, SLP-SYNC-03)
- GooseAppModel.healthStore weak var added, set by AppShellView in onAppear/onDisappear
- GooseAppModel+SleepSync.swift added to Xcode project (project.pbxproj)
- Build succeeded: xcodebuild iPhone 17 simulator

## Task Commits

1. **Task 1: GooseAppModel+SleepSync.swift — full sync flow** - `e93fdef` (feat)
2. **Task 2: Wire trigger in +Lifecycle.swift + initial string in HealthDataStore.swift** - `91b41b2` (feat)

## Files Created/Modified

- `GooseSwift/GooseAppModel+SleepSync.swift` (new, 182 lines) — maybeScheduleMorningSleepSync(), syncBandSleepHistory() async, overnightWindow() static helper, bandSleepId() static helper
- `GooseSwift/GooseAppModel+Lifecycle.swift` — maybeScheduleMorningSleepSync() call added at end of state=="ready" branch
- `GooseSwift/GooseAppModel.swift` — weak var healthStore: HealthDataStore? added alongside onHistoricalSyncCompleted
- `GooseSwift/AppShellView.swift` — model.healthStore = healthStore in onAppear, model.healthStore = nil in onDisappear
- `GooseSwift/HealthDataStore.swift` — bandSleepImportStatus = "A aguardar sincronização" (was "No band sync yet")
- `GooseSwift.xcodeproj/project.pbxproj` — GooseAppModel+SleepSync.swift registered in PBXBuildFile, PBXFileReference, PBXGroup, PBXSourcesBuildPhase

## Decisions Made

- historicalSyncStatus polling chosen over onHistoricalSyncCompleted: the callback is a single slot owned by AppShellView (line 21); overwriting it would break the packet inputs refresh after manual user-triggered syncs (Pitfall #3). Polling is safe, bounded (2 min max), and avoids all race conditions.
- weak var healthStore on GooseAppModel: GooseAppModel extensions have no direct access to HealthDataStore. Added alongside the existing onHistoricalSyncCompleted pattern. AppShellView sets it in onAppear and clears it in onDisappear — same lifecycle as the callback.
- Deterministic sleep_id format: band_ble.{deviceId}.{yyyy-MM-dd} using local timezone date of overnightStart. Combined with Rust UNIQUE(platform, platform_record_id) constraint, this is the real idempotency guarantee (not the UserDefaults guard alone).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added GooseAppModel+SleepSync.swift to Xcode project (project.pbxproj)**
- **Found during:** Task 1 (first xcodebuild produced "cannot find 'maybeScheduleMorningSleepSync' in scope")
- **Issue:** New Swift file was not registered in the Xcode project; compiler could not see it
- **Fix:** Added PBXBuildFile (D1000000000000000000005D), PBXFileReference (D2000000000000000000005D), PBXGroup entry, and PBXSourcesBuildPhase entry in project.pbxproj using the next available sequential IDs
- **Files modified:** GooseSwift.xcodeproj/project.pbxproj
- **Verification:** Build succeeded after adding the entries

**2. [Rule 2 - Missing Critical] Added weak var healthStore: HealthDataStore? to GooseAppModel**
- **Found during:** Task 1 (GooseAppModel extensions have no healthStore property — AppShellView owns HealthDataStore)
- **Issue:** syncBandSleepHistory() needs to call markBandSleepSyncRequested, markBandSleepSyncFailed, refreshSleepAfterBandSync on HealthDataStore. Plan explicitly anticipated this: "If no direct reference exists, store a weak var healthStore: HealthDataStore? set by AppShellView"
- **Fix:** Added weak var to GooseAppModel.swift; updated AppShellView.swift to set/clear it in onAppear/onDisappear (same lifecycle as onHistoricalSyncCompleted)
- **Files modified:** GooseSwift/GooseAppModel.swift, GooseSwift/AppShellView.swift
- **Verification:** Build succeeded; no strong reference cycle (weak var)

---

**Total deviations:** 2 auto-fixed (1 Rule 3, 1 Rule 2)
**Impact on plan:** Both fixes were anticipated and necessary for the plan to compile and function correctly. No scope creep.

## Success Criteria Verification

1. GooseAppModel+SleepSync.swift exists and compiles — PASS (build succeeded)
2. maybeScheduleMorningSleepSync() called from handleBLEConnectionStateChange when state=="ready" and overnightGuardActive==false — PASS (line 166 of +Lifecycle.swift)
3. syncBandSleepHistory() writes UserDefaults at start before any await — PASS (first line of function body)
4. SQLite-first gravity check calls store.gravity_rows_between with threshold 100 — PASS
5. sleep_id is deterministic format "band_ble.{deviceId}.{yyyy-MM-dd}" — PASS (bandSleepId() helper)
6. staging_method == "no_imu_data" guard prevents insert and sets "A aguardar sincronização" — PASS
7. On success: bandSleepImportStatus = "Sincronizado da pulseira" — PASS
8. bandSleepImportStatus initial value in HealthDataStore.swift is "A aguardar sincronização" — PASS
9. xcodebuild compiles without errors — PASS (iPhone 17 simulator, Build SUCCEEDED)
10. No onHistoricalSyncCompleted slot conflict (uses historicalSyncStatus polling instead) — PASS

## Known Stubs

None — all data paths wired fully. syncBandSleepHistory() calls real bridge methods (store.gravity_rows_between, metrics.sleep_staging, sleep.import_external_history) with real args. HealthDataStore callbacks (markBandSleepSyncRequested, markBandSleepSyncFailed, refreshSleepAfterBandSync) are real methods already implemented.

## Threat Surface Scan

No new network endpoints or trust boundary changes introduced. All bridge calls use existing validated methods. The weak var healthStore reference follows the existing onHistoricalSyncCompleted callback pattern and introduces no new security surface.

Threat mitigations implemented as required by threat register:
- T-50-04: Deterministic sleep_id + UNIQUE Rust constraint prevent duplicate inserts
- T-50-05: Poll loop bounded to 120 iterations (2 min) with Task.sleep(1s); timeout calls markBandSleepSyncFailed
- T-50-06: guard !deviceId.isEmpty before any bridge call
- T-50-07: historicalSyncStatus polling avoids onHistoricalSyncCompleted conflict

## Self-Check: PASSED

- GooseSwift/GooseAppModel+SleepSync.swift exists: FOUND
- GooseSwift/GooseAppModel+Lifecycle.swift contains maybeScheduleMorningSleepSync: FOUND (line 166)
- GooseSwift/HealthDataStore.swift contains "A aguardar sincronização": FOUND (line 16)
- Commits e93fdef and 91b41b2: FOUND in git log

---
*Phase: 50-morning-band-sleep-sync*
*Completed: 2026-06-10*
