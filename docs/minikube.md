# Uruchomienie lokalne (Minikube)

Runbook dla deweloperów i osób testujących aplikację na lokalnym klastrze Minikube.

← [README](../README.md) · Szczegóły techniczne: [technical-details.md](technical-details.md) · Produkcja: [production.md](production.md)

## Spis treści

- [Wymagania](#wymagania)
- [Start klastra](#start-klastra)
- [Build obrazów](#build-obrazów)
- [Sekrety](#sekrety)
- [Wdrożenie Helm](#wdrożenie-helm)
- [Ingress, hosts i tunnel](#ingress-hosts-i-tunnel)
- [Weryfikacja](#weryfikacja)
- [Rebuild po zmianach](#rebuild-po-zmianach)
- [Sprzątanie](#sprzątanie)
- [Diagnostyka](#diagnostyka)

## Wymagania

- Docker
- Minikube >= 1.38
- Helm >= 3.x
- kubectl
- Lokalnie do dev (opcjonalnie): JDK 21 + Maven (lub `./mvnw`), Node.js 20 i npm

Komendy Helm uruchamiaj z **katalogu głównego projektu**. Chart podawaj jako **`./helm/pm`** (z `./`) — inaczej Helm interpretuje `helm/pm` jako repozytorium `helm` i chart `pm` → błąd `repo helm not found`.

Na minikube **nie** instaluj pluginu Barman — backupy włączane są tylko w prod ([production.md](production.md)).

## Start klastra

```bash
minikube start --memory=4096 --cpus=2 --driver=docker
minikube addons enable ingress
minikube addons enable metrics-server   # wymagane dla HPA i kubectl top
```

### CloudNativePG operator (jednorazowo)

```bash
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm upgrade --install cnpg cnpg/cloudnative-pg -n cnpg-system --create-namespace
```

## Build obrazów

Obrazy buduj w **demonie Dockera minikube** (nie w domyślnym Dockerze hosta), z tagami zgodnymi z [`helm/pm/values.yaml`](../helm/pm/values.yaml):

```bash
eval $(minikube -p minikube docker-env --shell bash)
docker build -t pm-backend:3.2-auth backend/
docker build -t pm-frontend:1.1-auth frontend/
```

## Sekrety

Secret Postgres musi istnieć **przed** `helm install` — CloudNativePG `Cluster` odwołuje się do `postgres-credentials` przy bootstrap.

### Ręcznie (kubectl)

```bash
kubectl create namespace pm-app --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic postgres-credentials \
  --from-literal=username=pm \
  --from-literal=password=pmpass \
  --from-literal=POSTGRES_USER=pm \
  --from-literal=POSTGRES_PASSWORD=pmpass \
  --from-literal=POSTGRES_DB=pm \
  -n pm-app --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic backend-secrets \
  --from-literal=JWT_SECRET=$(openssl rand -base64 64) \
  -n pm-app --dry-run=client -o yaml | kubectl apply -f -
```

Klucze `username`/`password` — wymagane przez CNPG; `POSTGRES_*` — kompatybilność wsteczna.

### Skrypt bootstrap (zalecane)

[`scripts/create-secrets.sh`](../scripts/create-secrets.sh) tworzy sekrety idempotentnie:

```bash
PG_USER=pm PG_PASSWORD='pmpass' ./scripts/create-secrets.sh
```

Na produkcji używaj Sealed Secrets — zob. [production.md → Sekrety](production.md#sekrety).

## Wdrożenie Helm

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

## Ingress, hosts i tunnel

`minikube tunnel` przypisuje `EXTERNAL-IP` wyłącznie serwisom typu `LoadBalancer`. Patch jest wymagany jednorazowo po każdym świeżym starcie klastra:

```bash
kubectl patch svc ingress-nginx-controller \
  -n ingress-nginx \
  -p '{"spec":{"type":"LoadBalancer"}}'
```

Wpis w `/etc/hosts`:

```bash
echo "127.0.0.1 pm.local" | sudo tee -a /etc/hosts

# Windows: C:\Windows\System32\drivers\etc\hosts
# 127.0.0.1 pm.local
```

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
sudo pkill -f "minikube tunnel"
minikube tunnel --cleanup          # opcjonalnie
minikube tunnel                    # ponownie w osobnym terminalu (może wymagać sudo)
```

Typowe przyczyny: stary proces tunelu w tle, brak uprawnień sudo do portów 80/443, uruchomienie tunelu w tym samym terminalu co inne komendy.

## Weryfikacja

```bash
curl http://pm.local/
curl http://pm.local/api/rooms
curl -X POST -H 'Content-Type: application/json' \
     -d '{"nick":"tester","isPublic":true}' \
     http://pm.local/api/rooms
kubectl exec -n pm-app pm-postgres-1 -- psql -U postgres -d pm -c 'SELECT code, status FROM rooms;'
```

Aplikacja w przeglądarce: <http://pm.local>

## Rebuild po zmianach

```bash
eval $(minikube -p minikube docker-env --shell bash)
docker build -t pm-backend:3.2-auth backend/
docker build -t pm-frontend:1.1-auth frontend/

helm upgrade --install pm ./helm/pm -n pm-app \
  -f helm/pm/values.yaml -f helm/pm/values-minikube.yaml

kubectl rollout restart deployment/frontend deployment/backend -n pm-app
kubectl rollout status deployment/frontend -n pm-app
```

Po rebuildzie wymuś restart podów — przy tym samym tagu Kubernetes nie przeładuje obrazu (`imagePullPolicy: Never`).

Weryfikacja nagłówków nginx po zmianach frontendu:

```bash
curl -sI http://pm.local/ | grep -iE "content-security|x-frame|cache-control"
```

## Sprzątanie

```bash
helm uninstall pm -n pm-app
kubectl delete secret postgres-credentials -n pm-app
kubectl delete namespace pm-app
minikube delete
```

Po `minikube delete` / restarcie klastra release Helm znika. Pełna sekwencja od nowa: start klastra → CNPG operator → secrety → `helm upgrade --install` → patch Ingress → hosts → tunnel → weryfikacja.

## Diagnostyka

### Kubernetes

```bash
kubectl get all,pvc,ingress,hpa -n pm-app
kubectl logs -n pm-app deploy/backend -f
kubectl top pod -n pm-app
kubectl port-forward -n pm-app svc/backend-svc 3000:3000   # API aplikacji
kubectl port-forward -n pm-app deploy/backend 9090:9090    # Actuator (health, prometheus)
```

### Helm

```bash
helm status pm -n pm-app
helm list -n pm-app
helm history pm -n pm-app
```

### HPA

```bash
kubectl describe hpa backend-hpa -n pm-app
kubectl get hpa backend-hpa -n pm-app -w
kubectl top pod -n pm-app -l app=backend

# test obciążeniowy (skalowanie w górę po CPU)
kubectl run loadgen --image=busybox:1.36 --restart=Never -n pm-app -- \
  sh -c 'while true; do wget -q -O- http://backend-svc:3000/api/rooms; done'
kubectl delete pod loadgen -n pm-app   # po teście — HPA stopniowo wraca do min. 2
```

### PostgreSQL

```bash
kubectl get cluster pm-postgres -n pm-app
kubectl exec -n pm-app pm-postgres-1 -- psql -U postgres -d pm -c 'SELECT code, status FROM rooms;'
PRIMARY=$(kubectl get cluster pm-postgres -n pm-app -o jsonpath='{.status.currentPrimary}')
kubectl exec -n pm-app "$PRIMARY" -- \
  psql -U postgres -d pm -c "SELECT application_name, state FROM pg_stat_replication;"
```

Więcej o CNPG i Flyway: [technical-details.md → PostgreSQL](technical-details.md#postgresql-cloudnativepg).
