# Państwa Miasta

Multiplayerowa gra słowna "Państwa Miasta" wdrożona na lokalnym klastrze Kubernetes z wykorzystaniem `minikube` i **Helm**.

## Architektura

```mermaid
flowchart LR
    user[Przegladarka] -->|http://pm.local| ingress[ingress-nginx]
    ingress -->|path /| feSvc[Service<br/>frontend-svc:80]
    ingress -->|path /api| beSvc[Service<br/>backend-svc:3000]
    feSvc --> feDep[Deployment frontend<br/>2 repliki<br/>nginx + Vite build]
    beSvc --> beDep[Deployment backend<br/>2-10 replik HPA<br/>Spring Boot + JPA]
    beDep -->|"jdbc:postgresql://pm-postgres-rw"| pgRw[Service pm-postgres-rw]
    beDep -->|Pub/Sub room:updates| redis[Deployment redis:7-alpine]
    pgRw --> cnpg[CloudNativePG Cluster<br/>pm-postgres x3]
    cnpg --> pvc[(PVC 2Gi x3<br/>storageClass standard)]
```


| Komponent  | Technologia                           | Folder           | 
| ---------- | ------------------------------------- | ---------------- | 
| Frontend   | React 19 + Vite + Tailwind + nginx    | `frontend/`      | 
| Backend    | Spring Boot 4 (Java 21) + Spring Data JPA | `backend/` | 
| Baza       | PostgreSQL 16 (CloudNativePG Cluster, 3 instancje) | `helm/pm/templates/postgres-cluster.yaml` |
| Redis      | redis:7-alpine (Pub/Sub broadcast WS) | `helm/pm/templates/redis-*.yaml` |
| Wdrożenie  | Helm chart `pm`                       | `helm/pm/`       | 
| Ekspozycja | ingress-nginx, path-based             | `helm/pm/templates/ingress.yaml` | 

Frontend komunikuje się z backendem przez **relatywny** prefix `/api` (zob. [`frontend/src/services/api.ts`](frontend/src/services/api.ts)) — ten sam build chodzi za Ingressem niezależnie od hosta.

Stan pokoju (lobby + gra) synchronizowany jest przez **WebSocket** (`/api/ws/rooms/{code}`) — hook [`useRoomWebSocket.ts`](frontend/src/hooks/useRoomWebSocket.ts). Mutacje (start, stop, głosowanie) nadal idą przez REST. Lista publicznych pokoi na stronie głównej nadal używa REST co 3s.

## Struktura 

```
.
├── backend/        # Spring Boot (Java 21) + Spring Data JPA
│   ├── src/main/java/
│   ├── pom.xml
│   ├── Dockerfile
│   └── openapi.yaml
├── frontend/       # React/Vite + nginx
│   ├── src/
│   ├── Dockerfile
│   └── nginx.conf
├── helm/pm/        # Helm chart
│   ├── Chart.yaml
│   ├── values.yaml
│   ├── values-minikube.yaml
│   ├── values-prod.yaml
│   └── templates/  # namespace, cnpg postgres, redis, backend, frontend, ingress, hpa
└── chat_export.json
```

## Wymagania

- `minikube` >= 1.38, `kubectl`, `helm` >= 3.x, Docker
- Lokalnie do dev: JDK 21 + Maven (lub `./mvnw`), Node.js 18+ i npm dla frontendu

Komendy Helm uruchamiaj z katalogu głównego projektu. Chart podawaj jako **`./helm/pm`** (z `./`) — inaczej Helm interpretuje `helm/pm` jako repozytorium `helm` i chart `pm` → błąd `repo helm not found`.

## Uruchomienie w minikube

### 1. Start klastra + addony

```bash
minikube start --memory=4096 --cpus=2 --driver=docker
minikube addons enable ingress
minikube addons enable metrics-server   # wymagane dla HPA i kubectl top
```

### 2. CloudNativePG operator (jednorazowo)

```bash
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm upgrade --install cnpg cnpg/cloudnative-pg -n cnpg-system --create-namespace
```

Na minikube **nie** instaluj pluginu Barman — backupy włączane są tylko w prod (`values-prod.yaml`).

### 3. Build obrazów w demonie minikube

```bash
eval $(minikube -p minikube docker-env --shell bash)
docker build -t pm-backend:3.1 backend/
docker build -t pm-frontend:1.1 frontend/
```

### 4. Secret Postgres (poza chartem — hasła nie trafiają do git)

Secret musi istnieć **przed** `helm install` — CloudNativePG `Cluster` odwołuje się do `postgres-credentials` przy bootstrap.

```bash
kubectl create namespace pm-app --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic postgres-credentials \
  --from-literal=username=pm \
  --from-literal=password=pmpass \
  --from-literal=POSTGRES_USER=pm \
  --from-literal=POSTGRES_PASSWORD=pmpass \
  --from-literal=POSTGRES_DB=pm \
  -n pm-app --dry-run=client -o yaml | kubectl apply -f -
```

Klucze `username`/`password` — wymagane przez CNPG; `POSTGRES_*` — kompatybilność wsteczna.

### 5. Wdrożenie Helm chart

Używaj **`helm upgrade --install`** — tworzy release przy pierwszym uruchomieniu i aktualizuje przy kolejnych.

```bash
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml \
  -f helm/pm/values-minikube.yaml

kubectl wait --for=jsonpath='{.status.phase}'="Cluster in healthy state" \
  cluster/pm-postgres -n pm-app --timeout=300s
kubectl wait --for=condition=Ready pod --all -n pm-app --timeout=180s
kubectl get all,hpa,ingress -n pm-app
helm list -n pm-app
```

Walidacja przed wdrożeniem:

```bash
helm lint ./helm/pm \
  -f helm/pm/values.yaml \
  -f helm/pm/values-minikube.yaml

helm template pm ./helm/pm \
  -f helm/pm/values.yaml \
  -f helm/pm/values-minikube.yaml

helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml \
  -f helm/pm/values-minikube.yaml \
  --dry-run
```

Aktualizacja i rollback:

```bash
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml \
  -f helm/pm/values-minikube.yaml

helm history pm -n pm-app
helm rollback pm 1 -n pm-app
```

### 5. Patch ingress-nginx-controller na LoadBalancer

`minikube tunnel` przypisuje `EXTERNAL-IP` wyłącznie serwisom typu `LoadBalancer`. Patch jest wymagany jednorazowo po każdym świeżym starcie klastra:

```bash
kubectl patch svc ingress-nginx-controller \
  -n ingress-nginx \
  -p '{"spec":{"type":"LoadBalancer"}}'
```

### 6. Wpis w `/etc/hosts`

```bash
echo "127.0.0.1 pm.local" | sudo tee -a /etc/hosts

# Windows: C:\Windows\System32\drivers\etc\hosts
# 127.0.0.1 pm.local
```

### 7. Uruchomienie tunnela

W **osobnym terminalu** (proces musi działać cały czas):

```bash
minikube tunnel
```

Sprawdzenie:

```bash
kubectl get svc -n ingress-nginx ingress-nginx-controller
# EXTERNAL-IP powinno być 127.0.0.1 gdy tunnel działa
```

Jeśli tunnel nie startuje:

```bash
sudo pkill -f "minikube tunnel"    # zabij wiszący proces
minikube tunnel --cleanup          # opcjonalnie
minikube tunnel                    # ponownie w osobnym terminalu (może wymagać sudo)
```

Typowe przyczyny: stary proces tunelu w tle, brak uprawnień sudo do portów 80/443, uruchomienie tunelu w tym samym terminalu co inne komendy (zamiast osobnego okna).

### 8. Weryfikacja end-to-end

```bash
curl http://pm.local/
curl http://pm.local/api/rooms
curl -X POST -H 'Content-Type: application/json' \
     -d '{"nick":"tester","isPublic":true}' \
     http://pm.local/api/rooms
kubectl exec -n pm-app pm-postgres-1 -- psql -U postgres -d pm -c 'SELECT code, status FROM rooms;'
```

Aplikacja w przeglądarce: <http://pm.local>

### Komendy diagnostyczne

```bash
kubectl get all,pvc,ingress,hpa -n pm-app
helm status pm -n pm-app
kubectl logs -n pm-app deploy/backend -f
kubectl top pod -n pm-app
kubectl describe hpa backend-hpa -n pm-app
kubectl port-forward -n pm-app svc/backend-svc 3000:3000
```

### Sprzątanie

```bash
helm uninstall pm -n pm-app
kubectl delete secret postgres-credentials -n pm-app
kubectl delete namespace pm-app
minikube delete
```

### Odtworzenie po restarcie minikube

Po `minikube delete` / restarcie klastra release Helm znika. Pełna sekwencja od nowa: kroki 1 → 2 (CNPG operator) → 4 (namespace + secret) → 5 (`helm upgrade --install`) → tunnel → 8.

## Konfiguracja

Parametry bazowe: [`helm/pm/values.yaml`](helm/pm/values.yaml). Override minikube: [`helm/pm/values-minikube.yaml`](helm/pm/values-minikube.yaml). Prod + Barman: [`helm/pm/values-prod.yaml`](helm/pm/values-prod.yaml).

|                           | Gdzie                                                                                       | Domyślnie                                              |
| --------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| Postgres mode               | `values.yaml` → `postgres.mode`                                                             | `cnpg` (legacy StatefulSet: `legacy`)                  |
| CNPG Cluster                | `values.yaml` → `postgres.cnpg.*`                                                           | `pm-postgres`, 3 instancje, `max_connections: 200`     |
| Flyway baseline (brownfield)| `values-minikube.yaml` → `postgres.flyway.baselineOnMigrate`                                  | `true` jednorazowo, potem `false`                      |
| Hasło Postgres              | Secret `postgres-credentials` (klucze `username`/`password` + `POSTGRES_*`)                 | `pm` / `pmpass` / `pm`                                 |
| Datasource URL              | `configmap.yaml` → `pm.postgres.jdbcUrl`                                                    | `pm-postgres-rw.pm-app.svc...:5432/pm`                 |
| HikariCP pool               | `values.yaml` → `backend.hikari.maximumPoolSize`                                            | 5 (10 podów HPA × 5 = 50 połączeń)                     |
| Rozmiar wolumenu Postgres   | `values.yaml` → `postgres.cnpg.storage`                                                     | 2Gi × 3, `storageClassName: standard`                  |
| Liczba replik frontendu     | `values.yaml` → `frontend.replicas`                                                         | 2                                                      |
| Liczba replik backendu      | `values.yaml` → `backend.replicas` + `hpa.*`                                                | min 2, max 10 (HPA: **tylko CPU** 70%)                 |
| Obrazy Docker               | `values.yaml` → `backend.image`, `frontend.image`                                           | `pm-backend:3.2-auth`, `pm-frontend:1.1-auth`         |
| Rejestr / digest / pull     | `values.yaml` → `global.appImageRegistry`, `image.digest`, `global.imagePullSecrets`        | puste (dev lokalny); prod: GHCR + `@sha256` + `ghcr-pull` |
| imagePullPolicy (minikube)  | `values-minikube.yaml` → `global.imagePullPolicy`                                           | `Never`                                                |
| Routing / host              | `values.yaml` → `ingress.host`                                                             | `pm.local`, `/api` → backend, `/` → frontend           |
| TLS / HTTPS                 | `values.yaml` → `ingress.tls.*` (prod: `values-prod.yaml`)                                  | wyłączone (dev HTTP); prod: cert-manager + `letsencrypt-prod` |
| Redis                       | `values.yaml` → `redis.*` (dev) / `redisHA.*` (prod, Bitnami Sentinel)                       | dev: pojedynczy `redis:7-alpine`; prod: HA Sentinel (3 węzły) |

## Helm 

Chart [`helm/pm/`](helm/pm/) pakuje wszystkie zasoby Kubernetes aplikacji:

- **Chart.yaml** — metadane chartu (`name: pm`, `version: 0.1.0`)
- **values.yaml** — domyślne wartości (bez haseł; tylko `postgres.credentialsSecret`)
- **values-minikube.yaml** — override minikube: `imagePullPolicy: Never`, `namespace.create: false`
- **values-prod.yaml** — prod: Barman backup, `primaryUpdateStrategy: supervised`, wyższe resources, TLS (cert-manager)
- **templates/** — szablony Go Template generujące manifesty

Secret Postgres **nie jest** w repozytorium — tworzony ręcznie przed wdrożeniem. Opcjonalnie `postgres.credentials.create: true` + `--set postgres.credentials.user=... --set postgres.credentials.password=...` tylko na lokalny dev (nie commitować haseł).

Walidacja chartu: `helm lint`, `helm template`, `helm upgrade --install --dry-run`.

## API

Specyfikacja OpenAPI: [`backend/openapi.yaml`](backend/openapi.yaml).

Najważniejsze endpointy:

- `GET /api/rooms` - lista publicznych pokoi w lobby
- `POST /api/rooms` - utworzenie pokoju (`{nick, isPublic}`)
- `POST /api/rooms/:code/join` - dołączenie do pokoju
- `GET /api/rooms/:code` - pełny stan pokoju (REST fallback; główny sync przez WebSocket)
- `WS /api/ws/rooms/:code` - push stanu pokoju (subscribe + ping/pong)
- `POST /api/rooms/:code/settings` / `/start` / `/stop` / `/answers` / `/vote` / `/next-round` / `/reset` / `/leave`

## Uwierzytelnianie (JWT, bez konta)

Przy **create/join** backend zwraca `accessToken` (JWT). Klient wysyła `Authorization: Bearer <token>` na chronionych endpointach i `{ type: "subscribe", token }` w WebSocket.

| Endpoint | Auth |
|----------|------|
| `GET /api/health` | publiczny (probe K8s) |
| `GET /api/rooms`, `POST /api/rooms`, `POST .../join` | publiczny (wydaje token) |
| Reszta REST + `GET /api/rooms/:code` | Bearer JWT |
| WebSocket subscribe | token w pierwszej wiadomości |

**Rejoin:** nick + kod pokoju → nowy token (istniejący gracz, dowolna faza gry). Nowy nick tylko w lobby.

**Host:** autoryzacja z DB (`is_host`), nie tylko claim JWT.

Secret (jednorazowo):

```bash
kubectl create secret generic backend-secrets \
  --from-literal=JWT_SECRET=$(openssl rand -base64 64) \
  -n pm-app --dry-run=client -o yaml | kubectl apply -f -
```

Deploy auth wymaga **atomowego** rebuild backend + frontend (`3.2-auth` / `1.1-auth`).

## TLS / HTTPS (produkcja)

Na produkcji Ingress terminuje TLS, a certyfikat wystawia **cert-manager** (Let's Encrypt, HTTP-01). Lokalny dev (`values-minikube.yaml`) zostaje na czystym HTTP (`tls.enabled: false`).

Włączenie sterowane wartościami `ingress.tls.*` (zob. [`helm/pm/values.yaml`](helm/pm/values.yaml), prod: [`helm/pm/values-prod.yaml`](helm/pm/values-prod.yaml)):

| Klucz | Opis |
|-------|------|
| `ingress.tls.enabled` | dodaje blok `spec.tls`, annotację `cert-manager.io/cluster-issuer` i `ssl-redirect` |
| `ingress.tls.secretName` | nazwa Secreta na certyfikat (tworzony przez cert-manager), domyślnie `pm-tls` |
| `ingress.tls.clusterIssuer` | nazwa `ClusterIssuer`, np. `letsencrypt-prod` |
| `ingress.tls.sslRedirect` | wymuszenie HTTP → HTTPS (`true`) |

### 1. Instalacja cert-manager (jednorazowo)

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm install cert-manager jetstack/cert-manager \
  -n cert-manager --create-namespace --set crds.enabled=true
kubectl get pods -n cert-manager
```

### 2. ClusterIssuery

Przykładowe manifesty (poza chartem) w [`infra/cert-manager/`](infra/cert-manager/). Podmień `email` na właściwy adres, potem zaaplikuj. Najpierw **staging** (test bez ostrych rate-limitów), po sukcesie **prod**:

```bash
kubectl apply -f infra/cert-manager/clusterissuer-staging.yaml
kubectl apply -f infra/cert-manager/clusterissuer-prod.yaml
kubectl get clusterissuer
```

### 3. DNS + deploy prod

Ustaw rekord A domeny (`ingress.host`, domyślnie placeholder `pm.example.com`) na IP `ingress-nginx-controller`:

```bash
kubectl get svc -n ingress-nginx ingress-nginx-controller
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml -f helm/pm/values-prod.yaml
```

cert-manager automatycznie wykryje annotację na Ingressie, przeprowadzi challenge HTTP-01 i zapisze certyfikat do Secreta `pm-tls`.

### 4. Weryfikacja

```bash
kubectl get certificate -n pm-app
curl -I https://pm.example.com/api/health    # 200; http -> 308 redirect na https
```

### Debugowanie certyfikatu

Najczęstszy problem przy Let's Encrypt: challenge wisi w `pending`, bo DNS nie wskazuje na IP Ingressu.

```bash
kubectl get certificate -n pm-app
kubectl describe certificate pm-tls -n pm-app
# Challenge HTTP-01
kubectl get challenge -n pm-app
# Challenge w "pending" => sprawdź DNS i IP Ingressu
nslookup pm.example.com
kubectl get svc -n ingress-nginx ingress-nginx-controller
```

## Container registry (produkcja)

Lokalny dev (minikube) buduje obrazy w demonie Dockera klastra (`imagePullPolicy: Never`, pusty `global.appImageRegistry`). Na produkcji obrazy backend/frontend pochodzą z prywatnego rejestru (przykład: GHCR), są **pinowane po digest** (`@sha256:...`) i pobierane przez `imagePullSecrets`. Konfiguracja w [`helm/pm/values-prod.yaml`](helm/pm/values-prod.yaml):

| Klucz | Opis |
|-------|------|
| `global.appImageRegistry` | prefiks rejestru obrazów aplikacji, np. `ghcr.io/OWNER` (puste = obraz lokalny) |
| `backend.image.digest` / `frontend.image.digest` | `sha256:...`; ustawiony ma priorytet nad `tag` |
| `global.imagePullSecrets` | lista nazw Secretów typu `docker-registry` |

### 1. Build + push do GHCR

```bash
docker build -t ghcr.io/OWNER/pm-backend:3.2-auth backend/
docker build -t ghcr.io/OWNER/pm-frontend:1.1-auth frontend/
docker push ghcr.io/OWNER/pm-backend:3.2-auth
docker push ghcr.io/OWNER/pm-frontend:1.1-auth
```

### 2. Pobranie digestu (do `values-prod.yaml`)

```bash
docker buildx imagetools inspect ghcr.io/OWNER/pm-backend:3.2-auth \
  --format '{{ "{{json .Manifest.Digest}}" }}'
# wynik: "sha256:..." -> wpisz jako backend.image.digest (repository zostaje pm-backend)
```

### 3. imagePullSecret (jednorazowo)

```bash
kubectl create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io \
  --docker-username=GH_USER \
  --docker-password=GH_TOKEN \
  -n pm-app
```

Następnie deploy: `helm upgrade --install pm ./helm/pm -n pm-app -f helm/pm/values.yaml -f helm/pm/values-prod.yaml`.

## Redis HA (produkcja)

Dev używa pojedynczego `redis:7-alpine` (`redis.enabled: true`). Produkcja używa subchartu **Bitnami Redis** w trybie replication + **Sentinel** (`redisHA.enabled: true`, `redis.enabled: false` w [`values-prod.yaml`](helm/pm/values-prod.yaml)): 3 węzły (1 master + 2 repliki), automatyczny failover, AOF persistence, PDB i auth. Tryby wykluczają się wzajemnie.

Backend łączy się przez Sentinel wyłącznie konfiguracją (`spring.data.redis.sentinel.*` ze zmiennych env) — Lettuce jest już w zależnościach, więc **bez zmian w kodzie i bez rebuildu**. Zachowane: Pub/Sub `room:updates`, keyspace `__keyevent@0__:expired` (`notify-keyspace-events Ex` w `commonConfiguration`), Lua/liczniki (zapisy routowane na master).

| Klucz | Opis |
|-------|------|
| `redisHA.sentinel.masterSet` | nazwa zbioru mastera (`mymaster`) |
| `redisHA.sentinel.quorum` | quorum Sentinela (`2`) |
| `redisHA.replica.replicaCount` | liczba węzłów (`3`) |
| `redisHA.replica.persistence` / `master.persistence` | AOF PVC (`1Gi`, `storageClass: standard`) |
| `redisHA.replica.pdb` | PodDisruptionBudget (`maxUnavailable: 1`) |
| `redisHA.auth.existingSecret` | sekret z hasłem (`redis-credentials`, klucz `redis-password`) |

### 1. Sekret z hasłem (jednorazowo)

```bash
kubectl create secret generic redis-credentials \
  --from-literal=redis-password=$(openssl rand -base64 24) \
  -n pm-app --dry-run=client -o yaml | kubectl apply -f -
```

### 2. Pobranie subchartu i deploy

```bash
helm dependency build helm/pm          # pobiera Bitnami redis do helm/pm/charts/
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml -f helm/pm/values-prod.yaml
kubectl get statefulset,pdb,svc -n pm-app -l app.kubernetes.io/name=redis
```

### 3. Test failover

```bash
# usuń pod-mastera; Sentinel wypromuje replikę, backend reconnectuje przez Sentinel
kubectl delete pod pm-redis-node-0 -n pm-app
kubectl logs -n pm-app deploy/backend -f | grep -i "sentinel\|master"
```

> Uwaga o obrazach Bitnami: od 2025 część publicznych obrazów Bitnami przeniesiono (model "Bitnami Secure Images"). Jeśli pull `bitnami/redis` zawiedzie, nadpisz `redisHA.image.registry`/`redisHA.image.repository` (np. `bitnamilegacy`) lub własny mirror.

## PostgreSQL (CloudNativePG)

Baza działa jako **CloudNativePG `Cluster`** (`pm-postgres`, 3 instancje = quorum). Backend łączy się przez serwis **`pm-postgres-rw`**. PDB (`maxUnavailable: 1`) chroni przed jednoczesnym evictem wielu instancji przy `kubectl drain`.

### Migracja ze starego StatefulSet

PVC `postgres-data-postgres-0` **nie jest** adoptowany przez CNPG.

1. `pg_dump` ze starego `postgres-0` (przed usunięciem StatefulSet)
2. `helm upgrade` z `postgres.mode=cnpg`
3. Poczekaj: `kubectl wait --for=jsonpath='{.status.phase}'="Cluster in healthy state" cluster/pm-postgres -n pm-app --timeout=300s`
4. `pg_restore` do `pm-postgres-rw`

### Flyway (migracje schematu)

Schemat zarządza **Flyway** przy starcie backendu (`spring-boot-starter-flyway` + `flyway-database-postgresql`).
Hibernate: `ddl-auto=validate` — nie modyfikuje schematu.

Migracje: [`backend/src/main/resources/db/migration/`](backend/src/main/resources/db/migration/)

| Plik | Opis |
|------|------|
| `V1__initial_schema.sql` | Pełny schemat (świeże bazy) |
| `V2__players_fk_cascade.sql` | Idempotentny fix FK CASCADE (brownfield) |

**Nowa migracja:** dodaj `V3__opis.sql`, rebuild backendu, restart deploymentu.

**Brownfield** (baza CNPG utworzona przez stary `ddl-auto=update`, bez `flyway_schema_history`):

1. W [`values-minikube.yaml`](helm/pm/values-minikube.yaml): `postgres.flyway.baselineOnMigrate: true`
2. `helm upgrade` + restart backendu → baseline v1 + uruchomienie V2
3. Po sukcesie ustaw z powrotem `baselineOnMigrate: false`

**Lokalny test (przed docker build):**

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

### Test failover (lab)

```bash
PRIMARY=$(kubectl get cluster pm-postgres -n pm-app -o jsonpath='{.status.currentPrimary}')
kubectl exec -n pm-app "$PRIMARY" -- \
  psql -U postgres -d pm -c "SELECT application_name, state, sent_lsn, write_lsn FROM pg_stat_replication;"
kubectl delete pod "$PRIMARY" -n pm-app
kubectl wait --for=jsonpath='{.status.phase}'="Cluster in healthy state" \
  cluster/pm-postgres -n pm-app --timeout=120s
kubectl get cluster pm-postgres -n pm-app -o jsonpath='New primary: {.status.currentPrimary}{"\n"}'
curl http://pm.local/api/rooms
```

### Prod: backup Barman PITR

```bash
helm upgrade --install cnpg-plugin-barman-cloud cnpg/plugin-barman-cloud -n cnpg-system
kubectl create secret generic pm-postgres-s3-credentials \
  --from-literal=ACCESS_KEY_ID=... --from-literal=SECRET_ACCESS_KEY=... -n pm-app
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml -f helm/pm/values-prod.yaml \
  --set postgres.cnpg.backup.destinationPath=s3://YOUR-BUCKET/pm-postgres/
```

## Ograniczenia

- **CloudNativePG PVC**: każda instancja ma własny PVC; `helm uninstall` nie usuwa PVC domyślnie.
- **Flyway brownfield**: jednorazowo `postgres.flyway.baselineOnMigrate: true` w values-minikube; potem wyłącz.

- **Pierwszy start backendu** jest wolniejszy (JVM + Hibernate schema), stąd podwyższone `initialDelaySeconds` w sondach.
- **`helm upgrade` bez release**: samo `helm upgrade` failuje, gdy release nie istnieje — używaj `helm upgrade --install`.

## Architektura auto-stop i WebSocket

Czas trwania rundy jest przechowywany jako `game.roundEndsAt` (Unix ms) w PostgreSQL. Scheduler co 1s sprawdza **tylko pokoje z aktywnymi subskrypcjami WebSocket** na danym podzie i wykonuje atomowy `UPDATE`:

```sql
UPDATE rooms SET status = 'reviewing'
WHERE code = :code AND status = 'playing' AND round_ends_at <= :now
```

Dzięki temu backend można skalować (HPA 2–10 replik). Po każdej mutacji REST backend publikuje kod pokoju do **Redis Pub/Sub** (`room:updates`); każda replika pushuje świeży stan do swoich klientów WS. `mainTimeLeft` liczone dynamicznie przy pushu; frontend odlicza lokalnie między pushami.

**Redis** — dev: 1 replika (`redis.enabled`) wystarczy na lab; restart Redisa zrywa subskrypcje Pub/Sub (klienci WS reconnectują). Prod: HA z Sentinelem (zob. sekcja [Redis HA](#redis-ha-produkcja)).

### Cleanup pokoi (TTL) i wyjście

- **`POST /api/rooms/{code}/leave`** — gracz opuszcza pokój w dowolnej fazie; ostatni gracz kasuje pokój od razu.
- Gdy **nikt nie ma aktywnego WebSocket** w pokoju, Redis ustawia TTL: **3 min** (lobby/finished) lub **10 min** (playing/reviewing) — czas na reconnect po zamknięciu karty / F5.
- Ponowne **subscribe WS** anuluje TTL; po wygaśnięciu klucza pokój jest usuwany z Postgres.
- Konfiguracja: `APP_ROOM_TTL_LOBBY_SECONDS`, `APP_ROOM_TTL_IN_GAME_SECONDS` w ConfigMap (domyślnie 180 / 600).
- Redis wymaga `--notify-keyspace-events Ex` (w chart Helm). Event `expired` może przyjść z lekkim opóźnieniem (lazy expiration).

Po zmianach backendu wymagany rebuild obrazu:

```bash
eval $(minikube docker-env)
docker build -t pm-backend:3.0 backend/
docker build -t pm-frontend:1.0 frontend/   # po zmianach frontendu
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml -f helm/pm/values-minikube.yaml
```

## HPA (Horizontal Pod Autoscaler)

Włączony w chart (`hpa.enabled: true`). Wymaga addonu `metrics-server`.

Skaluje backend **tylko po CPU** (`hpa.cpu.enabled: true`, cel 70%). Metryka pamięci jest **wyłączona** (`hpa.memory.enabled: false`) — JVM (Spring Boot) utrzymuje stały, wysoki footprint RAM niezależnie od ruchu; przy włączonej metryce pamięci HPA fałszywie skalował w górę do `maxReplicas` nawet przy `cpu: 1%`.

```bash
kubectl get hpa backend-hpa -n pm-app -w
kubectl top pod -n pm-app -l app=backend

# test obciążeniowy (skalowanie w górę po CPU)
kubectl run loadgen --image=busybox:1.36 --restart=Never -n pm-app -- \
  sh -c 'while true; do wget -q -O- http://backend-svc:3000/api/rooms; done'
kubectl delete pod loadgen -n pm-app   # po teście — HPA stopniowo wraca do min. 2
```

HPA pokazuje `cpu: <unknown>/70%` przez 1–2 minuty po starcie — metrics-server potrzebuje czasu na zebranie metryk z nowych podów.
