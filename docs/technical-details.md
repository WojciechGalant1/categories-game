# Szczegóły techniczne

Referencja architektury, konfiguracji i zachowania systemu — dla osób, które chcą zrozumieć *jak* działa aplikacja, nie tylko ją uruchomić.

← [README](../README.md) · Uruchomienie: [minikube.md](minikube.md) · Produkcja: [production.md](production.md)

## Spis treści

- [Struktura projektu](#struktura-projektu)
- [Konfiguracja](#konfiguracja)
- [Helm Chart](#helm-chart)
- [API i autoryzacja](#api-i-autoryzacja)
- [PostgreSQL (CloudNativePG)](#postgresql-cloudnativepg)
- [Skalowanie i synchronizacja stanu](#skalowanie-i-synchronizacja-stanu)
- [Observability](#observability)
- [Hardening backendu](#hardening-backendu)
- [Hardening frontendu](#hardening-frontendu)
- [Ograniczenia](#ograniczenia)

## Struktura projektu

```
.
├── backend/                          # Spring Boot 4 (Java 21) + JPA + Flyway + JWT + WebSocket
│   ├── src/main/java/com/example/panstwamiasta/
│   │   ├── auth/                     # JWT, filtry, autoryzacja pokoju
│   │   ├── config/                   # Security, Redis, rate limit, ProductionSecretsGuard
│   │   ├── controller/               # REST API (health, rooms)
│   │   ├── dto/                      # Request/response DTO (+ Bean Validation)
│   │   ├── exception/                # GlobalExceptionHandler, typowane wyjątki
│   │   ├── model/                    # Encje JPA (Player, GameState, RoomSettings)
│   │   ├── repository/               # Spring Data JPA
│   │   ├── room/                     # Encja Room (@Version optimistic locking)
│   │   ├── scheduler/                # Auto-stop rund (roundEndsAt)
│   │   ├── service/                  # RoomService, ScoreCalculator, TTL, broadcast
│   │   └── websocket/                # WebSocket handler + rejestr sesji
│   ├── src/main/resources/db/migration/   # Flyway V1–V3
│   ├── Dockerfile, openapi.yaml, mvnw
├── frontend/                         # React 19 + Vite + Tailwind + nginx unprivileged
│   ├── src/hooks/                    # useRoomWebSocket, useWebSocketSync, useGameActions
│   ├── src/services/                 # api.ts, errors.ts
│   ├── Dockerfile, nginx.conf, package-lock.json
├── helm/pm/                          # Helm chart (values, templates, Bitnami redis subchart)
├── scripts/create-secrets.sh         # bootstrap sekretów dev
├── infra/cert-manager/               # ClusterIssuery TLS
├── infra/sealed-secrets/             # Sealed Secrets prod
└── docs/                             # dokumentacja + screenshots/
```

## Konfiguracja

Parametry bazowe: [`helm/pm/values.yaml`](../helm/pm/values.yaml). Override minikube: [`values-minikube.yaml`](../helm/pm/values-minikube.yaml). Prod: [`values-prod.yaml`](../helm/pm/values-prod.yaml).

| | Gdzie | Domyślnie |
|---|------|-----------|
| Postgres mode | `postgres.mode` | `cnpg` (legacy: `legacy`) |
| CNPG Cluster | `postgres.cnpg.*` | `pm-postgres`, 3 instancje |
| Flyway baseline (brownfield) | `values-minikube.yaml` → `postgres.flyway.baselineOnMigrate` | jednorazowo `true`, potem `false` |
| Hasło Postgres | Secret `postgres-credentials` | `pm` / `pmpass` (dev) |
| HikariCP pool | `backend.hikari.maximumPoolSize` | 5 |
| Repliki frontend/backend | `frontend.replicas`, `hpa.*` | 2 / HPA 2–10 (CPU 70%) |
| Obrazy | `backend.image`, `frontend.image` | `pm-backend:3.2-auth`, `pm-frontend:1.1-auth` |
| Rejestr / digest | `global.appImageRegistry`, `image.digest` | dev: lokalne; prod: GHCR + `@sha256` |
| Redis | `redis.*` / `redisHA.*` | dev: `redis:7-alpine`; prod: Sentinel |
| TLS | `ingress.tls.*` | dev: off; prod: cert-manager |
| NetworkPolicies | `networkPolicy.enabled` | dev: off; prod: on |
| Actuator | `backend.managementPort` | `9090` |
| Metryki | `metrics.serviceMonitor.*` | dev: off; prod: ServiceMonitor |
| Rate limiting | `backend.rateLimit.*` | 20 req / 60s na IP |
| CORS / WS | `backend.cors.*`, `backend.ws.*` | dev: `*`; prod: domena HTTPS |

## Helm Chart

Chart [`helm/pm/`](../helm/pm/) pakuje zasoby Kubernetes:

- **values.yaml** — domyślne wartości (bez haseł)
- **values-minikube.yaml** — `imagePullPolicy: Never`, `namespace.create: false`
- **values-prod.yaml** — TLS, Barman, Redis HA, digesty GHCR
- **templates/** — backend/frontend deployments, CNPG cluster, redis, ingress, HPA, networkpolicies, servicemonitor, PDB

Secret Postgres **nie jest** w repozytorium — tworzony przed wdrożeniem ([minikube.md](minikube.md) / [production.md](production.md)).

Walidacja: `helm lint`, `helm template`, `helm upgrade --install --dry-run`.

## API i autoryzacja

Specyfikacja OpenAPI: [`backend/openapi.yaml`](../backend/openapi.yaml).

### Endpointy

- `GET /api/rooms` — lista publicznych pokoi
- `POST /api/rooms` — utworzenie pokoju
- `POST /api/rooms/:code/join` — dołączenie
- `GET /api/rooms/:code` — stan pokoju (REST fallback)
- `WS /api/ws/rooms/:code` — push stanu (subscribe + ping/pong)
- `POST /api/rooms/:code/settings` / `/start` / `/stop` / `/answers` / `/vote` / `/next-round` / `/reset` / `/leave`

### JWT (bez konta)

Przy **create/join** backend zwraca `accessToken`. Klient wysyła `Authorization: Bearer <token>` i `{ type: "subscribe", token }` w WebSocket.

| Endpoint | Auth |
|----------|------|
| `GET /api/health` | publiczny (sondy K8s → Actuator `:9090`) |
| `GET/POST /api/rooms`, `POST .../join` | publiczny (wydaje token) |
| Reszta REST + `GET /api/rooms/:code` | Bearer JWT |
| WebSocket subscribe | token w pierwszej wiadomości |

**Rejoin:** nick + kod → nowy token. **Host:** z DB (`is_host`), nie tylko claim JWT.

### Kody HTTP błędów

| Kod | Przyczyny |
|-----|-----------|
| 400 | Walidacja, niedozwolona akcja |
| 401 | Brak/nieważny JWT |
| 403 | Gra trwa, tylko host, join w trakcie gry |
| 404 | Pokój nie istnieje |
| 409 | Optimistic locking (po retry) |
| 429 | Rate limit create/join |

## PostgreSQL (CloudNativePG)

Warstwa persystencji — stan pokoi, graczy, gry. Backend przez JDBC; schemat zarządza Flyway (`ddl-auto=validate`).

### Architektura

**CloudNativePG `Cluster`** (`pm-postgres`, 3 instancje). Backend → serwis **`pm-postgres-rw`**. PDB `maxUnavailable: 1`.

### Migracja StatefulSet → CNPG

PVC `postgres-data-postgres-0` **nie jest** adoptowany.

1. `pg_dump` ze starego `postgres-0`
2. `helm upgrade` z `postgres.mode=cnpg`
3. `kubectl wait ... cluster/pm-postgres`
4. `pg_restore` do `pm-postgres-rw`

### Flyway

Migracje: [`backend/src/main/resources/db/migration/`](../backend/src/main/resources/db/migration/)

| Plik | Opis |
|------|------|
| `V1__initial_schema.sql` | Pełny schemat |
| `V2__players_fk_cascade.sql` | Fix FK CASCADE (brownfield) |
| `V3__rooms_optimistic_lock.sql` | Kolumna `version` |

**Brownfield:** jednorazowo `postgres.flyway.baselineOnMigrate: true` w values-minikube, potem `false`.

**Lokalny test (bez K8s):**

```bash
docker run --rm -d --name pm-pg-test -e POSTGRES_DB=pm -e POSTGRES_USER=pm \
  -e POSTGRES_PASSWORD=pmpass -p 5432:5432 postgres:16
docker run --rm -d --name pm-redis-test -p 6379:6379 redis:7-alpine
cd backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/pm
export SPRING_DATASOURCE_USERNAME=pm SPRING_DATASOURCE_PASSWORD=pmpass
export FLYWAY_BASELINE_ON_MIGRATE=false
./mvnw spring-boot:run
```

### Backupy Barman

Wdrożenie backupów: [production.md → Backupy](production.md#backupy-postgresql-barman).

### Failover

```bash
PRIMARY=$(kubectl get cluster pm-postgres -n pm-app -o jsonpath='{.status.currentPrimary}')
kubectl exec -n pm-app "$PRIMARY" -- \
  psql -U postgres -d pm -c "SELECT application_name, state, sent_lsn, write_lsn FROM pg_stat_replication;"
kubectl delete pod "$PRIMARY" -n pm-app
kubectl wait --for=jsonpath='{.status.phase}'="Cluster in healthy state" \
  cluster/pm-postgres -n pm-app --timeout=120s
```

## Skalowanie i synchronizacja stanu

Stan pokoju synchronizowany przez **WebSocket** (`/api/ws/rooms/{code}`). Hook UI: [`useRoomWebSocket.ts`](../frontend/src/hooks/useRoomWebSocket.ts) (składa `useWebSocketSync`, `useGameActions`, `useGameTimer`). Mutacje przez REST.

### WebSocket + Redis Pub/Sub

Każda replika backendu ma rejestr sesji WS. Po mutacji REST → Redis kanał `room:updates` → push do klientów. Prod: Sentinel ([production.md → Redis HA](production.md#redis-ha)).

Redis: TTL pokoi (`__keyevent@0__:expired`), `notify-keyspace-events Ex`.

### HPA

Skalowanie backendu **tylko po CPU** (70%), min 2 / max 10. Pamięć wyłączona — JVM ma stały footprint RAM.

### Auto-stop rund

`game.roundEndsAt` w PostgreSQL. Scheduler co 1s na pokojach z aktywnym WS:

```sql
UPDATE rooms SET status = 'reviewing'
WHERE code = :code AND status = 'playing' AND round_ends_at <= :now
```

### TTL pokoi

- `POST /api/rooms/{code}/leave` — ostatni gracz kasuje pokój
- Brak WS → TTL Redis: 3 min (lobby) / 10 min (gra)
- Subscribe WS anuluje TTL
- ConfigMap: `APP_ROOM_TTL_LOBBY_SECONDS` (180), `APP_ROOM_TTL_IN_GAME_SECONDS` (600)

## Observability

**Actuator** na porcie **`9090`** (poza ingress): `health`, `info`, `prometheus`.

- `/actuator/health/liveness` — płytka (startup/liveness probe)
- `/actuator/health/readiness` — głęboka (db + redis)
- `/actuator/prometheus` — Micrometer

Probes w [`backend-deployment.yaml`](../helm/pm/templates/backend-deployment.yaml): `startupProbe` do ~150s na JVM/Flyway.

Metryki: `metrics.serviceMonitor.enabled` (prod) lub adnotacje `prometheus.io/*`.

Logi JSON: `backend.logging.structuredFormat: ecs` (prod).

PDB app-tier: [`app-pdb.yaml`](../helm/pm/templates/app-pdb.yaml) — `maxUnavailable: 1`.

## Hardening backendu

- **GlobalExceptionHandler** — spójne `ApiError` (404/403/400/409/500)
- **Optimistic locking** — `@Version` na `Room`, `@Retryable` w `RoomService`
- **Bean Validation** na DTO
- **Rate limiting** — Bucket4j + Redis (create/join, 429)
- **Graceful shutdown** — `terminationGracePeriodSeconds: 40`, preStop sleep 5
- **CORS/WS originy** — konfigurowalne (`*` dev, domena prod)
- **submitAnswers** — tylko `playing`/`reviewing`; w reviewing bez nadpisywania
- **ScoreCalculator** — wydzielona logika punktacji

## Hardening frontendu

- **nginx** — CSP, X-Frame-Options, gzip, cache statyków ([`nginx.conf`](../frontend/nginx.conf))
- **npm ci** — deterministyczny build
- **ApiHttpError** — 401 rejoin+retry, 403 alert ([`api.ts`](../frontend/src/services/api.ts), [`errors.ts`](../frontend/src/services/errors.ts))
- Submit odpowiedzi przy stop + fallback przy transycji `playing` → `reviewing`

## Ograniczenia

- **CNPG PVC** — `helm uninstall` nie usuwa PVC domyślnie
- **Flyway brownfield** — jednorazowo `baselineOnMigrate: true`
- **Pierwszy start backendu** — wolny (JVM + Flyway); `startupProbe` ~150s
- **`helm upgrade`** — używaj `helm upgrade --install` gdy release nie istnieje
