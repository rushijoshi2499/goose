---
phase: 84-gen4-battery
reviewed: 2026-06-14T00:00:00Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - Rust/core/src/bridge.rs
  - Rust/core/src/protocol.rs
  - GooseSwift/GooseBLEClient.swift
  - GooseSwift/GooseBLEClient+BatteryCommands.swift
  - GooseSwift/GooseBLEClient+Commands.swift
  - GooseSwift/GooseBLEClient+PeripheralDelegate.swift
  - GooseSwift/NotificationFrameParsing.swift
  - GooseSwift/GooseAppModel+NotificationPipeline.swift
findings:
  critical: 1
  warning: 2
  info: 3
  total: 6
status: issues_found
---

# Phase 84: Code Review Report

**Reviewed:** 2026-06-14T00:00:00Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

Phase 84 adds Gen4 battery reading from two wire sources: Event-48 (type 48 BLE notifications) and Cmd 26 (GET_BATTERY_LEVEL command-response). The implementation adds two Rust bridge methods, wires a Cmd 26 auto-send on Gen4 connection, and adds an Event-48 extraction branch in the notification pipeline.

The Event-48 path (BAT-01) is structurally correct — offset 17 maps to data-body offset 5 as documented, the sanity guard and Swift secondary guard cooperate correctly, and the wireProtocol == .gen4 gate prevents false-positive application on Gen5 events. This path will work when deployed.

The Cmd 26 path (BAT-02) contains a blocker: the Rust parser reads the wrong bytes from the payload it receives. The Swift caller passes the full Gen4 COMMAND_RESPONSE payload (packet_type at [0], sequence at [1], cmd26 at [2], origin_sequence at [3], result_code at [4], data body at [5+]), but `parse_cmd26_battery` reads the battery raw value from `payload[2..4]` — which contains the command identifier byte (26) and the origin sequence byte, not the actual battery value. This means `raw = 26 | (origin_seq << 8)` will exceed the 1100 sanity guard for virtually all real WHOOP responses (origin_seq ≥ 5 → raw ≥ 1306 > 1100), causing every call to return an error that is logged as `cmd26.parse.failed` and silently discarded. Gen4 battery reading via cmd26 is non-functional.

Two warnings round out the findings: the overnight storage classifier hard-codes the Gen5 8-byte header offset when detecting packet type (misreads Gen4 frames), and the event48_battery_pct compact field fires for every Event-48 packet regardless of event_id (including BOOT, CHARGING_ON, and all other event types, not only BATTERY_LEVEL = 3).

---

## Critical Issues

### CR-01: `parse_cmd26_battery` reads command-identifier and origin-sequence bytes instead of the actual battery data

**File:** `Rust/core/src/bridge.rs:439-448`

**Issue:** The function receives the full Gen4 COMMAND_RESPONSE payload extracted by `gen4Payload()` (Swift: `GooseBLEClient+Parsing.swift:1001-1012`), which strips only the 4-byte frame header and 4-byte trailing CRC. The resulting byte slice has the layout:

```
[0] = 36   (COMMAND_RESPONSE packet type)
[1] = seq  (BLE sequence counter)
[2] = 26   (command identifier — the command being responded to)
[3] = origin_seq  (origin sequence from the sent command)
[4] = 1    (result code = SUCCESS)
[5+] = data body (where the actual battery raw value resides)
```

The implementation reads `raw = u16::from(payload[2]) | u16::from(payload[3]) << 8`, which gives `raw = 26 | (origin_seq * 256)`. The Cmd 26 sequence counter in Swift starts at 48 (`nextCmd26BatteryCommandSequence`), so `origin_seq` will typically be in the range 48–255. For `origin_seq = 48`: `raw = 26 + 12288 = 12314 > 1100` — the sanity guard fires and returns an error. `handleCmd26BatteryResponse` catches this, logs `cmd26.parse.failed`, and discards the result. Gen4 battery via cmd26 is non-functional.

The Rust inline comment and the planning document describe the intended layout as `[0]=command_number(26), [1]=sequence, [2-3]=battery_raw`. This layout would hold if the function received only the data body starting at `payload[2]` of the extracted Gen4 payload (i.e., from the command identifier byte onward), rather than the full extracted payload.

**Fix:** Either (a) adjust `parse_cmd26_battery` to read the battery raw from the data body, or (b) have the Swift caller trim the payload before passing it. The data body starts at `payload[5]`; a minimal fix in Rust would be:

```rust
// Option A — read from data body (payload[5..7]) with updated guard
fn parse_cmd26_battery(payload: &[u8]) -> GooseResult<u16> {
    if payload.len() < 7 {
        return Err(GooseError::message(format!(
            "cmd26 payload too short for data body: {} < 7",
            payload.len()
        )));
    }
    // payload[5..7]: data body, battery raw u16 LE (BAT-02)
    let raw = u16::from(payload[5]) | u16::from(payload[6]) << 8;
    if raw > 1000 {
        return Err(GooseError::message(format!(
            "cmd26 battery raw={raw} exceeds sanity guard 1000"
        )));
    }
    Ok(raw / 10)
}
```

Or alternatively (Option B) in Swift, trim to the data body before passing:

```swift
// In handleCmd26BatteryResponse, pass only the data body:
let dataPortion = Array(payload.dropFirst(5))   // skip type, seq, cmd, orig_seq, result_code
let payloadHex = Data(dataPortion).hexString
```

In either case the unit tests in `battery_parse_tests` must be updated to use a payload matching the real wire format (not the synthetic `cmd26_payload(5, 850)` test helper which places raw at `[2..4]`).

---

## Warnings

### WR-01: `OvernightRawNotificationStorageClassifier.classify()` always reads packet type from byte 8, breaking Gen4 frames

**File:** `GooseSwift/NotificationFrameParsing.swift:191-212`

**Issue:** The classifier reads `packetType = headerBytes[8]` unconditionally, which is correct for Gen5 (8-byte header) but wrong for Gen4 (4-byte header). For a Gen4 notification, the actual packet type byte is at `headerBytes[4]`, not `[8]`. Reading `[8]` gives a byte from the middle of the Gen4 payload — likely a BLE sequence counter or part of the event data — causing incorrect packet type classification. This affects `compactKey` generation and the compact-live-flood sampling policy applied to Gen4 overnight notifications: valid Gen4 data packets could be misclassified or correctly structured entries missed.

The `GooseNotificationEvent` passed to `classify()` already exposes `wireProtocol` (computed from `characteristicUUID`), so the fix is straightforward.

**Fix:**
```swift
static func classify(_ event: GooseNotificationEvent) -> Classification {
    let headerBytes = Array(event.value.prefix(10))
    let headerLen = event.wireProtocol == .gen4 ? 4 : 8
    guard headerBytes.count >= headerLen + 1, headerBytes[0] == 0xaa else {
        return Classification(packetType: nil, packetK: nil, compactKey: nil)
    }

    let packetType = headerBytes[headerLen]           // byte immediately after header
    let packetK = headerBytes.count > headerLen + 1 ? headerBytes[headerLen + 1] : nil
    // ... rest of function unchanged
```

---

### WR-02: `event48_battery_pct` is extracted from every Event-48 frame regardless of event_id, not only BATTERY_LEVEL (id=3)

**File:** `Rust/core/src/bridge.rs:3258-3260`

**Issue:** In `compact_parsed_frame_summary`, the Event branch computes `event48_battery_pct` for every `ParsedPayload::Event` frame by decoding `data_hex` and reading a u16 at data-body offset 5. However, only Event-48 frames with `event_id == 3` (BATTERY_LEVEL, per `strap_event_name` in `protocol.rs:1042`) carry a battery percentage at that offset. Other Event-48 types — BOOT (15), CHARGING_ON (7), CHARGING_OFF (8), BLE_CONNECTION_UP (11), etc. — may have arbitrary data at offset 5 that could decode to a plausible battery percentage. The Swift guard at `GooseAppModel+NotificationPipeline.swift:666-670` filters on `batteryViaEvent48 == true && wireProtocol == .gen4`, which prevents applying the incorrect value, but it does not prevent the compact summary from advertising a non-None `event48_battery_pct` for non-battery events. Any downstream consumer of the compact summary field would receive a spurious value.

**Fix:** Add an `event_id == Some(3)` check before computing the battery value:

```rust
let event48_battery_pct: Option<u16> = if event_id == Some(3) {
    hex::decode(data_hex)
        .ok()
        .and_then(|data| parse_event48_battery_from_data(&data))
} else {
    None
};
```

---

## Info

### IN-01: `capturedAt` in cmd26 battery path uses bridge-call completion time, not BLE notification arrival time

**File:** `GooseSwift/GooseBLEClient+BatteryCommands.swift:67-76`

**Issue:** `handleCmd26BatteryResponse` dispatches the bridge call to a background queue. Inside the async closure, `capturedAt: Date()` is evaluated after the bridge call returns — meaning the timestamp passed to `applyBatteryLevel` reflects the parse completion time, not the time the BLE notification arrived. The BLE arrival timestamp is available via the `capturedAt` parameter of the CoreBluetooth delegate (captured at the start of `didUpdateValueFor`).

**Fix:** Capture the arrival time before dispatching and use it in the closure:

```swift
func handleCmd26BatteryResponse(_ payload: [UInt8]) {
    // ... guards ...
    let arrivalTime = Date()                  // capture before async dispatch
    let payloadHex = Data(payload).hexString
    DispatchQueue.global(qos: .utility).async { [weak self] in
        // ...
        self.applyBatteryLevel(pct, capturedAt: arrivalTime, sourceTitle: "cmd26.battery")
```

---

### IN-02: `parse_event48_battery` accepts `raw == 1100` (→ pct = 110%) through the Rust guard

**File:** `Rust/core/src/bridge.rs:406-411`

**Issue:** The guard is `if raw > 1100 { return Err(...) }`, so `raw = 1100` is accepted and returns `1100 / 10 = 110`. The Swift secondary guard `batteryPct <= 100` in the notification pipeline catches this and rejects it, so it does not produce an incorrect reading in production. However, the Rust-layer test (`event48_boundary_accept_1100`) explicitly asserts that 110% is a valid return value from the Rust function — this could mislead a future reader into thinking 110% is a legitimate output. Tightening the Rust guard to `raw > 1000` would reject values that cannot correspond to a real battery percentage.

**Note:** This is a minor design inconsistency. The Swift double-guard prevents any end-user impact.

---

### IN-03: Unit test helper `cmd26_payload` produces a payload layout inconsistent with the real V5/Gen4 wire format

**File:** `Rust/core/src/bridge.rs:11036-11045`

**Issue:** The `cmd26_payload(len, raw)` test helper writes `raw` at bytes `[2..4]` of a zero-filled buffer. This tests the internal byte-reading logic of `parse_cmd26_battery` in isolation, but the synthetic payload has `payload[0]=0` and `payload[2..4]=battery_raw`, which does not match the real Gen4 COMMAND_RESPONSE payload structure (`payload[0]=36`, `payload[2]=26`, `payload[3]=origin_seq`, `payload[5+]=battery_raw`). As a result the unit tests pass even though the function reads the wrong bytes from a real device response (CR-01). A round-trip test that hex-encodes a real captured COMMAND_RESPONSE frame would have caught this.

**Fix:** Replace or augment `cmd26_payload` with a builder that follows the actual COMMAND_RESPONSE layout:

```rust
fn real_cmd26_response_payload(origin_seq: u8, result_code: u8, battery_raw: u16) -> Vec<u8> {
    let mut v = vec![
        36u8,           // COMMAND_RESPONSE packet type
        origin_seq + 1, // response sequence
        26,             // command identifier (cmd26)
        origin_seq,     // origin_sequence from sent command
        result_code,    // 1 = SUCCESS
        (battery_raw & 0xff) as u8,
        (battery_raw >> 8) as u8,
    ];
    v.resize(v.len() + 3, 0); // padding
    v
}
```

---

_Reviewed: 2026-06-14T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
