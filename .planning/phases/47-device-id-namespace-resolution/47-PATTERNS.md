# Phase 47: Device ID Namespace Resolution - Pattern Map

**Mapped:** 2026-06-10
**Files analyzed:** 9 existing files modified (no new files)
**Analogs found:** 9 / 9

## File Classification

| Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------|------|-----------|----------------|---------------|
| `Rust/core/src/store.rs` (structs + migration + insert + read) | model/storage | CRUD | Self — existing `ensure_decoded_frame_columns()` + `insert_raw_evidence()` + `RawEvidenceRow` | exact |
| `Rust/core/src/capture_import.rs` (`CapturedFrameInput`) | model | transform | `store.rs` `RawEvidenceInput` serde pattern | exact |
| `Rust/core/src/bridge.rs` (`upload_get_raw_frames_for_upload_bridge`) | service/bridge | request-response | Self — existing `json!({})` frame serialisation block | exact |
| `GooseSwift/GooseBLEClient.swift` (add `connectedPeripheralUUID`) | service/BLE | event-driven | `GooseBLEClient.swift` lines 31-35 existing UUID properties | exact |
| `GooseSwift/GooseBLEClient+CentralDelegate.swift` (`didConnect` set UUID) | service/BLE | event-driven | Same file line 217 — UUID already extracted in log | exact |
| `GooseSwift/GooseAppModel+Lifecycle.swift` (`handleBLEConnectionStateChange`) | controller | event-driven | Self lines 115-124 — `activeDeviceID` wiring pattern | exact |
| `GooseSwift/CaptureFrameWriteQueue.swift` (add `currentDeviceUUID`) | service/queue | batch | Self lines 196-200 — `_activeDeviceID` / `activeDeviceID` NSLock pattern | exact |
| `GooseSwift/GooseUploadService.swift` (`uploadRawFrames`) | service | request-response | Self lines 148-167 — frames dict construction pattern | exact |
| `server/db/init.sql` (ADD COLUMN `device_uuid`) | config/migration | batch | Lines 123-136 — `ADD COLUMN IF NOT EXISTS device_generation TEXT` pattern | exact |
| `server/ingest/app/read.py` (`read_device_frames`) | service | CRUD | Self lines 296-385 — parameterised psycopg query | exact |
| `server/ingest/app/main.py` (`export_device_frames`) | controller | request-response | Self lines 469-481 — route handler with psycopg connection | exact |

---

## Pattern Assignments

### `Rust/core/src/store.rs` — Struct changes

**Role:** model/storage | **Data Flow:** CRUD

**Analog:** `store.rs` itself (existing `RawEvidenceInput`, `RawEvidenceRow`, `DecodedFrameRow` structs)

**Existing `RawEvidenceInput` struct** (lines 165-174) — add `device_uuid` field here:
```rust
#[derive(Debug, Clone)]
pub struct RawEvidenceInput<'a> {
    pub evidence_id: &'a str,
    pub source: &'a str,
    pub captured_at: &'a str,
    pub device_model: &'a str,
    pub payload: &'a [u8],
    pub sensitivity: &'a str,
    pub capture_session_id: Option<&'a str>,
    // NEW Phase 47:
    pub device_uuid: Option<&'a str>,
}
```

**Existing `RawEvidenceRow` struct** (lines 176-187) — add `device_uuid` field here:
```rust
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RawEvidenceRow {
    pub evidence_id: String,
    pub source: String,
    pub captured_at: String,
    pub device_model: String,
    pub payload_hex: String,
    pub sha256: String,
    pub sensitivity: String,
    #[serde(default)]
    pub capture_session_id: Option<String>,
    // NEW Phase 47:
    #[serde(default)]
    pub device_uuid: Option<String>,
}
```

**Existing `DecodedFrameRow` struct** (lines 199-218) — add `device_uuid` field at end:
```rust
// existing fields unchanged; add:
pub device_uuid: Option<String>,
```

---

### `Rust/core/src/store.rs` — `ensure_raw_evidence_columns()` migration

**Analog:** `store.rs` lines 6861-6873 (exact same function — extend the tuple list)

**Existing pattern** (lines 6861-6873):
```rust
fn ensure_raw_evidence_columns(&self) -> GooseResult<()> {
    let columns = self.table_columns_unchecked("raw_evidence")?;
    for (column, ddl) in [(
        "capture_session_id",
        "capture_session_id TEXT REFERENCES capture_sessions(session_id) ON DELETE SET NULL",
    )] {
        if !columns.contains(column) {
            self.conn
                .execute(&format!("ALTER TABLE raw_evidence ADD COLUMN {ddl}"), [])?;
        }
    }
    Ok(())
}
```

**What to change:** Extend the array with a second tuple `("device_uuid", "device_uuid TEXT")`, then after the loop add:
```rust
self.conn.execute(
    "CREATE INDEX IF NOT EXISTS idx_raw_evidence_by_device_uuid \
     ON raw_evidence(device_uuid, captured_at)",
    [],
)?;
```
Note: column is `captured_at` (not `ts`) — verified at store.rs line 1110.

**Existing `ensure_decoded_frame_columns()` pattern** (lines 6875-6890) — same approach, add `("device_uuid", "device_uuid TEXT")` tuple:
```rust
fn ensure_decoded_frame_columns(&self) -> GooseResult<()> {
    let columns = self.table_columns_unchecked("decoded_frames")?;
    for (column, ddl) in [
        ("packet_type_name", "packet_type_name TEXT"),
        (
            "parsed_payload_json",
            "parsed_payload_json TEXT NOT NULL DEFAULT 'null'",
        ),
    ] {
        if !columns.contains(column) {
            self.conn
                .execute(&format!("ALTER TABLE decoded_frames ADD COLUMN {ddl}"), [])?;
        }
    }
    Ok(())
}
```
No index on `decoded_frames.device_uuid` — success criteria only require the index on `raw_evidence`.

---

### `Rust/core/src/store.rs` — `insert_raw_evidence()` extension

**Analog:** `store.rs` lines 2177-2234 (exact same function — extend column list and params)

**Existing INSERT** (lines 2190-2213):
```rust
let mut statement = self.conn.prepare_cached(
    r#"
    INSERT OR IGNORE INTO raw_evidence (
        evidence_id,
        source,
        captured_at,
        device_model,
        payload_hex,
        sha256,
        sensitivity,
        capture_session_id
    ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
    "#,
)?;
let changed = statement.execute(params![
    input.evidence_id,
    input.source,
    input.captured_at,
    input.device_model,
    payload_hex,
    sha256,
    input.sensitivity,
    input.capture_session_id
])?;
```

**What to change:** Add `device_uuid` as column 9 (`?9`) and `input.device_uuid` as the 9th param.

---

### `Rust/core/src/store.rs` — `raw_evidence()` and `raw_evidence_between()` read paths

**Analog:** `store.rs` lines 5079-5147 (exact same functions — extend SELECT column list and row mapper)

**Existing SELECT** (lines 5082-5106) — add `device_uuid` to the SELECT list and `row.get(8)?` in the mapper:
```rust
SELECT
    evidence_id, source, captured_at, device_model,
    payload_hex, sha256, sensitivity, capture_session_id,
    device_uuid  -- NEW
FROM raw_evidence
```
Row mapper: add `device_uuid: row.get(8)?` to `RawEvidenceRow { ... }`.

Same change applies to `raw_evidence_between()` at lines 5113-5147.

---

### `Rust/core/src/capture_import.rs` — `CapturedFrameInput` struct

**Role:** model | **Data Flow:** transform

**Analog:** `capture_import.rs` lines 64-78 (existing `CapturedFrameInput` — `#[serde(default)]` nullable field pattern)

**Existing struct** (lines 64-78):
```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapturedFrameInput {
    pub evidence_id: String,
    #[serde(default)]
    pub frame_id: Option<String>,
    pub source: String,
    pub captured_at: String,
    pub device_model: String,
    pub frame_hex: String,
    pub sensitivity: String,
    #[serde(default)]
    pub capture_session_id: Option<String>,
    #[serde(default = "default_device_type")]
    pub device_type: DeviceType,
}
```

**What to add** (copy the `capture_session_id` pattern exactly):
```rust
#[serde(default)]
pub device_uuid: Option<String>,
```

Then in the function that builds `RawEvidenceInput` from `CapturedFrameInput`, pass `device_uuid: frame.device_uuid.as_deref()`.

---

### `Rust/core/src/bridge.rs` — `upload_get_raw_frames_for_upload_bridge`

**Role:** service/bridge | **Data Flow:** request-response

**Analog:** `bridge.rs` lines 3597-3624 (exact same function)

**Existing `json!({})` frame serialisation** (lines 3605-3617):
```rust
let frames: Vec<serde_json::Value> = rows
    .iter()
    .map(|r| {
        let captured_at_unix: f64 = iso8601_to_unix(&r.captured_at);
        json!({
            "captured_at_unix": captured_at_unix,
            "frame_hex": r.payload_hex,
            "source": r.source,
            "device_type": "GOOSE",
            "device_model": r.device_model,
            "sensitivity": r.sensitivity,
        })
    })
    .collect();
```

**What to add:** `"device_uuid": r.device_uuid` inside the `json!({})` block. `serde_json` serialises `Option<String>` as `null` when `None`, which is correct for the iOS upload service to read as `nil`.

---

### `GooseSwift/GooseBLEClient.swift` — add `connectedPeripheralUUID`

**Role:** service/BLE | **Data Flow:** event-driven

**Analog:** `GooseBLEClient.swift` lines 31-35 (existing UUID property declarations)

**Existing pattern** (lines 31-35):
```swift
var activeDeviceIdentifier: UUID?
var selectedDeviceID: UUID?
var connectedAt: Date?
```

**What to add** — after existing UUID properties, same declaration style:
```swift
var connectedPeripheralUUID: String?
```

Note: declared as `String?` not `UUID?` because `peripheral.identifier.uuidString` is already the string form needed downstream. Consistent with how `activeDeviceIdentifier?.uuidString` is used everywhere.

---

### `GooseSwift/GooseBLEClient+CentralDelegate.swift` — set UUID in `didConnect`

**Role:** service/BLE | **Data Flow:** event-driven

**Analog:** `GooseBLEClient+CentralDelegate.swift` line 217 (UUID already extracted in log line — just assign it to the new property)

**Existing log line** (line 217):
```swift
record(source: "ble", title: "connect.succeeded", body: "\(peripheral.name ?? fallbackName ?? "WHOOP") \(peripheral.identifier.uuidString) evidence=\(evidence)")
```

**What to add** — immediately before or after the `record(...)` call:
```swift
connectedPeripheralUUID = peripheral.identifier.uuidString
```

---

### `GooseSwift/CaptureFrameWriteQueue.swift` — add `currentDeviceUUID` and update `CapturedFrameWriteRow`

**Role:** service/queue | **Data Flow:** batch

**Analog:** `CaptureFrameWriteQueue.swift` lines 196-200 (existing `_activeDeviceID` / `activeDeviceID` NSLock-guarded property)

**Existing thread-safe property pattern** (lines 196-200):
```swift
private var _activeDeviceID: String?
var activeDeviceID: String? {
    get { stateLock.withLock { _activeDeviceID } }
    set { stateLock.withLock { _activeDeviceID = newValue } }
}
```

**What to add** — immediately after the `activeDeviceID` block, same pattern:
```swift
private var _currentDeviceUUID: String?
var currentDeviceUUID: String? {
    get { stateLock.withLock { _currentDeviceUUID } }
    set { stateLock.withLock { _currentDeviceUUID = newValue } }
}
```

**`CapturedFrameWriteRow` struct** (lines 51-75) — add stored property (captured at enqueue time to avoid race on reconnect — see Pitfall 3 in RESEARCH.md):

**Existing struct** (lines 51-74):
```swift
struct CapturedFrameWriteRow {
  let evidenceID: String
  let frameID: String
  let source: String
  let capturedAt: String
  let deviceModel: String
  let frameHex: String
  let sensitivity: String
  let captureSessionID: String?
  let deviceType: String

  var bridgeObject: [String: Any] {
    [
      "evidence_id": evidenceID,
      "frame_id": frameID,
      "source": source,
      "captured_at": capturedAt,
      "device_model": deviceModel,
      "frame_hex": frameHex,
      "sensitivity": sensitivity,
      "capture_session_id": captureSessionID ?? NSNull(),
      "device_type": deviceType,
    ]
  }
}
```

**What to add:** `let deviceUUID: String?` stored property and `"device_uuid": deviceUUID ?? NSNull()` in `bridgeObject`. The `NSNull()` fallback matches the existing `captureSessionID ?? NSNull()` pattern exactly.

---

### `GooseSwift/GooseAppModel+Lifecycle.swift` — wire UUID in `handleBLEConnectionStateChange`

**Role:** controller | **Data Flow:** event-driven

**Analog:** `GooseAppModel+Lifecycle.swift` lines 115-125 (existing `activeDeviceID` wiring — exact same call site)

**Existing wiring** (lines 115-125):
```swift
func handleBLEConnectionStateChange(_ state: String) {
    if state == "ready" {
      connectedDeviceGeneration = ble.discoveredDevices
        .first(where: { $0.id == ble.activeDeviceIdentifier })?.generation
      captureFrameWriteQueue.activeDeviceID = ble.activeDeviceIdentifier?.uuidString
    } else {
      connectedDeviceGeneration = nil
      captureFrameWriteQueue.activeDeviceID = nil
    }
    // ...
}
```

**What to add** inside `if state == "ready"` block:
```swift
if let uuid = ble.connectedPeripheralUUID,
   let deviceModel = ble.activeDeviceName {
    captureFrameWriteQueue.currentDeviceUUID = uuid
    var map = (UserDefaults.standard.object(forKey: "goose.swift.device_uuid_map")
        .flatMap { JSONSerialization.jsonObject(with: $0 as! Data) as? [String: String] }) ?? [:]
    map[uuid] = deviceModel
    if let data = try? JSONSerialization.data(withJSONObject: map) {
        UserDefaults.standard.set(data, forKey: "goose.swift.device_uuid_map")
    }
}
```

And inside the `else` block:
```swift
captureFrameWriteQueue.currentDeviceUUID = nil
```

**UserDefaults key constant** — add to an appropriate `static let` namespace (see RESEARCH.md Pattern 3; either extend `GooseBLEClient.DefaultsKey` or create a small storage enum). Copy the existing `RemoteServerStorage` or `GooseBLEClient.DefaultsKey` pattern — static let in the enclosing type, reverse-DNS string.

---

### `GooseSwift/GooseUploadService.swift` — extend `uploadRawFrames`

**Role:** service | **Data Flow:** request-response

**Analog:** `GooseUploadService.swift` lines 148-184 (exact same function — frames dict passes through from bridge response)

**Existing frame payload construction** (lines 163-167):
```swift
let frames = framesResult["frames"] as? [Any] ?? []
guard !frames.isEmpty else { return }

let deviceDict: [String: Any] = ["id": deviceID.uuidString, "mac": NSNull(), "name": NSNull()]
let payload: [String: Any] = ["device": deviceDict, "frames": frames]
```

**What to change:** The `frames` array already contains the per-frame dicts from the bridge response (including the new `device_uuid` key once bridge.rs is updated). No structural change needed to `uploadRawFrames` — the frame dicts pass through as-is. The Pydantic `IngestFrame` model on the server side needs `device_uuid: str | None = None` added.

If the `device_uuid` must also be injected at upload time from UserDefaults (fallback path), the pattern is:
```swift
let uuidMap = (UserDefaults.standard.data(forKey: "goose.swift.device_uuid_map")
    .flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: String] }) ?? [:]
```
Then look up `uuidMap[deviceID.uuidString]` to get the device model (for cross-reference), but per RESEARCH.md Open Question 3, the cleaner path is bridge.rs including `device_uuid` in its response — prefer that.

---

### `server/db/init.sql` — `raw_frames` column migration

**Role:** config/migration | **Data Flow:** batch

**Analog:** `server/db/init.sql` lines 123-136 (`ADD COLUMN IF NOT EXISTS device_generation TEXT` pattern)

**Existing idempotent migration pattern** (lines 129-136):
```sql
ALTER TABLE hr_samples        ADD COLUMN IF NOT EXISTS device_generation TEXT DEFAULT '5.0';
ALTER TABLE rr_intervals      ADD COLUMN IF NOT EXISTS device_generation TEXT DEFAULT '5.0';
-- ...
```

**What to add** after the existing `raw_frames` table block (after line 121):
```sql
-- Phase 47: CoreBluetooth peripheral UUID on raw_frames. Idempotent.
ALTER TABLE raw_frames ADD COLUMN IF NOT EXISTS device_uuid TEXT;
CREATE INDEX IF NOT EXISTS raw_frames_device_uuid ON raw_frames (device_uuid, captured_at);
```

No DEFAULT — NULL is the correct value for pre-migration rows (D-07).

---

### `server/ingest/app/read.py` — `read_device_frames` bidirectional lookup

**Role:** service | **Data Flow:** CRUD

**Analog:** `read.py` lines 296-385 (exact same function — change only the `raw_frames` query section)

**Existing `raw_frames` query** (lines 357-381):
```python
remaining = max(0, limit - len(out))
db_rows = conn.execute(
    """SELECT extract(epoch FROM captured_at)::float AS captured_at_unix,
              frame_hex, source, device_model, device_type, sensitivity
       FROM raw_frames
       WHERE device_id = %s
         AND extract(epoch FROM captured_at) >= %s
         AND extract(epoch FROM captured_at) <= %s
       ORDER BY captured_at
       LIMIT %s""",
    (device_id, from_ts, to_ts, remaining),
).fetchall()
```

**What to change:** Add helper at module top:
```python
import uuid as _uuid

def _is_uuid(s: str) -> bool:
    try:
        _uuid.UUID(s)
        return True
    except ValueError:
        return False
```

Then replace the `WHERE device_id = %s` clause with bidirectional lookup. The `raw_batches` (archive) path uses `devices.device_id` as PK which is already the CoreBluetooth UUID — no change needed there. Only the `raw_frames` query changes:
```python
is_uuid = _is_uuid(device_id)
if is_uuid:
    device_clause = "device_uuid = %s"
else:
    device_clause = "device_model = %s"

db_rows = conn.execute(
    f"""SELECT extract(epoch FROM captured_at)::float AS captured_at_unix,
               frame_hex, source, device_model, device_type, sensitivity
        FROM raw_frames
        WHERE {device_clause}
          AND extract(epoch FROM captured_at) >= %s
          AND extract(epoch FROM captured_at) <= %s
        ORDER BY captured_at
        LIMIT %s""",
    (device_id, from_ts, to_ts, remaining),
).fetchall()
```

**Security:** `device_id` is never interpolated — always passed as a `%s` parameter. Both branches are parameterised.

---

### `server/ingest/app/main.py` — `export_device_frames` route

**Role:** controller | **Data Flow:** request-response

**Analog:** `main.py` lines 469-481 (exact same function — no change to route itself; change is in `read.read_device_frames` called at line 480)

**Existing route** (lines 469-481):
```python
@app.get("/v1/export/frames/{device_id}", dependencies=[Depends(require_auth)])
def export_device_frames(
    device_id: str,
    from_: float = Query(0.0, alias="from", ge=0.0),
    to: float = Query(9_999_999_999.0, alias="to"),
    limit: int = Query(5000, ge=1, le=5000),
):
    with psycopg.connect(cfg.db_dsn) as conn:
        frames = read.read_device_frames(conn, device_id, from_ts=from_, to_ts=to, limit=limit)
    return {"device_id": device_id, "frames": frames, "count": len(frames)}
```

No change needed in `main.py` — the bidirectional logic lives entirely in `read.read_device_frames`. The route signature and response shape are unchanged.

---

## Shared Patterns

### NSLock-guarded property (Swift queue thread safety)
**Source:** `GooseSwift/CaptureFrameWriteQueue.swift` lines 196-200
**Apply to:** `currentDeviceUUID` property on `CaptureFrameWriteQueue`
```swift
private var _activeDeviceID: String?
var activeDeviceID: String? {
    get { stateLock.withLock { _activeDeviceID } }
    set { stateLock.withLock { _activeDeviceID = newValue } }
}
```

### `NSNull()` fallback in `bridgeObject` dict
**Source:** `GooseSwift/CaptureFrameWriteQueue.swift` lines 62-74
**Apply to:** `deviceUUID` key in `CapturedFrameWriteRow.bridgeObject`
```swift
"capture_session_id": captureSessionID ?? NSNull(),
```

### `#[serde(default)]` for new nullable Rust fields
**Source:** `Rust/core/src/store.rs` lines 185-186, `Rust/core/src/capture_import.rs` lines 73-75
**Apply to:** `device_uuid: Option<String>` on `RawEvidenceRow`, `CapturedFrameInput`
```rust
#[serde(default)]
pub capture_session_id: Option<String>,
```

### `ensure_*_columns()` additive migration
**Source:** `Rust/core/src/store.rs` lines 6861-6890
**Apply to:** `ensure_raw_evidence_columns()` and `ensure_decoded_frame_columns()`
```rust
let columns = self.table_columns_unchecked("raw_evidence")?;
for (column, ddl) in [("capture_session_id", "capture_session_id TEXT ...")] {
    if !columns.contains(column) {
        self.conn.execute(&format!("ALTER TABLE raw_evidence ADD COLUMN {ddl}"), [])?;
    }
}
```

### UserDefaults JSON dict persistence
**Source:** `GooseSwift/GooseUploadService.swift` lines 168 and throughout codebase
**Apply to:** `goose.swift.device_uuid_map` read/write in `GooseAppModel+Lifecycle.swift`
```swift
// Write:
let data = try? JSONSerialization.data(withJSONObject: map)
UserDefaults.standard.set(data, forKey: key)
// Read:
let map = UserDefaults.standard.data(forKey: key)
    .flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: String] } ?? [:]
```

### Parameterised psycopg SQL
**Source:** `server/ingest/app/read.py` lines 358-372
**Apply to:** bidirectional `WHERE` clause in `read_device_frames`
```python
conn.execute("SELECT ... WHERE device_id = %s ...", (device_id, from_ts, to_ts, remaining))
```

---

## No Analog Found

All files have exact or near-exact analogs in the codebase. No file requires patterns from RESEARCH.md alone.

---

## Critical Pitfalls (from RESEARCH.md — planner must propagate)

| Pitfall | Risk | Mitigation |
|---------|------|------------|
| Index column name: `raw_evidence` uses `captured_at` not `ts` | SQLite runtime error | Use `ON raw_evidence(device_uuid, captured_at)` |
| `CapturedFrameInput` in `capture_import.rs` must also gain `device_uuid` | UUID never reaches SQLite | Add `#[serde(default)] pub device_uuid: Option<String>` to `CapturedFrameInput` |
| Capture UUID at enqueue time, not in `bridgeObject` | Race on reconnect | Store `let deviceUUID: String?` in `CapturedFrameWriteRow` struct, not computed in `bridgeObject` closure |
| Bridge response must include `device_uuid` | Upload payload missing UUID | Add `"device_uuid": r.device_uuid` to `json!({})` in `upload_get_raw_frames_for_upload_bridge` |
| No `PRAGMA user_version` bump | Schema tracking confusion | `ensure_*_columns()` pattern does NOT bump user_version — do not add it |

---

## Metadata

**Analog search scope:** `Rust/core/src/`, `GooseSwift/`, `server/`
**Files scanned:** 11
**Pattern extraction date:** 2026-06-10
