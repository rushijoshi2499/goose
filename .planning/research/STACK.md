# Technology Stack — Goose Server Integration

**Project:** Goose (tigercraft4/goose fork)
**Researched:** 2026-06-03
**Scope:** iOS upload client + server Docker packaging for self-hosted deployment

---

## Context: What Already Exists

This is brownfield integration, not greenfield. The existing pieces constrain choices more than any preference does.

| Component | State | Location |
|-----------|-------|----------|
| FastAPI ingest server | Working, tested | `my-whoop/server/ingest/` |
| TimescaleDB schema | Stable, has migrations | `my-whoop/server/db/init.sql` |
| Docker Compose stack | Working | `my-whoop/server/docker-compose.yml` |
| Dockerfile | Working (single-stage) | `my-whoop/server/ingest/Dockerfile` |
| iOS URLSession HTTP client | Pattern exists in codebase | `OpenAICoachResponsesClient.swift` |
| iOS Keychain secrets | Pattern exists in codebase | `OnboardingPersistence.swift` |
| iOS settings persistence | UserDefaults | `OnboardingPersistence.swift` |

The constraint from `PROJECT.md` is explicit: **no external iOS dependencies**. URLSession only.

---

## Recommended Stack

### iOS Upload Client

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| URLSession | System (iOS 15+) | HTTP POST to `/v1/ingest-decoded` | Zero dependencies; already used in OpenAICoachResponsesClient |
| Codable / JSONEncoder | System | Serialize DecodedBatch payload | Native; matches server Pydantic model field names exactly |
| Keychain (SecItem*) | System | Store bearer token + server URL | Already used in OnboardingPersistence for ChatGPT auth |
| UserDefaults | System | Upload enabled flag, last-upload timestamp | Consistent with existing settings pattern |
| DispatchQueue | System | Offline queue drain, retry loop | Used everywhere in GooseAppModel |

**No third-party networking library.** Alamofire, AsyncHTTPClient etc. are out of scope by project constraint and unnecessary for a single POST endpoint.

#### URLSession upload pattern for this project

**Do NOT use background URLSession (`URLSessionConfiguration.background`).** Here's why:

1. Background upload tasks require writing the body to a temp file on disk before upload — the payload is ephemeral decoded stream data, not a file.
2. Background tasks require `application(_:handleEventsForBackgroundURLSession:)` in the AppDelegate and a separate delegate object — significant complexity for no user benefit. The app already uses `UIBackgroundTaskIdentifier` (see `GooseAppModel`) for overnight guard.
3. The upload is opportunistic (best-effort while connected), not guaranteed delivery. An offline queue persisted to SQLite or UserDefaults is simpler and sufficient.
4. The server already handles idempotent upserts (`ON CONFLICT … DO UPDATE`) — retrying the same batch is safe.

**Use instead:** `URLSession.shared.data(for:)` with Swift concurrency (`async/await`) in a `Task`, triggered after each successful BLE decode batch. Add a lightweight persist-and-retry mechanism using an in-process queue (e.g. a `DispatchQueue` serializing POST attempts, with exponential backoff for 5xx/network errors). If the app goes to background, drain any pending batch inside an existing `UIBackgroundTaskIdentifier` — the pattern is already established in `GooseAppModel+OvernightRun.swift`.

**Confidence: HIGH** — this follows the exact pattern already in `OpenAICoachResponsesClient.swift` and `GooseAppModel` background task management. No new patterns are introduced.

#### iOS request structure

```swift
// Matches server DecodedBatch exactly (snake_case via CodingKeys)
struct ServerUploadBatch: Encodable {
    let device: ServerDevice
    let streams: ServerStreams
    let deviceGeneration: String

    enum CodingKeys: String, CodingKey {
        case device, streams
        case deviceGeneration = "device_generation"
    }
}
```

The server's Pydantic `DecodedBatch` uses `device_generation` (snake_case). JSONEncoder needs custom CodingKeys or a `keyEncodingStrategy = .convertToSnakeCase` — either works; explicit CodingKeys are safer against future field additions.

#### Bearer token + server URL storage

Store in Keychain using `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` — same attribute as existing `OnboardingPersistence.writeKeychainData`. Server URL can go in UserDefaults (not secret). Bearer token must go in Keychain (secret).

#### Upload trigger points

Preferred: trigger upload at the end of each `upsert_streams` equivalent in the iOS decoded-stream pipeline — after frames are written to SQLite (captureFrameWriteQueue drain) so the local store is always the authoritative record. Upload is fire-and-forget with retry queue; SQLite is the source of truth.

**Confidence: HIGH** — consistent with existing `CaptureFrameWriteQueue` drain-and-callback pattern.

---

### Server Packaging

#### Dockerfile — Multi-Stage Build

The existing Dockerfile is single-stage (`python:3.11-slim`). For production Docker packaging, upgrade to multi-stage to separate build-time deps (pip, wheel compilation for neurokit2/scipy) from the runtime image.

**Recommended Dockerfile pattern:**

```dockerfile
# ── Stage 1: build wheels ──────────────────────────────────────────────────────
FROM python:3.11-slim AS builder
WORKDIR /build
COPY ingest/requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip wheel --no-cache-dir --wheel-dir /wheels -r requirements.txt

# ── Stage 2: runtime ───────────────────────────────────────────────────────────
FROM python:3.11-slim
WORKDIR /app
COPY --from=builder /wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links /wheels /wheels/*.whl
COPY packages/whoop-protocol /app/whoop-protocol
RUN pip install --no-cache-dir /app/whoop-protocol
COPY db /app/db
COPY ingest/app /app/app
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/healthz')"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**Why multi-stage matters here:** scipy, neurokit2, scikit-learn, and numpy have C extensions. The builder stage installs build tools; the runtime stage gets only pre-built wheels. The resulting image is smaller and has no compiler installed (smaller attack surface).

**Why Python 3.11, not 3.12+:** The existing `requirements.txt` pins `python:3.11-slim`. neurokit2 0.2.13 and scipy 1.17.1 are tested against 3.11. Changing the base image version risks breaking C extension compatibility without a full test run. Staying on 3.11 is correct for this brownfield copy.

**Confidence: HIGH** — multi-stage is standard practice; the specific image choice matches existing requirements.

#### Health Check

The server already exposes `GET /healthz` which checks the DB connection with a 3-second timeout. Use it in the HEALTHCHECK directive (shown above). The Docker Compose already has a `pg_isready` healthcheck on `whoop-db` and `depends_on: condition: service_healthy` — this pattern is correct and should be preserved verbatim.

**Confidence: HIGH** — observed directly in the existing `docker-compose.yml` and `main.py`.

#### Secrets Management

**Do NOT use Docker secrets (Swarm), Vault, or any secrets manager.** This is a self-hosted personal server. The correct pattern for this scope:

1. `.env` file on the host machine (already has `.env.example`)
2. `docker compose --env-file .env up` reads it automatically
3. `.env` is `.gitignore`d — never committed
4. The only required secrets are `WHOOP_API_KEY` and `WHOOP_DB_PASSWORD`

This is exactly what the existing setup does. Preserve it. Do not complicate it.

**Confidence: HIGH** — verified against existing `.env.example` and `docker-compose.yml`.

---

### TimescaleDB + Docker Compose

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| timescale/timescaledb | 2.17.2-pg16 | Time-series storage for biometric streams | Already in use, schema tested |
| Docker Compose v2 | System (`docker compose`) | Stack orchestration | Standard for self-hosted; no Swarm/K8s needed |

**The existing `docker-compose.yml` is correct.** Copy it verbatim. The only adjustments needed:

1. Update the `build.context` path if the server moves to `server/` within the Goose repo (it currently assumes `server/` is the build context).
2. Verify `DATA_ROOT` is documented clearly in the README — it must be an absolute path on the host.

**Hypertable design is already correct:** `hr_samples`, `rr_intervals`, `events`, `battery`, `spo2_samples`, `skin_temp_samples`, `resp_samples`, `gravity_samples` are hypertables on `ts`. `daily_metrics`, `sleep_sessions`, `exercise_sessions`, `profile` are plain tables (low-cardinality, exact-key lookups). This split is the right TimescaleDB design.

**Confidence: HIGH** — schema observed directly, design rationale matches TimescaleDB docs for high-frequency vs. derived data.

---

### Docker Image Distribution — Dockerfile in Repo vs Pre-Built Image

**Recommendation: Dockerfile in repo, no pre-built image registry.**

Rationale:

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| Dockerfile in repo + `docker compose build` | User owns the build; no registry account needed; self-contained; works with `git pull && docker compose up --build` | Slightly slower first deploy (builds locally) | **Recommended** |
| GitHub Container Registry (ghcr.io) | Faster pull; no local build | Requires registry account; version pinning; private repo needs PAT; adds CI complexity | Out of scope |
| Docker Hub | Easier to share publicly | Public image exposes server internals; bearer token flow unclear | Not appropriate |

The target user is a single person running their own server. `git pull && docker compose up --build` is the correct deploy story. Pre-built images add CI/CD complexity with no benefit for one user.

**Confidence: HIGH** — matches the project constraint ("servidor pessoal"; "git pull").

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| iOS networking | URLSession | Alamofire, AsyncHTTPClient | Explicit project constraint; URLSession sufficient |
| iOS upload session | `URLSession.shared` (foreground) | `URLSessionConfiguration.background` | Background upload requires file-based body + AppDelegate callbacks; unnecessary complexity; retry queue achieves same reliability |
| iOS secret storage | Keychain | UserDefaults | Bearer token is a secret; Keychain is mandatory |
| Python base image | `python:3.11-slim` | `python:3.12-slim`, `python:3.13-slim` | Existing requirements pinned to 3.11; neurokit2/scipy C extensions need testing per Python version |
| Dockerfile | Multi-stage (builder + runtime) | Single-stage (existing) | Multi-stage reduces image size and removes compiler from runtime |
| Secrets | `.env` file | Docker secrets, Vault | Personal server; `.env` is correct complexity level |
| Distribution | Dockerfile in repo | GHCR pre-built image | Single user; no CI/CD needed |

---

## Installation

No new dependencies are introduced on either side.

### iOS
No `Package.swift` or CocoaPods changes. New files are pure Swift using Foundation only.

### Server (copy to `server/` in Goose repo)
```bash
# First deploy
cp -r /path/to/my-whoop/server server/
echo "DATA_ROOT=/your/data/path" >> server/.env
echo "WHOOP_API_KEY=$(openssl rand -hex 32)" >> server/.env
echo "WHOOP_DB_PASSWORD=$(openssl rand -hex 32)" >> server/.env
cd server
docker compose up --build -d
```

### Verifying the server is healthy
```bash
curl http://localhost:8770/healthz
# {"status":"ok"}
```

---

## Confidence Assessment

| Area | Confidence | Basis |
|------|------------|-------|
| iOS URLSession pattern | HIGH | Pattern directly observed in `OpenAICoachResponsesClient.swift`; no third-party deps per constraint |
| iOS Keychain for token | HIGH | Pattern directly observed in `OnboardingPersistence.swift` |
| Background upload approach (foreground only) | HIGH | iOS docs + project constraint analysis; retry queue is simpler |
| FastAPI + Uvicorn | HIGH | Observed working in existing server; Context7 FastAPI docs verified |
| Multi-stage Dockerfile | HIGH | Standard Docker practice; matches existing Dockerfile structure |
| Python 3.11 base image | HIGH | Matches existing `requirements.txt`; avoids C extension regression risk |
| TimescaleDB version (2.17.2-pg16) | HIGH | Observed directly in `docker-compose.yml`; already working |
| Dockerfile in repo vs registry | HIGH | Project scope and user profile make this unambiguous |
| `.env` for secrets | HIGH | Already the pattern in `.env.example`; correct complexity level |

---

## Key Integration Points for Implementation

1. **Upload payload is already defined server-side.** The `DecodedBatch` Pydantic model in `main.py` is the contract. The Swift `Encodable` struct must match it field-for-field. `device_generation` must snake_case to `"device_generation"` — use `CodingKeys` or `keyEncodingStrategy`.

2. **Server is idempotent.** All stream upserts use `ON CONFLICT … DO UPDATE` or `DO NOTHING`. The iOS client can safely retry any failed upload without creating duplicates. This eliminates the need for complex exactly-once delivery logic.

3. **Upload trigger point.** The existing iOS pipeline already writes frames to SQLite via `CaptureFrameWriteQueue`. The upload should happen after each successful batch write (post-drain callback), not during BLE frame reception. This keeps the upload decoupled from the real-time pipeline.

4. **Server URL and token are user-configurable.** They belong in a new "Server" section under the "More" tab (consistent with `MoreRouteModels.swift` structure). URL goes to UserDefaults; token goes to Keychain.

5. **Build context for Docker.** The existing `Dockerfile` uses `COPY packages/whoop-protocol` which requires the build context to be `server/` (the parent of both `ingest/` and `packages/`). This is already correct in `docker-compose.yml` (`context: .` with `dockerfile: ingest/Dockerfile`). Preserve this when copying to the Goose repo.

---

## Sources

- Existing `my-whoop/server/ingest/Dockerfile` (observed directly)
- Existing `my-whoop/server/docker-compose.yml` (observed directly)
- Existing `my-whoop/server/ingest/app/main.py` — `DecodedBatch` model, `/v1/ingest-decoded` endpoint (observed directly)
- Existing `my-whoop/server/ingest/app/store.py` — idempotent upsert pattern (observed directly)
- Existing `my-whoop/server/db/init.sql` — full schema (observed directly)
- Existing `GooseSwift/OpenAICoachResponsesClient.swift` — URLSession pattern (observed directly)
- Existing `GooseSwift/OnboardingPersistence.swift` — Keychain + UserDefaults pattern (observed directly)
- Existing `GooseSwift/GooseAppModel.swift` — `UIBackgroundTaskIdentifier` pattern (observed directly)
- FastAPI deployment docs via Context7 `/fastapi/fastapi` — uvicorn, Docker, multi-worker (HIGH reputation source)
- `PROJECT.md` constraints: no external iOS deps; URLSession only; Bearer token simple auth
