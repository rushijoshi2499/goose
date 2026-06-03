# Domain Pitfalls

**Domain:** iOS BLE capture app + self-hosted FastAPI/TimescaleDB server with Docker
**Researched:** 2026-06-03
**Overall confidence:** HIGH — all pitfalls verified against official Apple docs, Docker docs, FastAPI docs, and TimescaleDB source. Git cherry-pick pitfalls verified against git documentation.

---

## Critical Pitfalls

Mistakes that cause data loss, silent failures, or require rewrites.

---

### Pitfall 1: URLSession background session with completion-handler closures

**What goes wrong:** `URLSession` background sessions silently discard completion-handler closures. The network request still goes out, but the result callback is never invoked. The iOS developer assumes the upload worked because there is no error — but the app never processes the response and cannot react to failures.

**Why it happens:** Apple explicitly prohibits completion-handler–based tasks in background `URLSessionConfiguration`. Only delegate-based sessions receive callbacks from background tasks. This is not a warning at compile time; the code compiles and runs with no indication that callbacks will be swallowed.

**Consequences:** Uploads that fail silently; duplicate retries if the caller adds manual retry logic that never triggers; data loss if the caller deletes the local frame buffer on the assumption the closure confirmed success.

**Applies to this project:** GooseSwift will use `URLSession` POST for `/v1/ingest-decoded`. If the upload is triggered while the user backgrounds the app (which is likely, given overnight BLE recording), and a background `URLSessionConfiguration` is used, every completion handler is a no-op.

**Prevention:**
- Use `URLSessionConfiguration.default` (foreground) for the upload if you do not need true background delivery.
- If background delivery is required, implement `URLSessionTaskDelegate` / `URLSessionDataDelegate` on a class, never closures. Implement `application(_:handleEventsForBackgroundURLSession:completionHandler:)` in `AppDelegate` and store the handler; call it only inside `urlSessionDidFinishEvents(forBackgroundSession:)`.
- Use only `uploadTask(with:from:)` or `uploadTask(with:fromFile:)` in background sessions — `dataTask` is not supported.

**Warning signs:**
- Upload code uses `URLSession.shared.dataTask` or a closure-based `uploadTask` inside a background session.
- No `URLSessionDelegate` class exists in the codebase.
- No `handleEventsForBackgroundURLSession` implementation in AppDelegate.

**Phase that must address it:** Phase — iOS upload client implementation.

---

### Pitfall 2: ATS blocks HTTP connections to self-hosted server IP addresses

**What goes wrong:** iOS App Transport Security (ATS) blocks all plain HTTP by default. Critically, `NSExceptionDomains` does not accept raw IP addresses as keys. A server accessed via `192.168.1.x` or a dynamic LAN IP cannot be whitelisted with a per-domain exception. Using `NSAllowsArbitraryLoads: true` disables ATS globally, which triggers mandatory manual App Store review and is rejected for apps distributed on the public App Store.

**Why it happens:** The project constraint is a personal self-hosted server. Users may access it by IP, by local hostname (`.local` via mDNS), or by a custom domain. Only the last two options have clean ATS exception paths.

**Consequences:**
- All HTTP requests to IP addresses fail with `NSURLErrorDomain -1022` (ATS policy violated).
- `NSAllowsArbitraryLoads` makes the app unsubmittable to the App Store.
- A subtle secondary pitfall: `NSAllowsArbitraryLoads: true` combined with `NSExceptionDomains` entries causes the exceptions to *tighten* ATS for those specific domains — this is the opposite of the developer's intent and is commonly misread.

**Prevention:**
- Require the server to be accessed via a resolvable hostname (e.g., `whoop.local` via mDNS / Bonjour, or a real DNS name).
- Add a targeted `NSExceptionDomains` entry for that hostname with only `NSExceptionAllowsInsecureHTTPLoads: true`.
- Ideal long-term: configure the server with a self-signed TLS cert or Let's Encrypt cert (no ATS exception needed at all).
- Do not use `NSAllowsArbitraryLoads`.

**Warning signs:**
- Server URL in GooseSwift is stored as a raw IP address string.
- `Info.plist` contains `NSAllowsArbitraryLoads: true` without a scoped domain exception.
- Network requests to the server fail immediately in the iOS simulator with no HTTP response code.

**Phase that must address it:** Phase — iOS upload client + deployment docs.

---

### Pitfall 3: Docker image layers permanently expose secrets copied into them

**What goes wrong:** Any credential placed in a Docker image layer — via `ENV`, `ARG`, `COPY` of a credentials file, or an inline `RUN` command — is permanently embedded in that layer's metadata. Even if a later layer deletes the file, `docker history --no-trunc` reveals the value. Pushing the image to a registry (Docker Hub, GHCR) leaks the secret globally.

**Why it happens:** Docker's copy-on-write layer model stores every `RUN`/`COPY`/`ENV` step immutably. "Deleting" in a later `RUN rm` step adds a whiteout entry but does not remove the earlier layer. Developers frequently pass database passwords or API tokens as `ARG` build variables, believing they are temporary.

**Applies to this project:** The `docker-compose.yml` being copied from `my-whoop` correctly uses environment variable substitution (`${WHOOP_API_KEY}`, `${WHOOP_DB_PASSWORD}`) rather than hardcoded values. The risk is in the *copy process* — if a developer accidentally commits a `.env` file alongside the `server/` directory, the secret lands in git history permanently.

**Consequences:** API key and database password exposed in git history; cannot be removed without a full `git filter-repo` rewrite and force-push.

**Prevention:**
- Add `.env` and `*.env` to `.gitignore` immediately when the `server/` directory is added to the repo.
- Write a `server/.env.example` with placeholder values and commit that instead.
- In the `Dockerfile`, never use `ARG` or `ENV` to pass runtime secrets — pass them only via `docker compose`'s `environment` block from the host shell.
- Use Docker build secrets (`--secret id=...`) if secrets are needed at build time (not applicable here since the Dockerfile only installs packages).

**Warning signs:**
- `.env` or `.env.local` present in the `server/` directory.
- `git status` shows `.env` as an untracked file before the first commit.
- `docker history <image>` shows `POSTGRES_PASSWORD` or `WHOOP_API_KEY` in a layer command.

**Phase that must address it:** Phase — Docker packaging (the very first commit adding `server/`).

---

### Pitfall 4: bootstrap_schema runs init.sql on a live database and ALTER TABLE on compressed hypertable chunks blocks

**What goes wrong:** The `db.py` `bootstrap_schema()` function re-applies `init.sql` on every container startup. The `init.sql` uses `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` for all schema migrations. This is safe on plain PostgreSQL tables. On TimescaleDB hypertables with compression enabled, `ALTER TABLE` that adds a column can fail or silently cause chunk decompression — and on large hypertables it can block for minutes with an `AccessExclusiveLock`.

**Why it happens:** TimescaleDB compressed chunks are stored in a column-oriented format. Adding a new column to a compressed hypertable requires decompressing all existing chunks, applying the schema change, and re-compressing. The current schema does not enable compression, so this is a future risk rather than an immediate one. However, it becomes critical the moment anyone turns on `timescaledb.enable_columnstore` or `add_compression_policy`.

**Secondary risk:** The `_SCHEMA_PATHS` search list in `db.py` tries three different paths using `os.path.exists`. If the container mounts a volume or the `COPY db /app/db` line in the Dockerfile resolves incorrectly, `_schema_sql()` raises `FileNotFoundError` and the service fails to start — with no data-layer error, only an application exception.

**Consequences:** Service fails to start after any Docker image rebuild if `init.sql` is not found at the expected paths. Schema additions block on live hypertables with compression. Rolling back a mis-applied migration is not possible without manual SQL.

**Prevention:**
- Keep compression disabled until dashboard/analysis phases explicitly need it.
- When compression is eventually added, use `timescaledb.enable_columnstore` only on new tables, not existing ones.
- Add a smoke test to the container health check that verifies `init.sql` is reachable before the service starts.
- Pin the schema search to a single absolute path (`/app/db/init.sql`) rather than a trial list; validate in the Dockerfile that the file exists at that path after `COPY`.

**Warning signs:**
- `docker compose logs whoop-ingest` shows `FileNotFoundError: init.sql not found` on startup.
- Any `ALTER TABLE hr_samples` command hangs in `pg_locks` for more than a few seconds.
- `timescaledb.enable_columnstore` appears in `docker-compose.yml` or `init.sql` without a migration plan.

**Phase that must address it:** Phase — Docker packaging and server copy.

---

## Moderate Pitfalls

Mistakes that cause significant rework but not data loss or security incidents.

---

### Pitfall 5: Cherry-picking upstream PRs instead of merging creates duplicate-commit conflicts

**What goes wrong:** If the fork applies upstream PRs via `git cherry-pick` rather than `git merge`, each cherry-picked commit gets a new SHA. When the upstream later merges those same PRs (or when the fork tries to merge `upstream/main`), Git sees the changes as two different commits and attempts to apply them twice. This manifests as conflicts in `git merge upstream/main` even though the content is identical.

**Why it happens:** Cherry-pick does 3-way merge internally using context lines. If the surrounding code differs between the fork and upstream (which it will, since new features are being added), the context mismatches and the pick either fails or produces a ghost conflict.

**Applies to this project:** 9 upstream PRs need review and integration. Some PRs overlap in scope (PR #6 and #10 both touch Rust CI; PR #12 and #13 both touch FFI). Picking selectively from overlapping PRs multiplies the conflict surface.

**Prevention:**
- Add `upstream` as a git remote (`git remote add upstream https://github.com/b-nnett/goose`).
- Fetch each PR as a branch (`git fetch upstream pull/N/head:pr/N`).
- Review and merge each PR as `git merge --no-ff pr/N` rather than cherry-pick.
- Where a PR has issues that need fixing before merging, fix on the `pr/N` branch, then merge — do not cherry-pick individual commits.
- Use cherry-pick only for single-commit targeted backports, and always with `-x` flag for traceability.

**Warning signs:**
- `git log --all --oneline` shows duplicate commit messages with different SHAs.
- `git merge upstream/main` produces conflicts in files that were already updated by an earlier cherry-pick.
- No `upstream` remote exists in the repo (`git remote -v` shows only `origin`).

**Phase that must address it:** Phase — upstream PR integration.

---

### Pitfall 6: FastAPI routes not protected by the Bearer token dependency

**What goes wrong:** FastAPI does not apply authentication globally by default. Every route that should be protected must explicitly declare `Depends(verify_token)` (or equivalent). A new route added without the dependency annotation is silently open to the public internet. This is easy to miss during code review because unauthenticated routes look identical to authenticated ones at a glance.

**Why it happens:** FastAPI's dependency injection is opt-in per route. The existing `my-whoop` server likely applies auth per-route; copying that code to Goose and adding new routes (e.g., a health check, or future endpoints) risks a developer forgetting the `Depends`.

**Applies to this project:** `POST /v1/ingest-decoded` is the primary ingest route. If a health check endpoint (`GET /healthz`) is added without thought, it is fine unauthenticated. But any future data-reading endpoint would be a problem.

**Prevention:**
- Apply auth globally via `app = FastAPI(dependencies=[Depends(verify_token)])` and then explicitly opt individual routes *out* (e.g., `GET /healthz`) using a `no_auth` override or by mounting on a sub-router without the global dep.
- Add a test that POSTs to every endpoint without an `Authorization` header and asserts `401` for all endpoints except the documented public ones.

**Warning signs:**
- New routes added to `app/main.py` without a `Depends(...)` annotation.
- `curl -X POST http://server/v1/some-new-endpoint` returns `200` without any `Authorization` header.
- No automated test for authentication coverage.

**Phase that must address it:** Phase — server copy and FastAPI integration.

---

### Pitfall 7: Docker build context includes the entire repo, inflating image build time and context size

**What goes wrong:** Without a `.dockerignore`, `docker compose build` sends the entire repo directory as the build context to the Docker daemon. This includes the iOS Xcode project, Rust core, `.git` directory (potentially hundreds of MB), and all `node_modules` or build artifacts. This does not affect the final image contents (only `COPY`ed files end up in the image) but massively slows every build.

**Applies to this project:** The `docker-compose.yml` is being placed at `server/` inside the Goose repo. If the build context is set to `.` (repo root) rather than `server/`, the context includes everything. Even if the context is `server/`, a missing `.dockerignore` includes Python `__pycache__`, `.pytest_cache`, virtual environments, and test fixtures.

**Prevention:**
- Add `server/.dockerignore` listing at minimum: `__pycache__/`, `*.pyc`, `.pytest_cache/`, `venv/`, `.env`, `*.env`, `tests/`, `*.md`.
- Set `context: ./server` in `docker-compose.yml` (already done in the `my-whoop` original) rather than the repo root.
- Verify context size with `docker build --no-cache 2>&1 | grep "Sending build context"` — should be under 10 MB for this server.

**Warning signs:**
- `docker compose build` takes more than 30 seconds before any `RUN` steps execute.
- Build log shows "Sending build context to Docker daemon: 500 MB".
- `.git` directory appears in the container (`docker run ... ls /app`).

**Phase that must address it:** Phase — Docker packaging.

---

### Pitfall 8: Upstream fork divergence — fork accumulates commits that block future upstream contributions

**What goes wrong:** As the fork adds the server, upload client, and Docker packaging, commits pile up on `main` that upstream will never accept (server code, docker-compose, iOS upload). If upstream merges some of the 9 PRs before the fork integrates them, the fork's `main` diverges from `upstream/main` in both directions. Future PRs from the fork to upstream become giant diffs that are impossible to review.

**Why it happens:** The project intends to submit PRs back to `b-nnett/goose` (mentioned in PROJECT.md: "PRs de volta ao upstream"). Mixing fork-specific infrastructure (server, Docker) with upstream-quality fixes (FFI safety, CI, scroll perf) on the same branch makes it impossible to cherry-pick clean upstream-facing commits.

**Prevention:**
- Keep fork-specific work (server, Docker, iOS upload) on a branch like `feat/server` or committed only to paths (`server/`, `GooseSwift/RemoteUploadClient.swift`) that will never conflict with upstream.
- Upstream-quality fixes (PRs #1, #3, #4, #6, etc.) should be reviewable in isolation as PRs to upstream. Work on these in branches named `upstream/fix-N` and submit directly from those branches.
- Never put server/ or docker-compose.yml on a branch intended as an upstream PR.

**Warning signs:**
- `git log upstream/main..HEAD` shows server-related commits mixed with BLE/FFI fixes.
- A PR opened against `b-nnett/goose` includes Dockerfile changes.
- `git diff upstream/main HEAD -- GooseSwift/` shows iOS upload client changes alongside scroll-perf fixes.

**Phase that must address it:** Phase — upstream PR integration (plan branch strategy before writing any code).

---

## Minor Pitfalls

Mistakes that cause friction, debugging time, or minor rework.

---

### Pitfall 9: Bearer token comparison is timing-attack vulnerable

**What goes wrong:** Comparing the incoming `Authorization: Bearer <token>` header value against the expected token using `==` (Python string equality or Swift `==`) is vulnerable to timing attacks. An attacker can measure response time differences to determine how many leading bytes of a guessed token match the real one.

**Why it happens:** String equality short-circuits on the first mismatched character. For a server on a home network this is a very low-risk attack, but it is the wrong habit to establish.

**Prevention:** Use `hmac.compare_digest(received_token, expected_token)` in Python (FastAPI side). This runs in constant time regardless of where the mismatch occurs.

**Warning signs:** `if token == config.api_key:` in `app/main.py` rather than `hmac.compare_digest(...)`.

**Phase that must address it:** Phase — server copy and FastAPI integration.

---

### Pitfall 10: `docker-entrypoint-initdb.d` init.sql runs ONLY on first container start — schema additions are silently skipped on existing volumes

**What goes wrong:** PostgreSQL (and thus TimescaleDB) only executes scripts in `/docker-entrypoint-initdb.d/` when the data directory is empty — i.e., on first start of a brand-new volume. If the schema evolves and new `ALTER TABLE` statements are appended to `init.sql`, those statements are never applied to an existing database volume.

**Applies to this project:** The `my-whoop` `db.py` solves this with `bootstrap_schema()` which re-runs `init.sql` on every container startup using `CREATE ... IF NOT EXISTS` and `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` (idempotent). The `init.sql` file is also mounted to `docker-entrypoint-initdb.d` for first-run bootstrapping. Both paths must stay in sync. If a developer adds only to `init.sql` but forgets that `bootstrap_schema()` must run on existing deployments, the migration never applies.

**Prevention:** Always add schema changes as `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` at the bottom of `init.sql`. Never change existing `CREATE TABLE` blocks. Test migrations against an existing populated volume, not just a fresh `docker compose up`.

**Warning signs:** Schema change added to the `CREATE TABLE` block only (not as a standalone `ALTER TABLE IF NOT EXISTS` below it). Running `docker compose up` against an existing volume shows no migration log output.

**Phase that must address it:** Phase — server copy. Establish the convention before any data accumulates.

---

### Pitfall 11: BLE data captured during iOS upload failure accumulates without back-pressure

**What goes wrong:** The iOS app continuously captures WHOOP BLE frames and stores them in SQLite. If the server is unreachable (home network offline, server down), upload attempts fail silently. Without a retry queue and a maximum pending-frame threshold, the SQLite local store grows unboundedly. When the server comes back online, a burst of accumulated data is uploaded all at once, potentially overwhelming the server or causing PostgreSQL `WAL` bloat.

**Prevention:**
- Implement exponential back-off for failed uploads (start: 5 s, max: 5 min).
- Cap the pending-upload queue at a configurable frame count; log a warning (not a crash) when the cap is exceeded.
- On the server side, the `batch_id` idempotency key in `store.batch_exists()` already handles duplicate uploads — but the iOS side must generate stable `batch_id` values for retry consistency.

**Warning signs:** The upload client generates a new UUID for each upload attempt rather than deriving a stable `batch_id` from the frame content or time range.

**Phase that must address it:** Phase — iOS upload client implementation.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Server copy (server/ directory) | `.env` accidentally committed with real credentials | Add `.gitignore` entry for `.env` in the same commit that adds `server/` |
| Docker packaging | Build context too large; init.sql path mismatch in container | Add `.dockerignore`; pin schema path to `/app/db/init.sql` |
| iOS upload client | ATS blocks HTTP to IP address; background session swallows callbacks | Use hostname + NSExceptionDomains; use delegate-based URLSession |
| Upstream PR integration | Cherry-pick divergence; fork-specific commits mixed into upstream PRs | Merge not cherry-pick; separate branches for upstream vs fork-only work |
| FastAPI route additions | New routes missing auth Depends | Global dependency or per-route test coverage |
| TimescaleDB schema evolution | ALTER TABLE blocked on compressed chunks | No compression until explicitly needed; always use IF NOT EXISTS |

---

## Sources

- Apple Developer — `NSAppTransportSecurity` Info.plist key documentation: https://developer.apple.com/documentation/bundleresources/information-property-list/nsapptransportsecurity
- Apple Developer — `URLSessionConfiguration` background session requirements: https://developer.apple.com/documentation/foundation/urlsessionconfiguration
- Docker Documentation — Build secrets and layer persistence: https://docs.docker.com/build/building/secrets/
- Docker Documentation — Image build best practices, .dockerignore: https://docs.docker.com/build/building/best-practices/
- Docker Compose — Environment variable precedence: https://docs.docker.com/compose/how-tos/environment-variables/envvars-precedence/
- FastAPI — Security / OAuth2 with JWT (route auth pitfalls): https://fastapi.tiangolo.com/tutorial/security/oauth2-jwt/
- git — cherry-pick man page (duplicate commits, -x flag, merge vs cherry-pick): https://git-scm.com/docs/git-cherry-pick
- GitHub Documentation — Syncing a fork (divergence and force-overwrite risk): https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks/syncing-a-fork
- TimescaleDB — `create_hypertable` with `if_not_exists` (Context7 source): https://github.com/timescale/timescaledb
- Alembic — Migration ops reference (idempotency guards, rollback safety): https://alembic.sqlalchemy.org/en/latest/ops.html
- my-whoop `db/init.sql` — examined directly for idempotency pattern in use: /Users/francisco/Documents/my-whoop/server/db/init.sql
- my-whoop `docker-compose.yml` — examined directly for credentials handling: /Users/francisco/Documents/my-whoop/server/docker-compose.yml
