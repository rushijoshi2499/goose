---
phase: 49-healthdatastore-async-migration
plan: "03"
subsystem: healthdatastore
tags: [async, swift-concurrency, snapshots, recovery, GooseRustBridge]
dependency_graph:
  requires:
    - phase: 49-01
      provides: "requestAsync / requestValueAsync on GooseRustBridge"
  provides:
    - async runPacketScores (4 awaited bridge calls, HealthDataStore+Snapshots.swift)
    - async runSleepScore (1 awaited bridge call, HealthDataStore+Snapshots.swift)
    - async runRecoveryV1 (1 awaited bridge call, HealthDataStore+Recovery.swift)
  affects:
    - 49-04 (StagingSleep+Readiness migration — same async pattern)
    - 49-05 (Exercise+IMU+V24 migration — same async pattern)
    - 49-07 (final caller cleanup — partial callers addressed here)
tech-stack:
  added: []
  patterns:
    - "async func with all @MainActor state captured as local let before first await"
    - "Task{ await store.runXxx() } wrapper in Button actions and .onAppear/.onChange"
    - "Direct self.prop = result after await (safe: HealthDataStore is @MainActor)"
key-files:
  created: []
  modified:
    - GooseSwift/HealthDataStore+Snapshots.swift
    - GooseSwift/HealthDataStore+Recovery.swift
    - GooseSwift/HealthDataStore.swift
    - GooseSwift/HealthDashboardViews.swift
    - GooseSwift/HealthRecoveryStressViews.swift
    - GooseSwift/AppShellView.swift
key-decisions:
  - "sleepArgs extracted as local let before the first await in runPacketScores to avoid redundant merging calls post-suspension"
  - "refreshSleepAfterBandSync Task chain updated: await runSleepScore(), bare runSleepStaging() until 49-04 migrates it"
  - "HealthRecoveryStressViews.swift: runPacketScores+runRecoveryV1 wrapped in Task{}; runReadinessV1+runV24Biometrics remain bare calls until 49-04/05"
  - "Rule 1 auto-fix: four sync callers of now-async methods fixed immediately to unblock build (D-06 clean-build requirement)"
patterns-established:
  - "Pattern: caller migration is incremental — wrap only the newly-async methods in Task{}; leave still-sync methods as bare calls until their respective plan migrates them"
requirements-completed: [ASYNC-01, ASYNC-02]
duration: 4min
completed: "2026-06-10"
---

# Phase 49 Plan 03: Score Runners Async Migration (Snapshots + Recovery) Summary

**Migrated `runPacketScores` (4 bridge calls), `runSleepScore` (1 bridge call), and `runRecoveryV1` (1 bridge call) from `packetInputQueue.async` GCD dispatch to Swift Concurrency `async func` + `await bridge.requestAsync` — 6 total bridge calls now off the @MainActor.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-06-10T08:14:09Z
- **Completed:** 2026-06-10T08:18:00Z
- **Tasks:** 2 (+ 1 Rule-1/Rule-3 deviation fix)
- **Files modified:** 6

## Accomplishments

- `runPacketScores()` is now `async` — 4 sequential `await bridge.requestAsync(...)` calls replace the old `packetInputQueue.async { ... DispatchQueue.main.async { } }` wrapper
- `runSleepScore()` is now `async` — 1 awaited bridge call; sleepArgs captured as local `let` before the suspension point
- `runRecoveryV1()` is now `async` — 1 awaited bridge call; `databasePath`/`bridgeArgs` captured from @MainActor state before the await; the `guard/nil` early-return path simplified to a direct `self.recoveryV1Result = nil`
- All @Observable property mutations (packetScoreReports, recoveryV1Result, packetScoreStatus) happen directly on `self` after the `await` — Swift guarantees @MainActor re-entry (D-02)
- Build passes with zero errors and zero Swift Concurrency warnings after caller fixes

## Task Commits

Each task was committed atomically:

1. **Task 1: Migrate runPacketScores and runSleepScore to async (Snapshots)** — `9becfe1` (feat)
2. **Task 2: Migrate runRecoveryV1 to async (Recovery)** — `796f184` (feat)
3. **Deviation fix: wrap async callers in Task{} to unblock build** — `6ce536c` (fix)

## Files Created/Modified

- `GooseSwift/HealthDataStore+Snapshots.swift` — `runPacketScores` and `runSleepScore` converted to async; 5 awaited requestAsync calls; `packetInputQueue` removed; `DispatchQueue.main.async` removed
- `GooseSwift/HealthDataStore+Recovery.swift` — `runRecoveryV1` converted to async; 1 awaited requestAsync call; `packetInputQueue.async` wrapper and inner `Task { @MainActor }` closures removed
- `GooseSwift/HealthDataStore.swift` — `refreshSleepAfterBandSync` Task chain: `runSleepScore()` → `await self.runSleepScore()`; `runSleepStaging()` left as bare sync call (49-04 will migrate it)
- `GooseSwift/HealthDashboardViews.swift` — Button `runPacketInputs()` → `Task{ await }`, Button `runPacketScores()` → `Task{ await }` (Rule 3 fix)
- `GooseSwift/HealthRecoveryStressViews.swift` — `.onAppear`/`.onChange` `runPacketScores()`+`runRecoveryV1()` → `Task{ await ... }` (Rule 1 fix; `runReadinessV1`/`runV24Biometrics` still sync)
- `GooseSwift/AppShellView.swift` — `onHistoricalSyncCompleted` closure `runPacketInputs()` → `Task{ await }` (Rule 3 fix — this was missed in 49-02)

## Decisions Made

- sleepArgs extracted as a separate local `let` before the first `await` in `runPacketScores` to keep the code clean and avoid repeated `merging` expressions after the suspension point
- Incremental caller migration: only wrap the specific methods that became async in THIS plan; leave `runReadinessV1`, `runV24Biometrics`, `runSleepStaging` as bare sync calls in mixed call sites until their plans migrate them — avoids premature `await` on still-sync methods

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1/Rule 3 - Blocking] Wrapped sync callers of newly-async methods in Task{} to unblock build**
- **Found during:** Post-Task-2 build verification
- **Issue:** `runPacketScores`, `runSleepScore`, `runRecoveryV1`, and `runPacketInputs` (from 49-02) were called from sync contexts (Button actions, `.onAppear`, `.onChange`, `onHistoricalSyncCompleted` closure) — Swift 5.5+ rejects `async` calls from sync contexts, causing build failure
- **Fix:** Wrapped each call site in `Task { await store.runXxx() }`. Mixed call sites (e.g., `HealthRecoveryStressViews` calling both async and still-sync methods) use a single `Task { }` only for the async methods, leaving still-sync methods as bare calls
- **Files modified:** `AppShellView.swift`, `HealthDashboardViews.swift`, `HealthDataStore.swift`, `HealthRecoveryStressViews.swift`
- **Verification:** `xcodebuild build` → `** BUILD SUCCEEDED **` with zero errors
- **Committed in:** `6ce536c`

---

**Total deviations:** 1 auto-fixed (Rule 1/Rule 3 blocking)
**Impact on plan:** Required to satisfy D-06 (each plan builds cleanly). No scope creep — the caller fixes match the pattern specified in RESEARCH.md Pattern 3 and the 49-07 caller migration plan.

## Issues Encountered

- `AppShellView.swift` line 22 `runPacketInputs()` was not updated in 49-02 (it was supposed to be deferred to 49-07, but became a compile error immediately). Fixed as part of the deviation fix in this plan.

## Known Stubs

None. All 6 bridge calls are wired and awaited.

## Threat Flags

None. This change is architectural refactoring only — no new network endpoints, auth paths, file access patterns, or schema changes introduced.

## Self-Check: PASSED

- GooseSwift/HealthDataStore+Snapshots.swift: 5 await bridge.requestAsync — VERIFIED (grep count = 5)
- GooseSwift/HealthDataStore+Snapshots.swift: 0 packetInputQueue — VERIFIED (grep count = 0)
- GooseSwift/HealthDataStore+Recovery.swift: 1 await bridge.requestAsync — VERIFIED (grep count = 1)
- GooseSwift/HealthDataStore+Recovery.swift: 0 packetInputQueue — VERIFIED (grep count = 0)
- Total across both files: 6 await bridge.requestAsync — VERIFIED
- Commit 9becfe1 (Task 1): FOUND
- Commit 796f184 (Task 2): FOUND
- Commit 6ce536c (deviation fix): FOUND
- Build: ** BUILD SUCCEEDED ** — VERIFIED

## Next Phase Readiness

- 49-04 can proceed: `runSleepStaging` and `runReadinessV1` are still sync — ready to be migrated in the next wave plan
- 49-05 can proceed: `runExerciseSessions`, `runIMUStepCount`, `runV24Biometrics` are still sync — ready for migration
- `HealthRecoveryStressViews.swift` has partial Task{} wrapping — `runReadinessV1`/`runV24Biometrics` calls remain bare sync until 49-04/05 migrate them; no build issue since those methods are still sync

---
*Phase: 49-healthdatastore-async-migration*
*Completed: 2026-06-10*
