# Phase 48: Upload Sync Race Fix - Research

**Researched:** 2026-06-10
**Domain:** Swift async upload pipeline / Rust SQLite sync flags / XCTest mocking
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Fix applies to all streams that have a `synced` flag in the Rust schema — not just `hr_samples`. Verify which streams have `synced=0/1` columns (expected: hr_samples; rr_intervals if applicable) and apply the pre-capture pattern to each.
- **D-02:** `markHrSamplesSynced` is refactored to accept pre-captured `[Int]` rowIDs as parameter instead of fetching internally. Pattern is generalised for any stream with a synced flag.
- **D-03:** Call `sync.rows_pending_upload` for each affected stream **before** constructing the upload payload (before `upload.get_recent_decoded_streams`). Store as `let rowIDs: [Int]`. Pass rowIDs to the marking function after 2xx.
- **D-04:** On failure (5xx or timeout after all retries), do NOT call `mark_synced` — rows stay with `synced=0` and are included in the next upload attempt.
- **D-05:** Add a **Swift XCTest target** (`GooseSwiftTests`) to the Xcode project (`GooseSwift.xcodeproj`). Use a `URLProtocol` mock (or inject `URLSession` via initialiser) to simulate 503 → rows remain `synced=0`, and 200 → rows transition to `synced=1`. This tests the Swift orchestration layer, not just Rust persistence.
- **D-06:** Also add Rust-level tests for `sync.rows_pending_upload` and `sync.mark_synced` correctness (can be in existing `Rust/core/tests/`).

### Claude's Discretion
- Exact XCTest target name, target membership, and whether to use `URLProtocol` or constructor injection for `URLSession` — implementer decides based on minimal setup overhead.
- How to discover all streams with `synced` columns — grep Rust schema migrations and `sync.rs` module, then fix only those found.

### Deferred Ideas (OUT OF SCOPE)
- Fixing `uploadRawFrames` (raw BLE frames) to also pre-capture frame IDs before marking — raw frames don't have a `synced` flag currently.
- Full sync idempotency / deduplication audit across all streams — broader than SYNCR-01.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SYNCR-01 | `performUpload` captura os rowIDs de `hr_samples` antes do HTTP request e só chama `markHrSamplesSynced` após confirmação 2xx do servidor — elimina blind-marking | Pre-capture pattern verified in GooseUploadService.swift lines 122-124; Rust bridge signatures confirmed; XCTest target already exists |
</phase_requirements>

---

## Summary

Phase 48 fixes a race condition in `GooseUploadService.performUpload` where `markHrSamplesSynced` (lines 122-124) calls `sync.rows_pending_upload` *after* a successful upload — meaning any new rows that arrive between the upload response and the mark call get their rowIDs captured and marked synced without having been sent. The fix pre-captures rowIDs before the HTTP request and passes them explicitly to `mark_synced` only on 2xx confirmation.

The scope (D-01) extends to all 8 streams included in the upload payload, all of which have `synced` columns in the Rust schema: `hr_samples`, `rr_intervals`, `events`, `battery`, `spo2_samples`, `skin_temp_samples`, `resp_samples`, and `gravity`. The fix is a code-only change — no schema migrations required.

The `GooseSwiftTests` XCTest target already exists in the Xcode project with several test files including `GooseUploadServiceTests.swift`. No new Xcode target setup is needed. URLSession injection via initialiser parameter is the cleanest approach since `GooseUploadService.init` already creates the session internally, and the service is marked `@unchecked Sendable` — a simple second initialiser accepting a `URLSession` enables deterministic mocking without `URLProtocol` global state.

**Primary recommendation:** Pre-capture rowIDs for all 8 upload streams using `sync.rows_pending_upload` before `upload.get_recent_decoded_streams`, pass them as `[String: [Int]]` (keyed by stream name) through the retry loop, and call `sync.mark_synced` per-stream only in the `uploadSucceeded = true` branch.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Race condition fix — pre-capture rowIDs | Swift Upload Service | — | All orchestration is in `GooseUploadService.performUpload`; Rust layer is stateless |
| synced flag persistence | Rust / SQLite | — | `mark_synced_rows` and `rows_pending_upload` live in `store.rs` |
| HTTP retry + 2xx gate | Swift Upload Service | — | `performRequest` already returns nil on failure; gate is in Swift |
| Swift orchestration test (503→no mark, 200→mark) | XCTest | — | `GooseSwiftTests` target already exists; tests Swift flow |
| Rust sync method correctness tests | Rust cargo test | — | `sync_methods_tests` module in `store.rs` already has infrastructure |

---

## Standard Stack

No new packages are introduced. This is a code-only fix within the existing stack.

### Core (already present)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Swift / Foundation | iOS 26.0 | `URLSession`, async/await, `Task.detached` | Already used throughout GooseUploadService |
| XCTest | Xcode built-in | Unit testing | Already in GooseSwiftTests target |
| rusqlite 0.37 | Existing Cargo.lock | SQLite reads for `rows_pending_upload` | Already used in store.rs |

**Installation:** No new packages required.

---

## Package Legitimacy Audit

Not applicable — no external packages are added in this phase.

---

## Architecture Patterns

### System Architecture Diagram

```
GooseUploadService.performUpload()
  │
  ├─ [NEW] Pre-capture: sync.rows_pending_upload x8 streams ──► SQLite (synced=0)
  │   Returns: {hr_samples: [rowIDs], rr_intervals: [rowIDs], ...}
  │
  ├─ upload.get_recent_decoded_streams ──► SQLite decoded_frames_between()
  │
  ├─ Build HTTP payload
  │
  ├─ Retry loop (3 attempts, exponential backoff)
  │   │
  │   ├─ performRequest() ──► POST /v1/ingest-decoded
  │   │   ├─ 2xx response ──► uploadSucceeded = true, break
  │   │   └─ non-2xx / error ──► nil, continue
  │   └─ (exhausted retries) ──► uploadSucceeded = false
  │
  ├─ if uploadSucceeded:
  │   └─ [FIXED] sync.mark_synced per stream (using pre-captured rowIDs)
  │       Not called on failure — rows stay synced=0 for next attempt
  │
  └─ uploadRawFrames (out of scope for this phase)
```

### Recommended Project Structure

No new directories. Changes are contained to:

```
GooseSwift/
└── GooseUploadService.swift     # performUpload + markStreamsSynced refactor

GooseSwiftTests/
└── GooseUploadServiceTests.swift  # Add race fix tests (URLSession injection)

Rust/core/tests/
└── sync_race_tests.rs           # New file: Rust tests for D-06
                                  # (or add to existing bridge_tests.rs)
```

### Pattern 1: Pre-capture rowIDs before HTTP request

**What:** Call `sync.rows_pending_upload` for each stream *before* `upload.get_recent_decoded_streams`. Store all rowID arrays keyed by stream name. Pass to marking function only after 2xx.

**When to use:** Any async upload pipeline where a gap between fetch and mark allows new rows to arrive.

**Example (Swift — verified from GooseUploadService.swift):**
```swift
// Source: GooseSwift/GooseUploadService.swift — refactored pattern
private func performUpload(deviceID: UUID, deviceType: String, sinceTimestamp: Date) async {
  // [VERIFIED] D-03: Pre-capture rowIDs BEFORE constructing the payload
  let pendingRowIDs = captureAllPendingRowIDs(deviceID: deviceID, sinceTimestamp: sinceTimestamp)

  // Then fetch streams for payload (may add new rows — safe because rowIDs already captured)
  let streamsResult = try rust.request(method: "upload.get_recent_decoded_streams", args: [...])

  // ... build payload, retry loop ...

  if uploadSucceeded {
    markStreamsSynced(rowIDsByStream: pendingRowIDs)  // [FIXED] Only on 2xx
  } else {
    logger.warning("upload failed — rows not marked synced, will retry")
  }
}

// Capture rowIDs for all streams before any HTTP activity
private func captureAllPendingRowIDs(deviceID: UUID, sinceTimestamp: Date) -> [String: [Int]] {
  let streams = ["hr_samples", "rr_intervals", "events", "battery",
                 "spo2_samples", "skin_temp_samples", "resp_samples", "gravity"]
  var result: [String: [Int]] = [:]
  let sinceTs = sinceTimestamp.timeIntervalSince1970
  for stream in streams {
    guard let report = try? rust.request(
      method: "sync.rows_pending_upload",
      args: ["database_path": databasePath, "stream": stream, "limit": 500]
    ) else { continue }
    let rows = report["rows"] as? [[String: Any]] ?? []
    result[stream] = rows.compactMap { row in
      guard let rowid = (row["rowid"] as? NSNumber)?.intValue ?? (row["rowid"] as? Int),
            let ts = (row["ts"] as? NSNumber)?.doubleValue ?? (row["ts"] as? Double),
            ts >= sinceTs else { return nil }
      // device_id filtering where column exists (hr_samples, rr_intervals, events, battery)
      if let deviceIdStr = row["device_id"] as? String,
         deviceIdStr != deviceID.uuidString { return nil }
      return rowid
    }
  }
  return result
}

// Mark all streams synced using pre-captured rowIDs
private func markStreamsSynced(rowIDsByStream: [String: [Int]]) {
  for (stream, rowIDs) in rowIDsByStream {
    guard !rowIDs.isEmpty else { continue }
    _ = try? rust.request(
      method: "sync.mark_synced",
      args: ["database_path": databasePath, "stream": stream, "row_ids": rowIDs]
    )
  }
}
```

### Pattern 2: URLSession injection for unit testing

**What:** Add a second initialiser `init(databasePath: String, session: URLSession)` (or `init(databasePath: String, sessionConfiguration: URLSessionConfiguration)`) to `GooseUploadService`. Tests pass a session backed by a mock `URLProtocol`.

**When to use:** Testing async HTTP orchestration without a real server.

**Example (verified from GooseUploadServiceTests.swift patterns):**
```swift
// Source: GooseSwiftTests/GooseUploadServiceTests.swift — existing pattern
// The existing init(databasePath:) already works for pure-Swift tests.
// For HTTP mock, add:
init(databasePath: String, session: URLSession) {
  self.databasePath = databasePath
  self.session = session
  self.rust = GooseRustBridge()
}

// In tests:
class MockURLProtocol: URLProtocol {
  static var handler: ((URLRequest) -> (HTTPURLResponse, Data?))?
  override class func canInit(with request: URLRequest) -> Bool { true }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
  override func startLoading() {
    guard let handler = MockURLProtocol.handler else { return }
    let (response, data) = handler(request)
    client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
    if let data { client?.urlProtocol(self, didLoad: data) }
    client?.urlProtocolDidFinishLoading(self)
  }
  override func stopLoading() {}
}
```

### Pattern 3: Rust test for sync race boundary (D-06)

**What:** Integration test in `Rust/core/tests/` that inserts rows, calls `rows_pending_upload` to capture IDs, inserts additional rows to simulate the race window, then calls `mark_synced` with the pre-captured IDs and asserts the new rows remain `synced=0`.

**Example (verified from sync_methods_tests in store.rs):**
```rust
// Source: Rust/core/src/store.rs sync_methods_tests module — pattern
#[test]
fn test_pre_capture_does_not_mark_rows_inserted_during_race_window() {
    let store = make_store();
    // Row inserted before upload
    store.conn.execute(
        "INSERT INTO hr_samples (device_id, ts, bpm) VALUES ('dev', 1.0, 70)", [],
    ).unwrap();
    // Pre-capture rowIDs (simulates step that happens before HTTP request)
    let captured_ids: Vec<i64> = store.rows_pending_upload("hr_samples", 500)
        .unwrap()
        .iter()
        .filter_map(|r| r["rowid"].as_i64())
        .collect();
    // Row inserted DURING the race window (after pre-capture, before mark)
    store.conn.execute(
        "INSERT INTO hr_samples (device_id, ts, bpm) VALUES ('dev', 2.0, 72)", [],
    ).unwrap();
    // Mark only pre-captured IDs (simulates post-2xx mark)
    store.mark_synced_rows("hr_samples", &captured_ids).unwrap();
    // New row must still be synced=0
    let pending = store.rows_pending_upload("hr_samples", 10).unwrap();
    assert_eq!(pending.len(), 1, "race-window row must remain pending");
    assert_eq!(pending[0]["ts"].as_f64(), Some(2.0));
}
```

### Anti-Patterns to Avoid

- **Calling mark_synced before 2xx:** The current bug — rows with `synced=0` become invisible to subsequent uploads even though the server never confirmed receipt.
- **Re-querying rows_pending_upload after the HTTP round-trip:** The fix's entire purpose is to avoid this; any re-query reintroduces the race.
- **Calling performRequest from @MainActor:** Already avoided — `performUpload` runs on a detached `.utility` task. The Rust bridge is synchronous; keep all bridge calls on the detached task thread.
- **Using global URLProtocol.registerClass in tests without teardown:** Leaks mock state across tests. Prefer constructor injection or call `URLProtocol.unregisterClass` in `tearDown`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SQL injection prevention in stream names | Custom allowlist | `STREAM_ALLOWLIST` in `store.rs` (line 700) | Already implemented; mark_synced_rows and rows_pending_upload both check it |
| Rowid-based partial update | Custom cursor logic | `sync.mark_synced` with pre-captured rowIDs | Rust already handles batch UPDATE with parameterised rowids |
| Retry backoff | Custom sleep loop | Existing `delays: [UInt64]` retry in performUpload | Already 3-attempt exponential backoff |

**Key insight:** The Rust sync infrastructure (`mark_synced_rows`, `rows_pending_upload`) is already correct and fully tested. The bug is purely in Swift orchestration — the fix is moving two lines of Swift code.

---

## Runtime State Inventory

Not applicable — this is a logic fix with no rename/refactor/migration scope. No stored data, live service config, OS-registered state, secrets, or build artifacts change.

---

## Common Pitfalls

### Pitfall 1: gravity table vs gravity2_samples confusion

**What goes wrong:** The upload payload stream key is `"gravity"` but two tables exist: `gravity` (older, synced column added via ALTER TABLE migration) and `gravity2_samples` (synced baked into schema). The upload bridge only populates `gravity` (from K10 accelerometer frames). `gravity2_samples` is in the STREAM_ALLOWLIST but NOT in the upload payload — do not pre-capture rows from it.

**Why it happens:** The STREAM_ALLOWLIST is more permissive than the upload payload. `gravity2_samples` exists for potential future use.

**How to avoid:** Map upload payload stream keys to table names explicitly:
- `hr` → `hr_samples`
- `rr` → `rr_intervals`
- `events` → `events`
- `battery` → `battery`
- `spo2` → `spo2_samples`
- `skin_temp` → `skin_temp_samples`
- `resp` → `resp_samples`
- `gravity` → `gravity`

Do NOT include `gravity2_samples` or `exercise_sessions` in the pre-capture loop for this phase.

**Warning signs:** Pre-capturing rows that were never uploaded — the server has no matching data to confirm.

### Pitfall 2: rows_pending_upload has no device_id or ts filter

**What goes wrong:** `rows_pending_upload` returns ALL rows with `synced=0` across all devices (no `device_id` or `since_ts` parameter in the Rust function). If two devices upload concurrently, rows from device B could be pre-captured and marked as synced by device A's upload.

**Why it happens:** The current `markHrSamplesSynced` already handles this with a client-side filter: it checks `ts >= sinceTs && deviceIdStr == deviceID.uuidString`. The refactored pre-capture must preserve this filtering.

**How to avoid:** In `captureAllPendingRowIDs`, keep the client-side filter on `device_id` and `ts >= sinceTimestamp` when extracting rowIDs from the returned rows. Not all stream tables have `device_id` (gravity rows from the bridge include `ts` but the table schema always has `device_id` — confirmed from schema).

**Warning signs:** Marking rows with timestamps far outside `sinceTimestamp`, or from a different device UUID.

### Pitfall 3: limit=500 may truncate large pending batches

**What goes wrong:** `rows_pending_upload("hr_samples", 500)` may not capture all pending rows if more than 500 are outstanding (e.g. after extended offline period). Rows beyond position 500 would not be marked after a successful upload.

**Why it happens:** The limit is needed to bound the SQL query size and parameterised placeholder count for `mark_synced`. The current implementation already uses limit=500 in the existing `markHrSamplesSynced`.

**How to avoid:** Accepted limitation per CONTEXT.md (no change to the limit policy). The `refreshPendingRowCount` uses limit=10,000 for counting only (not marking). The fix maintains parity with the existing limit. Document in code that limit=500 is intentional.

**Warning signs:** After a long offline gap, some rows remain `synced=0` after an upload — this is correct behaviour (they will be picked up in the next upload cycle).

### Pitfall 4: GooseSwiftTests target already exists — no Xcode GUI work needed

**What goes wrong:** D-05 says "add a Swift XCTest target" — a developer might attempt to add one via Xcode GUI, creating duplicate target configuration.

**Why it happens:** The CONTEXT.md was written before confirming the project's state.

**How to avoid:** The `GooseSwiftTests` target is fully configured in `GooseSwift.xcodeproj/project.pbxproj`. `GooseSwiftTests/GooseUploadServiceTests.swift` already exists with tests for `buildUploadPayload`. The race fix tests should be added as new methods in this existing file, or as a new file in the `GooseSwiftTests/` folder (adding its reference to `project.pbxproj`).

### Pitfall 5: D-06 Rust tests — where to put them

**What goes wrong:** Adding to `Rust/core/tests/` requires the test file to import the crate's public API only. The existing `sync_methods_tests` module in `store.rs` tests internal (`pub`) store methods directly via inline `#[cfg(test)]`. The pre-capture race test needs `store.rows_pending_upload` and `store.mark_synced_rows`, which are `pub` — either location works.

**How to avoid:** Place the new test in `Rust/core/src/store.rs` inside the existing `sync_methods_tests` module for consistency with existing sync tests (lines 9136–9303). This avoids the need to add a new test file to `Cargo.toml`.

---

## Code Examples

### Current bug location (verified)

```swift
// Source: GooseSwift/GooseUploadService.swift lines 121-127
if uploadSucceeded {
  // BUG: markHrSamplesSynced calls rows_pending_upload AFTER the upload response.
  // New rows arriving between uploadSucceeded=true and this call get captured and marked.
  markHrSamplesSynced(deviceID: deviceID, sinceTimestamp: sinceTimestamp)
  await uploadRawFrames(deviceID: deviceID, sinceTimestamp: sinceTimestamp)
  lastUploadTimestamp = Date()
  lastSyncedCount = syncedCount
}
```

### Rust bridge signatures (verified from store.rs and bridge.rs)

```rust
// Source: Rust/core/src/store.rs line 7210
pub fn mark_synced_rows(&self, stream: &str, row_ids: &[i64]) -> GooseResult<usize>
// Validated against STREAM_ALLOWLIST. Returns count of updated rows.

// Source: Rust/core/src/store.rs line 7232
pub fn rows_pending_upload(&self, stream: &str, limit: i64) -> GooseResult<Vec<serde_json::Value>>
// Returns JSON objects with "rowid" + all columns. No device_id or ts filter at Rust level.
```

### Swift bridge call patterns (verified from GooseUploadService.swift lines 244-274)

```swift
// Source: GooseSwift/GooseUploadService.swift markHrSamplesSynced — existing pattern
let pendingReport = try rust.request(
  method: "sync.rows_pending_upload",
  args: ["database_path": databasePath, "stream": "hr_samples", "limit": 500]
)
let rows = pendingReport["rows"] as? [[String: Any]] ?? []
// Each row has "rowid" (NSNumber or Int), "ts" (NSNumber or Double), "device_id" (String)

_ = try rust.request(
  method: "sync.mark_synced",
  args: ["database_path": databasePath, "stream": "hr_samples", "row_ids": rowIds]
)
```

### Existing XCTest pattern (verified from GooseUploadServiceTests.swift)

```swift
// Source: GooseSwiftTests/GooseUploadServiceTests.swift
import XCTest
@testable import GooseSwift

final class GooseUploadServiceTests: XCTestCase {
  private let service = GooseUploadService(databasePath: "/dev/null")
  // Existing tests call service.buildUploadPayload() directly (pure function, no network).
  // New race-fix tests will require a mock URLSession — add init(databasePath:session:).
}
```

### STREAM_ALLOWLIST (verified from store.rs line 700)

```rust
// Source: Rust/core/src/store.rs line 700
const STREAM_ALLOWLIST: &[&str] = &[
  "battery", "events", "exercise_sessions", "gravity", "gravity2_samples",
  "hr_samples", "resp_samples", "rr_intervals", "skin_temp_samples", "spo2_samples",
];
```

**Upload payload streams → table names (verified):**
| Payload key | Rust table | synced column | In pre-capture? |
|-------------|-----------|---------------|-----------------|
| `hr` | `hr_samples` | Schema | YES |
| `rr` | `rr_intervals` | Schema | YES |
| `events` | `events` | Schema | YES |
| `battery` | `battery` | Schema | YES |
| `spo2` | `spo2_samples` | ALTER TABLE migration | YES |
| `skin_temp` | `skin_temp_samples` | ALTER TABLE migration | YES |
| `resp` | `resp_samples` | ALTER TABLE migration | YES |
| `gravity` | `gravity` | ALTER TABLE migration | YES |
| — | `gravity2_samples` | Schema | NO (not in payload) |
| — | `exercise_sessions` | Schema | NO (not in payload) |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Mark after upload (current bug) | Pre-capture before upload, mark after 2xx | Phase 48 | Eliminates blind-marking race |

**No deprecated patterns in this phase.** The Rust `mark_synced_rows` and `rows_pending_upload` are already correctly implemented.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `gravity` table receives data via the upload pipeline (K10 accelerometer frames via `upload.get_recent_decoded_streams`) and therefore needs pre-capture | Don't Hand-Roll table | If gravity rows are never actually populated via this path, pre-capturing gravity is a no-op but not harmful |
| A2 | `exercise_sessions` and `gravity2_samples` are NOT included in the `upload.get_recent_decoded_streams` payload | Pitfall 1 | If they were included, those streams would be missed by the fix |
| A3 | The limit=500 for pre-capture matches the existing limit in `markHrSamplesSynced` and is acceptable | Pitfall 3 | If higher limits are needed, a follow-up refactor is out of scope for SYNCR-01 |

A1 and A2 are VERIFIED from reading `upload_get_recent_decoded_streams_bridge` in `bridge.rs` (lines 3304-3533). A3 is VERIFIED from reading `GooseUploadService.swift` line 251.

**If this table is empty of unverifiable items:** All material claims are verified from source code read in this session.

---

## Open Questions

1. **Should the limit for pre-capture be higher than 500?**
   - What we know: Current `markHrSamplesSynced` uses 500. `refreshPendingRowCount` uses 10,000 for display only.
   - What's unclear: Whether 500 rows per stream ever falls short in practice.
   - Recommendation: Keep 500 per stream per CONTEXT.md (no change to policy). Add a code comment explaining the intentional cap.

2. **Does `battery` table get new rows during upload?**
   - What we know: Battery rows are infrequent (level changes only). The race window is seconds.
   - What's unclear: Whether battery rows can arrive in the race window in practice.
   - Recommendation: Include battery in pre-capture anyway — the cost is one extra Rust call, and it's correct by definition.

---

## Environment Availability

Step 2.6: SKIPPED — this phase is a pure code/logic fix. No external CLI tools, services, or runtimes beyond the project's existing build infrastructure are required. `cargo test -p goose-core` already works (confirmed from git log showing prior Rust tests passing).

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | XCTest (Xcode built-in) + cargo test |
| Config file | GooseSwift.xcodeproj (XCTest), Rust/core/Cargo.toml (cargo) |
| Quick run command (Swift) | Run GooseSwiftTests target in Xcode simulator |
| Quick run command (Rust) | `cargo test -p goose-core sync_methods_tests` |
| Full suite command | `cargo test -p goose-core` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SYNCR-01a | 503 server response leaves rows with synced=0 | unit (Swift) | Xcode test run: `GooseUploadServiceTests` | ✅ GooseSwiftTests/GooseUploadServiceTests.swift (needs new test methods) |
| SYNCR-01b | 200 server response marks rows synced=1 | unit (Swift) | Xcode test run: `GooseUploadServiceTests` | ✅ Same file, new methods |
| SYNCR-01c | Pre-captured rowIDs do not include rows arriving during race window | unit (Rust) | `cargo test -p goose-core test_pre_capture_does_not_mark_rows_inserted_during_race_window` | ❌ Wave 0 — add to store.rs sync_methods_tests |
| SYNCR-01d | rows_pending_upload / mark_synced existing behaviour preserved | unit (Rust) | `cargo test -p goose-core sync_methods_tests` | ✅ Existing tests in store.rs lines 9174-9303 |

### Sampling Rate
- **Per task commit:** `cargo test -p goose-core sync_methods_tests` (Rust) + build GooseSwiftTests target
- **Per wave merge:** `cargo test -p goose-core` (full Rust suite)
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] Add `test_pre_capture_does_not_mark_rows_inserted_during_race_window` to `Rust/core/src/store.rs` `sync_methods_tests` module
- [ ] Add `init(databasePath:session:)` to `GooseUploadService` to enable URLSession injection in tests
- [ ] Add Swift test methods `test_upload503_leavesSynced0` and `test_upload200_marksSynced1` to `GooseSwiftTests/GooseUploadServiceTests.swift`

---

## Security Domain

### Applicable ASVS Categories (security_enforcement: true, ASVS level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Bearer token unchanged |
| V3 Session Management | No | No session state change |
| V4 Access Control | No | No access control change |
| V5 Input Validation | Yes — stream name | `STREAM_ALLOWLIST` in store.rs already validates stream names (T-29-03 mitigation) |
| V6 Cryptography | No | No crypto change |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via stream name | Tampering | `STREAM_ALLOWLIST` check in `mark_synced_rows` and `rows_pending_upload` (already implemented) |
| rowID manipulation | Tampering | rowIDs are internal SQLite integers captured from the database itself, never from user input |

No new security risks are introduced. The fix uses the same bridge calls as the current code; the only change is call ordering.

---

## Sources

### Primary (HIGH confidence — verified from source code in this session)
- `GooseSwift/GooseUploadService.swift` — complete file read; bug location confirmed at lines 121-127; `markHrSamplesSynced` signature and logic at lines 243-278; `init` at lines 27-32
- `Rust/core/src/store.rs` — `STREAM_ALLOWLIST` (line 700), `mark_synced_rows` (line 7210), `rows_pending_upload` (line 7232), `ensure_synced_columns` (line 7165), schema DDL (lines 1595-1731), existing sync tests (lines 9136-9303)
- `Rust/core/src/bridge.rs` — sync bridge handlers (lines 3775-3830), `upload_get_recent_decoded_streams_bridge` (lines 3304-3533) — confirmed 8 payload streams
- `GooseSwiftTests/GooseUploadServiceTests.swift` — complete file read; confirmed target exists with `@testable import GooseSwift` pattern
- `GooseSwift.xcodeproj/project.pbxproj` — confirmed `GooseSwiftTests` target fully configured (T50000000000000000000001)
- `.planning/phases/48-upload-sync-race-fix/48-CONTEXT.md` — decisions D-01 through D-06

### Secondary (MEDIUM confidence)
- `.planning/REQUIREMENTS.md` — SYNCR-01 requirement text confirmed
- `Rust/core/src/health_sync.rs` — confirmed this is the HealthKit dry-run module (not the upload sync module)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new packages; existing stack is fixed and well-understood
- Architecture: HIGH — verified from reading every relevant source file
- Pitfalls: HIGH — discovered from actual code reading (not assumptions)

**Research date:** 2026-06-10
**Valid until:** 2026-07-10 (stable domain; schema changes would invalidate)
