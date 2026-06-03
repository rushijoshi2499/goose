# Project Research Summary

**Project:** Goose — Servidor Remoto + Contribuições Upstream (tigercraft4/goose)
**Domain:** Brownfield iOS BLE biometrics app + self-hosted FastAPI/TimescaleDB server + upstream fork management
**Researched:** 2026-06-03
**Confidence:** HIGH

## Executive Summary

This is a brownfield integration milestone with three parallel workstreams: (1) copying an already-working FastAPI+TimescaleDB server into the Goose repo and packaging it with Docker, (2) building an iOS upload client in GooseSwift that sends decoded WHOOP BLE data to that server, and (3) reviewing and integrating 9 open upstream PRs from `b-nnett/goose`. All three workstreams have well-defined starting points — the server exists at `my-whoop/server/`, the iOS upload patterns exist in `OpenAICoachResponsesClient.swift` and `CaptureFrameWriteQueue.swift`, and the upstream PRs are enumerated and risk-categorised. No greenfield architecture decisions are required; every component follows an established pattern already in the codebase.

The recommended approach is sequential: get the server running in the Goose repo first (zero risk to iOS code), then add the iOS settings UI for server configuration, then wire up the upload service, then surface status feedback, and finally handle upstream PR integration as a parallel track. The server upload endpoint is idempotent (upsert, not insert), which eliminates any exactly-once delivery complexity on the iOS side — retry freely. The only constraint with real implementation risk is iOS App Transport Security: HTTP to raw IP addresses is blocked by ATS without a global `NSAllowsArbitraryLoads` exception. The correct mitigation is to require a resolvable hostname for the server and use a scoped `NSExceptionDomains` exception.

The largest risk surface is upstream PR integration: 9 PRs of varying scope, some overlapping (PR #6 and #10 both touch Rust CI; PR #12 and #13 both touch FFI threading). Merging rather than cherry-picking, and keeping fork-specific infrastructure (`server/`, Docker, iOS upload client) on a separate branch from upstream-quality fixes, is essential to avoid divergence.

## Recommended Stack

No new dependencies introduced. All technologies follow existing patterns.

| Layer | Technology | Notes |
|-------|-----------|-------|
| iOS HTTP | `URLSession.shared` (`async/await`) | Pattern from `OpenAICoachResponsesClient.swift` |
| iOS serialisation | `Codable` + `JSONEncoder` + explicit `CodingKeys` | snake_case `device_generation` |
| iOS credential storage | Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) | Pattern from `OnboardingPersistence.swift` |
| iOS settings | `UserDefaults` / `@AppStorage` | Server URL, upload-enabled flag |
| Server runtime | FastAPI + Uvicorn | Already running in my-whoop |
| Server DB | TimescaleDB 2.17.2-pg16 | Existing schema; stable idempotent migrations |
| Container | Multi-stage Dockerfile (`python:3.11-slim`) | Stage 1: compile wheels; Stage 2: runtime |
| Orchestration | Docker Compose v2 | Single-user self-hosted; `docker compose up --build` |

## Table Stakes Features (Must Have)

- Server URL + Bearer token configurable by user — absolute prerequisite for any upload
- `GooseUploadClient` POST to `/v1/ingest-decoded` with Bearer token
- Retry on network failure (3 attempts, exponential backoff 1s/2s/4s)
- Upload triggered automatically after each successful SQLite write batch (`handleCaptureFrameWriteResult`)
- Upload status feedback in More tab (last upload time, pending count)
- Correct `device_id` mapping from `ble.activeDeviceIdentifier?.uuidString`
- Server health check (`GET /healthz`) on app launch
- Correct `device_generation` in payload (`"4.0"` for GEN4, `"5.0"` default)

## Anti-Features (Deliberately NOT Building)

- Background `URLSessionConfiguration` with delegate — BLE capture already requires foreground
- Persistent upload queue in SQLite — in-memory retry sufficient for personal use
- Dashboard / data visualisation — out of scope per PROJECT.md
- Sync with cursor/watermark — idempotent upsert makes this unnecessary

## Architecture Decisions

1. **Trigger point:** `handleCaptureFrameWriteResult(_:)` in `GooseAppModel` — after each confirmed SQLite batch
2. **Upload service pattern:** Mirror `CaptureFrameWriteQueue` exactly (serial DispatchQueue, NSLock, non-blocking enqueue)
3. **New components:**
   - `GooseRemoteUploadService` — upload queue, URLSession POST, retry
   - `GooseRemoteUploadStore` — `@MainActor ObservableObject`, `@AppStorage` config, `@Published` status
   - `MoreRemoteServerView` — SwiftUI settings screen
   - `GooseAppModel+RemoteUpload.swift` — wiring extension
   - `server/` — verbatim copy of `my-whoop/server/` with multi-stage Dockerfile

## Top 5 Pitfalls

1. **ATS blocks HTTP to raw IP addresses** — `NSExceptionDomains` does not accept IP keys. Require resolvable hostname (e.g. `whoop.local` via mDNS). Prevention: document in `server/README.md` and validate in settings UI.

2. **URLSession background session silently discards completion handlers** — no compile-time warning. Prevention: always use `URLSession.shared`; wrap in `UIBackgroundTaskIdentifier` if needed.

3. **`.env` committed with real credentials** — git history is permanent. Prevention: add `server/.env` to `.gitignore` in the same commit that adds `server/`; commit only `.env.example`.

4. **Cherry-picking upstream PRs creates duplicate-SHA conflicts** — when upstream merges those PRs, git applies identical changes twice. Prevention: add `upstream` remote, fetch PRs as branches, use `git merge --no-ff`.

5. **Fork-specific infrastructure mixed with upstream-quality commits** — makes clean upstream PRs impossible. Prevention: keep `server/`, upload client commits on fork-specific branches; upstream fixes on isolated `upstream/fix-N` branches.

## Suggested Phase Build Order

### Phase 1: Server Copy + Docker Packaging
Zero iOS risk. Establishes working endpoint and security hygiene before any iOS code. Delivers `server/` in repo; `docker compose up --build` produces working stack; `/healthz` returns 200.

### Phase 2: iOS Settings UI
Configuration plumbing before any network calls. Delivers `MoreRoute.remoteServer`, `MoreRemoteServerView`, `GooseRemoteUploadStore` with server URL/API key/enabled flag.

### Phase 3: iOS Upload Service
Core feature. Delivers `GooseRemoteUploadService` with POST, retry, wiring to `handleCaptureFrameWriteResult`. BLE data appears in TimescaleDB within seconds.

### Phase 4: Upload Status Feedback
Observability. Delivers `@Published remoteUploadStatus`, pending batch count in UI, health check on launch.

### Phase 5: Upstream PR Integration
Independent track. Risk-ordered merge: #3 → #1 → #6 → #7 → #13 → #4 → #10 → #12 → #5. Clean PRs submitted back to `b-nnett/goose`.

## Open Gaps (Resolve Before Planning)

1. **ATS hostname strategy:** mDNS (`whoop.local`), real DNS, or local hostname? Must be decided before Phase 3; document in Phase 2 settings UI.
2. **Stable `batch_id` for retry:** Derive deterministically (e.g. SHA256 of `deviceID + sessionStartTimestamp + frameCount`) — not a new UUID per attempt.
3. **UserDefaults vs Keychain for API key:** Use Keychain (correct for credentials; not included in iCloud sync by default).
4. **PR #12 FFI threading risk:** Read full diff against fork before planning Phase 5 tasks.

## Confidence Assessment

| Area | Confidence | Notes |
|------|-----------|-------|
| Stack | HIGH | All technologies observed directly in working code |
| Features | HIGH | Server API contract read directly; PRs read via gh CLI |
| Architecture | HIGH | All patterns observed directly in existing codebase |
| Pitfalls | HIGH | Verified against Apple docs, Docker docs, git docs |

---
*Research completed: 2026-06-03 — Ready for roadmap*
