# Phase 120: Sleep Need UI - Context

**Gathered:** 2026-06-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Swift UI phase only. Replaces the hardcoded `480.0` fallback in Swift with a live call to the `sleep.compute_need` Rust bridge method, and renders the dynamic sleep need in the Sleep dashboard.

Requirements in scope: SLP-NEED-03
Out of scope: Rust changes (Phase 114 complete), body composition UI (Phase 121), stealth UI (Phase 122)

</domain>

<decisions>
## Implementation Decisions

### Bridge call placement
- **D-01:** New `@Published var dynamicSleepNeed: DynamicSleepNeed?` property on `HealthDataStore`. Bridge call lives in `HealthDataStore+Sleep.swift` (or existing sleep extension if one exists). Views observe via `@EnvironmentObject`. Consistent with all other metric `@Published` properties.

### Swift result type
- **D-02:** Local Swift struct (not a Rust-generated type) mirroring the bridge JSON:
  ```swift
  struct DynamicSleepNeed {
    let totalNeedMinutes: Double
    let baseNeedMinutes: Double
    let debtAdjustmentMinutes: Double
    let strainAdjustmentMinutes: Double
  }
  ```
  Nil when bridge returns an error or no history exists.

### Bridge args
- **D-03:** Pass `prior_strain: nil` — no strain adjustment. The `age_years` is derived from `OnboardingStorage.dateOfBirth` UserDefaults key (already used in `HealthDataStore+Snapshots.swift:1033`). If date of birth is absent, pass `age_years: nil` (Rust defaults to 26–64 bracket → 450 min per Phase 114 D-03).

### Display format
- **D-04:** Main label: `"\(h)h \(m)m recommended tonight"` where h/m come from `Int(totalNeedMinutes / 60)` and `Int(totalNeedMinutes.truncatingRemainder(dividingBy: 60))`. When minutes == 0, show just `"\(h)h recommended tonight"`.
- Label absent (view hidden) when `dynamicSleepNeed == nil`.

### Breakdown row
- **D-05:** Flat always-visible row below the main label, shown only when `dynamicSleepNeed != nil`. Shows base / debt / strain components as a single compact Text: e.g. `"Base 7.5h · Debt +15m · Strain +0m"`. No expansion/disclosure group.

### Hardcoded fallback replacement
- **D-06:** `HealthDataStore+Snapshots.swift` lines 28 and 68 have `"sleep_need_minutes": 480.0`. These are fallback values used when bridge fails. Replace the `480.0` with `dynamicSleepNeed?.totalNeedMinutes ?? 450.0` (aligns with Phase 114 D-03: age=nil defaults to 7.5h = 450 min).

### Where the UI change lands
- **D-07:** The static label `sleepNeededText` in `HealthSleepSheetsViews.swift` (line 149) should consume `dynamicSleepNeed`. Check `HealthSleepOverviewViews.swift` as well — if it has its own sleep need display, update it too.

### Claude's Discretion
- Bridge call dispatched to a background queue; result published `@MainActor`
- `refreshDynamicSleepNeed()` called alongside other `HealthDataStore.refresh*()` methods
- `#Preview` macro provides a static `DynamicSleepNeed` value (no bridge call in preview)
- No new Xcode project.pbxproj registration needed if added to existing extension file

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Primary files to modify
- `GooseSwift/HealthDataStore+Snapshots.swift` (or existing Sleep extension) — add `@Published var dynamicSleepNeed: DynamicSleepNeed?` + `refreshDynamicSleepNeed()` bridge call
- `GooseSwift/HealthSleepSheetsViews.swift` — replace static `sleepNeededText` with `dynamicSleepNeed`-driven display + breakdown row
- Check: `GooseSwift/HealthSleepOverviewViews.swift` — update any sleep need display if present

### Pattern references
- `GooseSwift/HealthDataStore+Snapshots.swift` lines 28, 68 — hardcoded `480.0` fallback to replace
- `GooseSwift/HealthSleepSheetsViews.swift` line 149 — `sleepNeededText` computed property to update
- `GooseSwift/HealthDataStore+Snapshots.swift` line 1033 — `OnboardingStorage.dateOfBirth` UserDefaults read pattern for age_years

### Rust bridge
- `Rust/core/src/bridge/sleep.rs` — `sleep.compute_need` args: `{database_path, age_years: Option<u8>, prior_strain: Option<f64>}`
- Bridge returns: `{total_need_minutes, base_need_minutes, debt_adjustment_minutes, strain_adjustment_minutes}`

### Requirements
- `.planning/REQUIREMENTS.md` §Sleep Need Algorithm (#164) — SLP-NEED-03

</canonical_refs>

<code_context>
## Existing Code Insights

- `SleepNeedResult` from Phase 114 (Rust) has 4 fields; bridge JSON key names use snake_case
- `HealthDataStore+Snapshots.swift:1033` already reads `OnboardingStorage.dateOfBirth` and computes age — reuse this pattern
- `sleepNeededText: String` computed property in `HealthSleepSheetsViews.swift:149` is the primary display site
- The `480.0` fallback in Snapshots is a Swift-side constant — NOT the same as the Rust hardcoded value replaced in Phase 114

</code_context>
