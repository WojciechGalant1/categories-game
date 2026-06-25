# Produkcja

Runbook wdrożenia na klaster produkcyjny: TLS, rejestr obrazów, Redis HA, bezpieczeństwo i sekrety.

← [README](../README.md) · Lokalnie: [minikube.md](minikube.md) · Szczegóły techniczne: [technical-details.md](technical-details.md)

## Spis treści

- [TLS / cert-manager](#tls--cert-manager)
- [Container Registry (GHCR)](#container-registry-ghcr)
- [Redis HA](#redis-ha)
- [Bezpieczeństwo](#bezpieczeństwo-networkpolicies--pod-security--non-root)
- [Sekrety](#sekrety)
- [Backupy PostgreSQL (Barman)](#backupy-postgresql-barman)
- [Diagnostyka](#diagnostyka)

Założenie: masz działający klaster Kubernetes z ingress-nginx. Instrukcja Minikube: [minikube.md](minikube.md).

## TLS / cert-manager

Na produkcji Ingress terminuje TLS, a certyfikat wystawia **cert-manager** (Let's Encrypt, HTTP-01). Lokalny dev (`values-minikube.yaml`) zostaje na czystym HTTP (`tls.enabled: false`).

Włączenie sterowane wartościami `ingress.tls.*` (zob. [`helm/pm/values.yaml`](../helm/pm/values.yaml), prod: [`helm/pm/values-prod.yaml`](../helm/pm/values-prod.yaml)):

| Klucz | Opis |
|-------|------|
| `ingress.tls.enabled` | dodaje blok `spec.tls`, annotację `cert-manager.io/cluster-issuer` i `ssl-redirect` |
| `ingress.tls.secretName` | nazwa Secreta na certyfikat (tworzony przez cert-manager), domyślnie `pm-tls` |
| `ingress.tls.clusterIssuer` | nazwa `ClusterIssuer`, np. `letsencrypt-prod` |
| `ingress.tls.sslRedirect` | wymuszenie HTTP → HTTPS (`true`) |

Instalacja cert-manager (jednorazowo):

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm install cert-manager jetstack/cert-manager \
  -n cert-manager --create-namespace --set crds.enabled=true
kubectl get pods -n cert-manager
```

ClusterIssuery — manifesty w [`infra/cert-manager/`](../infra/cert-manager/). Podmień `email`, potem zaaplikuj. Najpierw **staging**, po sukcesie **prod**:

```bash
kubectl apply -f infra/cert-manager/clusterissuer-staging.yaml
kubectl apply -f infra/cert-manager/clusterissuer-prod.yaml
kubectl get clusterissuer
```

DNS + deploy prod:

```bash
kubectl get svc -n ingress-nginx ingress-nginx-controller
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml -f helm/pm/values-prod.yaml
```

cert-manager automatycznie wykryje annotację na Ingressie, przeprowadzi challenge HTTP-01 i zapisze certyfikat do Secreta `pm-tls`.

Weryfikacja:

```bash
kubectl get certificate -n pm-app
curl -I https://pm.example.com/api/health    # 200; http -> 308 redirect na https
```

## Container Registry (GHCR)

Na produkcji obrazy backend/frontend pochodzą z prywatnego rejestru (przykład: GHCR), są **pinowane po digest** (`@sha256:...`) i pobierane przez `imagePullSecrets`. Konfiguracja w [`helm/pm/values-prod.yaml`](../helm/pm/values-prod.yaml):

| Klucz | Opis |
|-------|------|
| `global.appImageRegistry` | prefiks rejestru obrazów aplikacji, np. `ghcr.io/OWNER` (puste = obraz lokalny) |
| `backend.image.digest` / `frontend.image.digest` | `sha256:...`; ustawiony ma priorytet nad `tag` |
| `global.imagePullSecrets` | lista nazw Secretów typu `docker-registry` |

Build + push:

```bash
docker build -t ghcr.io/OWNER/pm-backend:3.2-auth backend/
docker build -t ghcr.io/OWNER/pm-frontend:1.1-auth frontend/
docker push ghcr.io/OWNER/pm-backend:3.2-auth
docker push ghcr.io/OWNER/pm-frontend:1.1-auth
```

Pobranie digestu (do `values-prod.yaml`):

```bash
docker buildx imagetools inspect ghcr.io/OWNER/pm-backend:3.2-auth \
  --format '{{ "{{json .Manifest.Digest}}" }}'
docker buildx imagetools inspect ghcr.io/OWNER/pm-frontend:1.1-auth \
  --format '{{ "{{json .Manifest.Digest}}" }}'
# wynik: "sha256:..." -> wpisz jako backend.image.digest / frontend.image.digest
```

Po aktualizacji digestów:

```bash
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml -f helm/pm/values-prod.yaml
kubectl rollout status deployment/backend deployment/frontend -n pm-app
```

imagePullSecret (jednorazowo):

```bash
kubectl create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io \
  --docker-username=GH_USER \
  --docker-password=GH_TOKEN \
  -n pm-app
```

## Redis HA

Dev używa pojedynczego `redis:7-alpine` (`redis.enabled: true`). Produkcja używa subchartu **Bitnami Redis** w trybie replication + **Sentinel** (`redisHA.enabled: true`, `redis.enabled: false` w [`values-prod.yaml`](../helm/pm/values-prod.yaml)): 3 węzły (1 master + 2 repliki), automatyczny failover, AOF persistence, PDB i auth.

Backend łączy się przez Sentinel wyłącznie konfiguracją (`spring.data.redis.sentinel.*` ze zmiennych env) — bez zmian w kodzie. Zachowane: Pub/Sub `room:updates`, keyspace `__keyevent@0__:expired`.

| Klucz | Opis |
|-------|------|
| `redisHA.sentinel.masterSet` | nazwa zbioru mastera (`mymaster`) |
| `redisHA.sentinel.quorum` | quorum Sentinela (`2`) |
| `redisHA.replica.replicaCount` | liczba węzłów (`3`) |
| `redisHA.replica.persistence` / `master.persistence` | AOF PVC (`1Gi`, `storageClass: standard`) |
| `redisHA.replica.pdb` | PodDisruptionBudget (`maxUnavailable: 1`) |
| `redisHA.auth.existingSecret` | sekret z hasłem (`redis-credentials`, klucz `redis-password`) |

Sekret z hasłem (jednorazowo):

```bash
kubectl create secret generic redis-credentials \
  --from-literal=redis-password=$(openssl rand -base64 24) \
  -n pm-app --dry-run=client -o yaml | kubectl apply -f -
```

Pobranie subchartu i deploy:

```bash
helm dependency build helm/pm
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml -f helm/pm/values-prod.yaml
kubectl get statefulset,pdb,svc -n pm-app -l app.kubernetes.io/name=redis
```

Test failover:

```bash
kubectl delete pod pm-redis-node-0 -n pm-app
kubectl logs -n pm-app deploy/backend -f | grep -i "sentinel\|master"
```

> Uwaga o obrazach Bitnami: od 2025 część publicznych obrazów Bitnami przeniesiono (model "Bitnami Secure Images"). Jeśli pull `bitnami/redis` zawiedzie, nadpisz `redisHA.image.registry`/`redisHA.image.repository` (np. `bitnamilegacy`) lub własny mirror.

## Bezpieczeństwo: NetworkPolicies + Pod Security + non-root

**Obrazy non-root.** Backend (`eclipse-temurin`) — `USER 10001`; frontend — `nginxinc/nginx-unprivileged` na porcie **8080**. Po zmianie obrazów przebuduj i odśwież digesty w `values-prod.yaml`.

```bash
eval $(minikube -p minikube docker-env --shell bash)   # tylko minikube
docker build -t pm-backend:3.2-auth backend/
docker build -t pm-frontend:1.1-auth frontend/
# prod (GHCR) — po push odczytaj digest
docker buildx imagetools inspect ghcr.io/OWNER/pm-frontend:1.1-auth --format '{{ "{{" }}.Manifest.Digest{{ "}}" }}'
```

**securityContext (restricted).** Non-root, `seccompProfile: RuntimeDefault`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`. Backend: `readOnlyRootFilesystem: true` + `emptyDir` na `/tmp`.

**Pod Security Admission (PSA).** Namespace z etykietami `pod-security.kubernetes.io/{enforce,warn,audit}`. Domyślnie `enforce: baseline`, `restricted` jako warn/audit. Gdy `namespace.create: false` (minikube), etykiety ręcznie:

```bash
kubectl label ns pm-app \
  pod-security.kubernetes.io/enforce=baseline \
  pod-security.kubernetes.io/warn=restricted \
  pod-security.kubernetes.io/audit=restricted --overwrite
```

**NetworkPolicies (default-deny + allow-list).** Włączane `networkPolicy.enabled: true` (prod). Otwarte tylko: DNS, ingress→frontend/backend, backend→Postgres/Redis, replikacja CNPG/Redis.

> Minikube domyślnie **nie** egzekwuje NetworkPolicy — użyj `minikube start --cni=calico` do testów polityk.

Więcej o hardeningu API i frontendu: [technical-details.md](technical-details.md).

## Sekrety

Aplikacja używa: `postgres-credentials`, `backend-secrets` (`JWT_SECRET`), `redis-credentials` (Redis HA), `ghcr-pull`, `pm-postgres-s3-credentials` (Barman). Żaden plaintext nie trafia do repo.

### Fail-fast guard

[`ProductionSecretsGuard`](../backend/src/main/java/com/example/panstwamiasta/config/ProductionSecretsGuard.java) aktywny pod profilem **`prod`** (`backend.springProfile: prod`). Przerywa start, jeśli wykryje domyślny JWT lub hasło Postgres (`pmpass`, `postgres`). Dev/minikube — guard nieaktywny.

### Sealed Secrets (GitOps)

```bash
# 1. Kontroler w klastrze (jednorazowo)
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system

# 2. CLI kubeseal: https://github.com/bitnami-labs/sealed-secrets#kubeseal

# 3. Wygeneruj zaszyfrowane manifesty (plaintext z env, nie z repo)
JWT_SECRET='...' PG_USER=pm PG_PASSWORD='...' REDIS_PASSWORD='...' \
  ./infra/sealed-secrets/seal.sh

# 4. Zacommituj i zaaplikuj
kubectl apply -f infra/sealed-secrets/
```

Dev/lab: [`scripts/create-secrets.sh`](../scripts/create-secrets.sh) — zob. [minikube.md → Sekrety](minikube.md#sekrety).

## Backupy PostgreSQL (Barman)

```bash
helm upgrade --install cnpg-plugin-barman-cloud cnpg/plugin-barman-cloud -n cnpg-system
kubectl create secret generic pm-postgres-s3-credentials \
  --from-literal=ACCESS_KEY_ID=... --from-literal=SECRET_ACCESS_KEY=... -n pm-app
helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml -f helm/pm/values-prod.yaml \
  --set postgres.cnpg.backup.destinationPath=s3://YOUR-BUCKET/pm-postgres/
```

Architektura CNPG i failover: [technical-details.md → PostgreSQL](technical-details.md#postgresql-cloudnativepg).

## Diagnostyka

### TLS

Najczęstszy problem: challenge wisi w `pending`, bo DNS nie wskazuje na IP Ingressu.

```bash
kubectl get certificate -n pm-app
kubectl describe certificate pm-tls -n pm-app
kubectl get challenge -n pm-app
nslookup pm.example.com
kubectl get svc -n ingress-nginx ingress-nginx-controller
```

### Kubernetes / Helm / HPA / PostgreSQL

Zob. [minikube.md → Diagnostyka](minikube.md#diagnostyka) — te same komendy `kubectl`/`helm` działają na prod (zamień `http://pm.local` na domenę prod).
