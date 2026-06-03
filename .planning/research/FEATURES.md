# Feature Landscape

**Domain:** iOS biometric data sync app (WHOOP BLE → self-hosted FastAPI/TimescaleDB) + fork PR management
**Researched:** 2026-06-03
**Confidence:** HIGH (codebase read directly; server API read directly; upstream PRs read via gh CLI)

---

## Table Stakes

Features the utilizador espera. Ausência causa frustração ou dados perdidos silenciosamente.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Upload com retry em falha de rede | Sem retry, dados ficam perdidos se o iPhone muda de rede ou o servidor está momentaneamente indisponível. O utilizador não vê nada. | Low | URLSession native; exponential back-off simples com 3 tentativas é suficiente para MVP |
| Fila de upload persistida localmente | App fechada antes do upload terminar → dados perdidos sem fila. Para este caso pessoal o risco é baixo mas a percepção de fiabilidade é alta. | Medium | `UserDefaults` ou SQLite para marcar frames como "pendente/enviado"; não requer framework externo |
| URL do servidor configurável pelo utilizador | Servidor self-hosted: cada utilizador tem o seu próprio endereço. Hard-coded é bloqueador total. | Low | Campo de texto em More → Settings ou nova rota "Server"; persistido em `UserDefaults`. Já existe padrão de `OnboardingPersistence.swift` para settings simples |
| Bearer token configurável | Servidor usa `secrets.compare_digest(authorization, expected)`. Sem token configurável o utilizador não consegue autenticar. | Low | Mesmo mecanismo que o URL; campo de texto protegido (`.secureField`) em Settings |
| Feedback de estado do upload na UI | Utilizador precisa de saber se os dados chegaram ao servidor. Silêncio total cria incerteza. | Low | Um `@Published var uploadStatus: String` no store + linha de estado no More tab (padrão já estabelecido: `storageStatus`, `rawExportStatus`, etc.) |
| Deduplicação no servidor (upsert, não insert) | O servidor já usa `upsert_streams` — cada `(device_id, ts)` é idempotent. O iOS pode reenviar sem criar duplicados. Fundamental para retry seguro. | None (já existe) | Comportamento já implementado em `store.py`; iOS só precisa de reenviar livremente |
| Mapeamento correto de `device_id` | `POST /v1/ingest-decoded` exige `device.id`; o `GooseBLEClient` expõe `activeDeviceIdentifier: UUID`. Sem mapeamento correto os dados ficam associados ao device errado. | Low | Ler `ble.activeDeviceIdentifier?.uuidString` no momento do upload |

---

## Differentiators

Features que acrescentam valor real sem muita complexidade. Valem para v1 deste fork pessoal.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Indicador de conectividade servidor (healthz check) | `GET /healthz` já existe no servidor. Um ping ao arrancar e antes de cada upload evita falhas silenciosas e mostra ao utilizador se o servidor está acessível. | Low | `URLSession.dataTask` simples; resultado publicado como `serverReachability: String` |
| Upload automático após cada sessão de captura | Actualmente o utilizador exporta manualmente. Triggar upload quando `captureFrameWriteQueue` drena reduz fricção sem background fetch. | Medium | Hook em `handleCaptureFrameWriteResult` ou em `stopCapture()`; upload da janela da sessão actual |
| Contagem de registos enviados vs. total local | "47 de 52 frames enviados" dá confiança ao utilizador sem exigir dashboard. | Low | Resposta de `upsert_streams` já retorna `counts`; acumular em `MoreDataStore` |
| Suporte a `device_generation` no payload | Servidor aceita campo opcional `device_generation` (default `"5.0"`). Goose já detecta GEN4 via `rustDeviceType`. Preencher correctamente melhora análise futura. | Low | `ble.modelNumber` ou `rustDeviceType == "GEN4"` → `"4.0"` |

---

## Anti-Features

Features para **não** construir neste milestone — adicionam complexidade sem valor proporcional agora.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Background URLSession (BGURLSessionConfiguration) | Exige `application(_:handleEventsForBackgroundURLSession:completionHandler:)` no AppDelegate, background modes no entitlements, gestão de completion handlers, e comportamento diferente por iOS version. Para um servidor pessoal onde o utilizador abre a app activamente, a complexidade não compensa. | Upload foreground quando a sessão termina; retry na próxima abertura da app |
| Conflict resolution sofisticado | O servidor já resolve via upsert idempotente por `(device_id, ts)`. Não existe conflito real a gerir. | Nada — confiar no upsert do servidor |
| Dashboard / visualização de dados no iOS | Out of scope explícito no PROJECT.md. O servidor já tem dashboard HTML. | Link para `http://[serverURL]/` no More tab |
| Sync incremental com cursor / watermark | Adiciona estado partilhado complexo (o que foi enviado, o que não foi). Para o volume de dados de um utilizador pessoal, reenviar a janela completa da sessão é seguro (upsert idempotente). | Reenviar sempre a janela da sessão activa |
| Push notifications do servidor para o iOS | Exige APNs, servidor-side token management, certificados. Nenhum utilizador pessoal precisa de alertas push do seu próprio servidor. | Status pull via healthz check |
| Autenticação OAuth / 2FA | Declarado Out of Scope no PROJECT.md. Bearer token simples é suficiente para servidor pessoal. | Bearer token em `SecureField` |
| Exportação de dados do servidor para o iOS | Fluxo inverso (servidor → iOS) não tem caso de uso neste milestone. | Fora do scope |
| Integração com Apple Health para envio ao servidor | A integração Apple Health no fork é para leitura de fallback (PR #5 upstream). Enviar dados Apple Health para o servidor próprio não é o objetivo — o objetivo é enviar dados WHOOP já decodificados. | Manter Apple Health como fallback local apenas |

---

## Feature Dependencies

```
URL configurável → Bearer token configurável → Upload pode ser tentado
Upload tentado → Healthz check (confirma servidor acessível antes de tentar)
Upload tentado → Retry em falha (rede ou 5xx)
Retry → Fila persistida (sobrevive a fecho da app)
Upload concluído → Feedback de estado na UI
Upload concluído → Contagem de registos enviados
device_id correcto → Deduplicação correcta no servidor
```

---

## MVP Recommendation

Prioridade para este milestone:

1. URL + token configuráveis (More → Server Settings) — desbloqueador total
2. `GooseUploadClient` (URLSession, POST `/v1/ingest-decoded`, sync foreground) — core da feature
3. Retry simples em falha (3 tentativas, back-off 1s/2s/4s) — fiabilidade mínima
4. Feedback de estado na UI (`uploadStatus` publicado no More tab) — observabilidade
5. Healthz check ao arrancar — confirma configuração correcta

Diferenciadores a incluir se o tempo permitir:
- Contagem de registos enviados
- `device_generation` correcta no payload

Definitivamente adiar:
- Background URLSession
- Fila persistida em SQLite (MVP pode usar in-memory com retry; a perda de dados por fecho da app é aceitável para uso pessoal)

---

## Upstream PR Integration Workflow

### O que faz uma boa review/integração para um fork maintainer

**Categorização por risco antes de tocar no código:**

| PR | Título | Risco para o fork | Acção recomendada |
|----|--------|-------------------|-------------------|
| #1 | Fix timeout message + dedup duration parsing | Muito baixo — refactor de 2 funções, zero comportamento | Merge directo após leitura do diff |
| #3 | FFI safety docs | Nenhum — apenas comentários/docs | Merge directo |
| #4 | Scroll perf (Home/Health views) | Baixo — UI tuning, `@State` caching | Testar scroll no simulator antes de merge |
| #5 | Apple Health fallback | Alto — 1081 adições, toca em múltiplas stores, adiciona HealthKit flows | Review linha-a-linha; verificar que não conflitua com `HealthDataStore` existente no fork |
| #6 | Rust CI (fmt + build + test) | Muito baixo — só CI config | Merge depois de verificar que o workflow não usa secrets do upstream |
| #7 | `core.list_methods` RPC | Baixo — additive only, novos testes | Verificar que os 119 métodos listados batem com o bridge.rs do fork |
| #10 | CI + bug fixes Rust | Médio — corrige bugs reais (`store.rs` guard, Python imports) | Merge os bug fixes; adaptar CI ao repo do fork se necessário |
| #12 | FFI background threading | Médio — move calls do @MainActor, 146+/73- | Verificar que `refreshBridgeCatalogs` existe no fork com a mesma assinatura; risco de deadlock se coordenação errada |
| #13 | Windows compat | Baixo — `#[cfg(unix)]` gates, path normalization | Merge se o fork corre testes em CI; skip se só macOS/iOS |

**Workflow de integração para cada PR:**

1. Ler diff completo antes de qualquer coisa — entender o que muda, não só o título
2. Verificar conflitos com as alterações do fork (especialmente em `GooseAppModel.swift`, `HealthDataStore.swift`, `GooseRustBridge.swift`)
3. PRs de Rust puro (#6, #7, #10, #13): testar `cargo test` localmente antes de merge
4. PRs de Swift (#1, #4, #5, #12): build no Xcode, verificar que não há warnings novos
5. PRs com bug fixes reais (#10, #13): cherry-pick os fixes para o upstream como PRs separados com crédito ao autor original
6. Ordem sugerida de integração: #3 → #1 → #6 → #7 → #13 → #4 → #10 → #12 → #5 (tamanho crescente, risco crescente)

**Anti-padrões a evitar:**
- Merge de vários PRs num único commit de "bulk merge" — perde-se rastreabilidade
- Integrar #5 (Apple Health grande) antes de validar que não conflitua com upstream PR #12 (threading FFI)
- Enviar PRs de volta ao upstream sem corrigir primeiro o que o CI do upstream rejeita

---

## Sources

- Codebase lido directamente: `GooseSwift/`, `GooseAppModel.swift`, `MoreDataStore.swift`, `MoreRouteModels.swift`, `GooseAppModel+NotificationPipeline.swift`
- API do servidor: `/Users/francisco/Documents/my-whoop/server/ingest/app/main.py` (lido directamente)
- Upstream PRs: `gh pr list --repo b-nnett/goose` + `gh pr view` para PRs #1, #5, #7, #10, #12, #13
- PROJECT.md: `.planning/PROJECT.md` (constraints, out-of-scope explícitos)
- URLSession background patterns: Context7 via MZDownloadManager docs (HIGH confidence para o padrão de background session + completion handler)
- Confiança geral: HIGH — toda a informação crítica vem de leitura directa do código, não de inferência
