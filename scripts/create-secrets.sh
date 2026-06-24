#!/usr/bin/env bash
#
# Bootstrap sekretów Kubernetes dla aplikacji pm (ścieżka dev / lab).
# Idempotentny: każdy sekret jest tworzony przez `kubectl apply` z dry-run,
# więc ponowne uruchomienie aktualizuje wartości bez błędów "already exists".
#
# Na produkcji preferuj Sealed Secrets (zob. infra/sealed-secrets/) zamiast tego skryptu.
#
# Wymagane zmienne:
#   PG_USER, PG_PASSWORD            - poświadczenia Postgres
# Opcjonalne:
#   PG_DB           (domyślnie: pm)
#   JWT_SECRET      (domyślnie: losowy 64-bajtowy base64)
#   NS             (domyślnie: pm-app)
#   REDIS_AUTH=true + REDIS_PASSWORD       - sekret redis-credentials (tylko gdy Redis ma auth, np. redisHA)
#   GITHUB_TOKEN + GITHUB_USER             - imagePullSecret ghcr-pull
#   S3_ACCESS_KEY_ID + S3_SECRET_ACCESS_KEY - poświadczenia backupu Barman (CNPG)
set -euo pipefail

NS="${NS:-pm-app}"

echo "==> postgres-credentials"
kubectl create secret generic postgres-credentials \
  --from-literal=username="${PG_USER:?Ustaw PG_USER}" \
  --from-literal=password="${PG_PASSWORD:?Ustaw PG_PASSWORD}" \
  --from-literal=POSTGRES_USER="${PG_USER}" \
  --from-literal=POSTGRES_PASSWORD="${PG_PASSWORD}" \
  --from-literal=POSTGRES_DB="${PG_DB:-pm}" \
  -n "$NS" --dry-run=client -o yaml | kubectl apply -f -

echo "==> backend-secrets (JWT)"
kubectl create secret generic backend-secrets \
  --from-literal=JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 64 | tr -d '\n')}" \
  -n "$NS" --dry-run=client -o yaml | kubectl apply -f -

# redis-credentials tylko gdy Redis ma auth (prod/redisHA); w dev (prosty Redis bez auth) niepotrzebne
if [ "${REDIS_AUTH:-false}" = "true" ]; then
  echo "==> redis-credentials"
  kubectl create secret generic redis-credentials \
    --from-literal=redis-password="${REDIS_PASSWORD:-$(openssl rand -base64 24)}" \
    -n "$NS" --dry-run=client -o yaml | kubectl apply -f -
fi

if [ -n "${GITHUB_TOKEN:-}" ]; then
  echo "==> ghcr-pull"
  kubectl create secret docker-registry ghcr-pull \
    --docker-server=ghcr.io \
    --docker-username="${GITHUB_USER:?Ustaw GITHUB_USER}" \
    --docker-password="$GITHUB_TOKEN" \
    -n "$NS" --dry-run=client -o yaml | kubectl apply -f -
fi

if [ -n "${S3_ACCESS_KEY_ID:-}" ]; then
  echo "==> pm-postgres-s3-credentials"
  kubectl create secret generic pm-postgres-s3-credentials \
    --from-literal=ACCESS_KEY_ID="$S3_ACCESS_KEY_ID" \
    --from-literal=SECRET_ACCESS_KEY="${S3_SECRET_ACCESS_KEY:?Ustaw S3_SECRET_ACCESS_KEY}" \
    -n "$NS" --dry-run=client -o yaml | kubectl apply -f -
fi

echo "Gotowe."
