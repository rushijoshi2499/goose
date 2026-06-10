---
phase: 49-healthdatastore-async-migration
plan: "02"
subsystem: healthdatastore
tags: [async, swift-concurrency, packet-inputs, GooseRustBridge]
dependency_graph:
  requires: [49-01]
  provides: [async-packetInputBridgeReports, async-runPacketInputs]
  affects:
    - GooseSwift/HealthDataStore+PacketInputs.swift
    - GooseSwift/HealthDataStore.swift
tech_stack:
  added: []
  patterns: [nonisolated static async, await bridge.requestAsync, Task shim for sync callers]
key_files:
  created: []
  modified:
    - GooseSwift/HealthDataStore+PacketInputs.swift
    - GooseSwift/HealthDataStore.swift
decisions:
  - "packetInputBridgeReports is now nonisolated static async — 21 calls use await bridge.requestAsync"
  - "runPacketInputs is now async func with no completion: parameter — @MainActor mutations after await"
  - "refreshSleepAfterBandSync completion closure replaced with Task { await ... } chain (internal caller)"
  - "packetInputQueue and heartRateTimelineQueue declarations retained for 49-07 removal"
  - "External callers (AppShellView, HealthDashboardViews) left untouched — 49-07 wraps them in Task { await ... }"
metrics:
  duration_minutes: 8
  completed_date: "2026-06-10"
  tasks_completed: 2
  files_modified: 2
requirements: [ASYNC-01, ASYNC-02]
---

# Phase 49 Plan 02: Migrate packetInputBridgeReports and runPacketInputs to async Summary

**One-liner:** Converted `packetInputBridgeReports` (21 bridge calls) and `runPacketInputs` to async/await — eliminating `packetInputQueue.async` dispatch and `DispatchQueue.main.async` round-trips for the largest single call-site batch in the migration.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Convert packetInputBridgeReports to nonisolated static async | 99ac5e5 | GooseSwift/HealthDataStore+PacketInputs.swift |
| 2 | Convert runPacketInputs to async, awaiting packetInputBridgeReports | f6e3e4b | GooseSwift/HealthDataStore.swift |

## What Was Built

### Task 1 — HealthDataStore+PacketInputs.swift

`packetInputBridgeReports(databasePath:)` converted from `nonisolated static func` (sync) to `nonisolated static func ... async`. All 21 sequential `try bridge.request(method:args:)` calls replaced with `try await bridge.requestAsync(method:args:)`. The function's own local `let bridge = GooseRustBridge()` instance is unchanged. The `do/catch` → `.success`/`.failure` Result wrapping is preserved exactly.

### Task 2 — HealthDataStore.swift

`runPacketInputs` converted from `func runPacketInputs(completion: (() -> Void)? = nil)` to `func runPacketInputs() async`. New body:
- Captures `databasePath` into a local `let` before the `await`
- Awaits `HealthDataStore.packetInputBridgeReports(databasePath: databasePath)` directly
- Mutates `@Observable` properties (`packetInputReports`, `packetInputStatus`, `packetInputIsRunning`) directly on `@MainActor` after the `await` — safe because `runPacketInputs` is an `@MainActor` method and Swift returns to the actor after suspension
- The `packetInputQueue.async` wrapper and inner `DispatchQueue.main.async` are removed entirely

In-file callers updated:
- `refreshPacketInputsIfNeeded`: bare `runPacketInputs()` → `Task { await self.runPacketInputs() }`
- `refreshPacketInputsAfterCapture`: `DispatchWorkItem` body updated from `self?.runPacketInputs()` → `Task { await self.runPacketInputs() }` (DispatchWorkItem/debounce mechanism preserved per RISK-04)
- `refreshSleepAfterBandSync`: `runPacketInputs { [weak self] in ... }` completion-closure replaced with `Task { [weak self] in await self.runPacketInputs(); self.runSleepScore(); self.runSleepStaging(); self.bandSleepImportStatus = ... }` chained call

`packetInputQueue` and `heartRateTimelineQueue` declarations retained (lines 53–54) — removal is deferred to 49-07 per D-05/D-06.

External callers in `AppShellView.swift` (line 22) and `HealthDashboardViews.swift` (line 569) are untouched — they will be wrapped in `Task { await ... }` in 49-07.

## In-File Task { await } Shims Added

These shims allow the sync callers within HealthDataStore.swift to call the now-async `runPacketInputs` without making the whole chain async. They must be finalized in 49-07:

| Location | Shim | Notes |
|----------|------|-------|
| `refreshPacketInputsIfNeeded` | `Task { await self.runPacketInputs() }` | Simple guard + call |
| `refreshPacketInputsAfterCapture` | `Task { await self.runPacketInputs() }` inside DispatchWorkItem | DispatchWorkItem debounce retained |
| `refreshSleepAfterBandSync` | `Task { [weak self] in await self.runPacketInputs(); ... }` | Chains runSleepScore/runSleepStaging |

## Deviations from Plan

None — plan executed exactly as written. The `refreshSleepAfterBandSync` completion-closure migration (completion → Task chain) was anticipated by the plan's instruction to handle in-file callers that must stay sync via `Task { await self.runPacketInputs() }`.

## Known Stubs

None. All 21 bridge calls are wired and awaited.

## Threat Flags

None. This change is architectural refactoring only — no new network endpoints, auth paths, file access patterns, or schema changes introduced.

## Self-Check: PASSED

- GooseSwift/HealthDataStore+PacketInputs.swift modified with 21 await bridge.requestAsync: VERIFIED (grep count = 21)
- GooseSwift/HealthDataStore.swift modified with async runPacketInputs: VERIFIED (grep count = 1)
- Commit 99ac5e5 (Task 1): FOUND
- Commit f6e3e4b (Task 2): FOUND
- packetInputQueue declaration retained: VERIFIED (grep count = 1)
- heartRateTimelineQueue declaration retained: VERIFIED (grep count = 1)
- No bare sync runPacketInputs() calls remain in HealthDataStore.swift: VERIFIED
- External callers (AppShellView, HealthDashboardViews) untouched: VERIFIED
