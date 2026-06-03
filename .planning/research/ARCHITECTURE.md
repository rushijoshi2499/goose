# Architecture Patterns — Goose Server Integration

**Domain:** Brownfield iOS app + self-hosted biometric server
**Researched:** 2026-06-03
**Confidence:** HIGH — based on direct codebase inspection

---

## Recommended Architecture

### System Overview (after this milestone)

```
WHOOP Device (BLE)
      │ CoreBluetooth notifications
      ▼
GooseBLEClient
      │ onNotification callback
      ▼
GooseAppModel (@MainActor coordinator)
      │ notificationIngestQueue
      ▼
NotificationFrameParser → GooseRustBridge → protocol.parse_notification_frame_batch
      │
      ▼ parsed frames
CaptureFrameWriteQueue (com.goose.swift.capture-frame-writes)
      │ capture.import_frame_batch → goose.sqlite
      │
      │ on write completion → handleCaptureFrameWriteResult(_:)
      ▼
[NEW] GooseRemoteUploadService
      │ enqueue(decoded rows)
      │ uploadQueue (com.goose.swift.remote-upload, serial, qos: .utility)
      ▼
URLSession.shared.dataTask
      │ POST /v1/ingest-decoded
      │ Bearer token
      ▼
server/ (FastAPI + TimescaleDB, Docker)
      │ store.upsert_streams
      ▼
TimescaleDB (hr_samples, rr_intervals, events, battery, spo2, …)
```

---

## Component Boundaries

### New iOS Component: GooseRemoteUploadService

| Property | Decision |
|----------|----------|
| File | `GooseSwift/GooseRemoteUploadService.swift` |
| Type | `final class`, NOT `@MainActor`, NOT `ObservableObject` |
| Owned by | `GooseAppModel` (one instance, like `captureFrameWriteQueue`) |
| Thread model | Internal serial `DispatchQueue` (`com.goose.swift.remote-upload`, qos: `.utility`) |
| Config source | Reads `serverURL` and `apiKey` from `UserDefaults` at call time (no live binding needed) |
| State published | Delegates a `@escaping @MainActor` completion closure back — same pattern as `CaptureFrameWriteQueue.enqueue` |

The service has one public method:

```swift
func enqueue(batch: GooseRemoteUploadBatch)
```

Where `GooseRemoteUploadBatch` carries:

```swift
struct GooseRemoteUploadBatch {
    let deviceID: String          // ble.activeDeviceIdentifier?.uuidString ?? "unknown"
    let deviceName: String?       // ble.modelNumber
    let streams: GooseDecodedStreams
}

struct GooseDecodedStreams {
    var hr: [[String: Any]]
    var rr: [[String: Any]]
    var events: [[String: Any]]
    var battery: [[String: Any]]
    var spo2: [[String: Any]]
    var skin_temp: [[String: Any]]
    var resp: [[String: Any]]
    var gravity: [[String: Any]]
}
```

This shape maps directly to the server's `DecodedBatch` / `DecodedStreams` Pydantic models.

### New iOS Component: GooseRemoteUploadStore (settings state)

| Property | Decision |
|----------|----------|
| File | `GooseSwift/GooseRemoteUploadStore.swift` |
| Type | `@MainActor final class ObservableObject` — same pattern as `MoreDataStore` |
| Owned by | `MoreView` (`@StateObject`) |
| Persistence | `@AppStorage` or `UserDefaults` for `serverURL` and `apiKey` |
| Published state | `serverURL: String`, `apiKey: String`, `uploadEnabled: Bool`, `lastUploadStatus: String`, `pendingBatchCount: Int` |

### New Swift View: MoreRemoteServerView

| Property | Decision |
|----------|----------|
| File | `GooseSwift/MoreRemoteServerViews.swift` |
| Route | Add `case remoteServer` to `MoreRoute` enum |
| Section | Add to `settingsRoutes: [MoreRoute]` (currently only `.privacy`) |
| Fields | Server URL text field, API key secure field, upload enabled toggle, last upload status label, pending count label |

### server/ Directory (Python)

No new architecture — copy from `my-whoop/server/` verbatim. The `server/` directory at the repo root is self-contained. Its internal structure is already well-defined:

```
server/
  ingest/
    app/           # FastAPI application
    tests/
    Dockerfile
    requirements.txt
    requirements-dev.txt
  db/
    init.sql
  packages/
    whoop-protocol/   # Python BLE frame parser (not needed for /v1/ingest-decoded path)
  docker-compose.yml
```

For the Goose fork, only the `ingest/` service and `docker-compose.yml` are load-bearing for this milestone. The `dashboard/` and `packages/whoop-protocol/` come along for free with the copy.

---

## Data Flow: BLE Capture → SQLite → Upload Queue → Server

### Step-by-step

1. **BLE notification arrives** — `GooseBLEClient` fires `onNotification` callback.

2. **Parse** — `GooseAppModel` queues bytes on `notificationIngestQueue`; Rust bridge method `protocol.parse_notification_frame_batch` reassembles and decodes frames. Result: a `NotificationIngestResult` containing `dataSignalSamples`, which carry HR, RR, SpO2, etc. as `WhoopDataSignalSample` values.

3. **SQLite write (existing path)** — `handleNotificationIngestResult` builds `CapturedFrameWriteRow` structs and calls `captureFrameWriteQueue.enqueue(rows:completion:)`. The queue batches these and calls Rust's `capture.import_frame_batch` → writes to `goose.sqlite`. On completion, `handleCaptureFrameWriteResult(_:)` is called on `@MainActor`.

4. **Upload trigger (new path)** — `handleCaptureFrameWriteResult(_:)` is the correct hook. After confirming `result.pass` and that at least one row was inserted (`result.inserted > 0`), call:

   ```swift
   remoteUploadService.enqueue(batch: GooseRemoteUploadBatch(
       deviceID: ble.activeDeviceIdentifier?.uuidString ?? "unknown",
       deviceName: ble.modelNumber,
       streams: extractDecodedStreams(from: result, pipeline: lastIngestResult)
   ))
   ```

   **Important:** The decoded stream values (HR bpm, RR intervals, etc.) are available on `NotificationIngestResult` / `WhoopDataSignalSample` at step 2, not on `CaptureFrameWriteResult`. The upload batch must be assembled from the ingest result and passed through, or stored transiently until the write confirms success.

   **Preferred approach:** In `handleNotificationIngestResult`, if the write succeeds, build the upload payload at the same time as the frame rows and pass it alongside the completion closure. This avoids re-querying SQLite.

5. **Upload execution** — `GooseRemoteUploadService.uploadQueue` drains batches serially. Each batch is serialised to JSON and sent via `URLSession.shared.dataTask(with:completionHandler:)` as a `POST /v1/ingest-decoded` with `Authorization: Bearer <token>`. The service merges consecutive pending batches when possible (same device, same session window) to reduce request count.

6. **Retry** — On network error (non-2xx or no connectivity), the service re-enqueues the batch with exponential backoff (2s, 4s, 8s, cap 60s). Maximum retry count: 5 per batch. After 5 failures the batch is dropped and the status is published.

7. **Status feedback** — The service publishes status via a `@escaping @MainActor` callback stored in `GooseAppModel`. `GooseAppModel` forwards it to a `@Published var remoteUploadStatus: String` that `MoreRemoteServerView` observes.

### Upload trigger policy

| Trigger | Decision |
|---------|----------|
| After each SQLite write batch (automatic) | YES — triggered from `handleCaptureFrameWriteResult` when upload is enabled and rows were inserted |
| Manual "Upload now" button | YES — `GooseRemoteUploadStore` exposes `triggerManualFlush()` that calls the service |
| Background / app suspended | NO — URLSession background transfer is overkill for this use case; foreground-only is sufficient since BLE capture already requires foreground |
| Periodic timer | NO — event-driven after write is sufficient; no polling needed |

---

## Settings Persistence

Store `serverURL` and `apiKey` in `UserDefaults` via `@AppStorage`. The service reads these at call time — no binding needed.

```swift
// UserDefaults keys (use a dedicated constants file or GooseRemoteUploadConstants.swift)
static let serverURLKey = "goose.remote.serverURL"
static let apiKeyKey = "goose.remote.apiKey"
static let uploadEnabledKey = "goose.remote.uploadEnabled"
```

Do NOT store the API key in `UserDefaults` in production if this ever becomes a shared app. For a personal self-hosted tool, `UserDefaults` is acceptable. The project constraints explicitly say "Bearer token simples é suficiente" — no Keychain needed for this milestone.

---

## Patterns to Follow

### Pattern 1: CaptureFrameWriteQueue (reference implementation)

The upload service replicates the exact structure of `CaptureFrameWriteQueue`:

- Private `writeQueue = DispatchQueue(label:, qos: .utility)` — serial
- `NSLock` for shared state (`pendingBatches`, `isUploading`, counters)
- Public `enqueue` returns immediately (never blocks caller)
- Internal `flushNext()` loop drains the queue
- Results dispatched back via `DispatchQueue.main.async { completion(result) }`

This is the established pattern. Do not invent a new one.

### Pattern 2: GooseAppModel extension file

Add upload wiring as `GooseAppModel+RemoteUpload.swift`:

- Holds `let remoteUploadService = GooseRemoteUploadService()`
- Exposes `@Published var remoteUploadStatus: String`
- Contains `scheduleRemoteUpload(for result: CaptureFrameWriteResult, ingestResult: NotificationIngestResult)`
- Contains `handleRemoteUploadResult(_ result: GooseRemoteUploadResult)`

This keeps the main `GooseAppModel.swift` file clean. It follows the existing `GooseAppModel+NotificationPipeline.swift`, `GooseAppModel+ActivityRecording.swift` pattern.

### Pattern 3: MoreDataStore pattern for settings

`GooseRemoteUploadStore` mirrors `MoreDataStore` structure:

- `@MainActor final class GooseRemoteUploadStore: ObservableObject`
- `@AppStorage` for persistent fields
- `@Published` for transient status strings
- Owned as `@StateObject` in `MoreRemoteServerView`
- Status computed from `GooseAppModel.remoteUploadStatus` passed in

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Uploading from @MainActor inline

Calling `URLSession.dataTask` or constructing the JSON payload from `@MainActor` blocks the main thread during serialisation. Always dispatch the payload construction and network call to `GooseRemoteUploadService`'s internal queue.

### Anti-Pattern 2: Re-querying SQLite for upload data

Do not query `goose.sqlite` to build the upload payload. The decoded values are already present in `NotificationIngestResult` at parse time. Re-querying adds latency, complexity, and a dependency on the SQLite schema for the upload path.

### Anti-Pattern 3: Background URLSession with complex delegate

A background `URLSession` requires a session delegate, background task completion handler wiring in `AppDelegate`, and a distinct bundle identifier for the session. For a personal tool that runs BLE capture in foreground, this is unnecessary complexity. Use `URLSession.shared` with `dataTask(with:completionHandler:)`.

### Anti-Pattern 4: Making GooseRemoteUploadService a singleton

The existing codebase has one singleton (`HeartRateSeriesStore.shared`) and treats it as a known anti-pattern. All other components are instance-owned. Follow this: own the service on `GooseAppModel`.

### Anti-Pattern 5: Triggering upload before SQLite write confirms

Enqueue upload only after `CaptureFrameWriteResult.pass == true && result.inserted > 0`. Uploading frames that failed to persist to SQLite breaks the "local store is source of truth" invariant.

---

## server/ Repo Structure

### Where to put server/ in the repo

Place at the root of the Goose repo alongside `GooseSwift/`, `Rust/`, `Scripts/`:

```
goose/
  GooseSwift/           # iOS app (existing)
  Rust/                 # Rust core (existing)
  Scripts/              # Build scripts (existing)
  GooseWorkoutLiveActivityExtension/  # Widget (existing)
  server/               # NEW — copied from my-whoop/server/
    ingest/
    db/
    packages/
    docker-compose.yml
    README.md
```

### CI isolation

The `server/` directory should have its own CI pipeline, separate from the iOS Xcode build. In GitHub Actions terms:

- iOS CI: triggered on changes to `GooseSwift/**`, `Rust/**`, `Scripts/**`
- Server CI: triggered on changes to `server/**`

This prevents a Python test failure from blocking iOS builds and vice versa.

### .gitignore additions needed

```
server/.env
server/__pycache__/
server/**/*.pyc
server/.venv/
```

### docker-compose.yml placement

Keep `docker-compose.yml` inside `server/` (not at repo root). The build context for the ingest Dockerfile is already set to the `server/` directory (`context: .` in `docker-compose.yml`). This works correctly when `docker compose up` is run from `server/`.

---

## Build Order

This is the correct sequence to minimise rework. Each step can be tested independently.

### Step 1: Copy server/ into repo

Copy `my-whoop/server/` to `goose/server/`. Verify `docker compose up` works. Run existing server tests (`pytest ingest/tests/`). This step has zero iOS dependencies and zero risk to the existing iOS app.

Deliverable: `server/` committed, Docker stack boots, `/healthz` returns 200.

### Step 2: Verify /v1/ingest-decoded manually

Send a hand-crafted `POST /v1/ingest-decoded` with `curl` using a Bearer token. Confirm the server accepts it and TimescaleDB has rows. This validates the contract before writing any iOS code.

Deliverable: `curl` test documented in `server/VERIFY.md` (or appended to existing one).

### Step 3: iOS settings UI (MoreRemoteServerView)

Add `GooseRemoteUploadStore`, `MoreRemoteServerView`, and the new `MoreRoute.remoteServer` case. Wire into `settingsRoutes`. At this point there is no upload logic — the view just shows a "not configured" status. This step has no network calls and is safe to ship.

Deliverable: Settings tab shows "Remote Server" entry; user can enter URL and token; values persist across restarts.

### Step 4: GooseRemoteUploadService (network layer)

Implement the service with the queue, JSON serialisation, URLSession call, and retry logic. Add `GooseAppModel+RemoteUpload.swift`. Wire the trigger into `handleCaptureFrameWriteResult`. Test with a live WHOOP connection against the local Docker stack.

Deliverable: Data captured via BLE appears in TimescaleDB within seconds of capture.

### Step 5: Upload status feedback

Surface `remoteUploadStatus` and `pendingBatchCount` in `MoreRemoteServerView`. Add `GooseAppModel.remoteUploadStatus` `@Published` property. This is cosmetic polish — move it earlier if debugging Step 4 is difficult without visibility.

**Rationale for this order:** Server must be running before iOS upload code can be tested end-to-end. Settings UI before service means the URL/token plumbing exists before the service tries to read it. Service before status means there is something real to display.

---

## Scalability Considerations

This is a personal self-hosted server for one user. Scale is not a design concern. However, two operational properties matter:

| Property | How addressed |
|----------|---------------|
| Idempotent uploads | `store.upsert_streams` uses `ON CONFLICT DO UPDATE` / `DO NOTHING`. Re-uploading the same batch is safe. |
| No data loss on retry | Batches are retried in-memory; if the app is killed mid-retry, at most one batch of decoded rows (a few seconds of data) is lost. The raw frames are already in SQLite, so historical sync remains possible. |

---

## Sources

- Direct code inspection: `GooseSwift/CaptureFrameWriteQueue.swift` (lines 180–463)
- Direct code inspection: `GooseSwift/GooseAppModel+NotificationPipeline.swift` (lines 180–295)
- Direct code inspection: `GooseSwift/MoreDataStore.swift` (full file)
- Direct code inspection: `GooseSwift/MoreRouteModels.swift` (full file)
- Direct code inspection: `my-whoop/server/ingest/app/main.py` (POST /v1/ingest-decoded, DecodedBatch schema)
- Direct code inspection: `my-whoop/server/ingest/app/store.py` (upsert_streams, ON CONFLICT semantics)
- Direct code inspection: `my-whoop/server/docker-compose.yml` (service structure, port config)
- `.planning/codebase/ARCHITECTURE.md` — threading model, GooseAppModel extension pattern
- `.planning/codebase/STACK.md` — no third-party Swift deps; URLSession is the correct choice
