#!/usr/bin/env bash
#
# Generuje zaszyfrowane SealedSecret (*.sealed.yaml) z plaintextu przez kubeseal.
# Pliki *.sealed.yaml są bezpieczne do commitu — szyfrowane publicznym kluczem
# kontrolera sealed-secrets danego klastra. Plaintext nigdy nie trafia do repo.
#
# Wymaga: zainstalowanego kontrolera sealed-secrets w klastrze + CLI kubeseal.
#
# Zmienne (analogicznie do scripts/create-secrets.sh):
#   PG_USER, PG_PASSWORD, PG_DB
#   JWT_SECRET
#   REDIS_PASSWORD
#   NS (domyślnie: pm-app)
set -euo pipefail

if ! command -v kubeseal &>/dev/null; then
  echo "ERROR: kubeseal nie jest zainstalowany." >&2
  echo "Instalacja: https://github.com/bitnami-labs/sealed-secrets#kubeseal" >&2
  exit 1
fi

NS="${NS:-pm-app}"
OUT="$(dirname "$0")"

seal() {
  # seal <out-name> <kubectl create secret ...args>
  local name="$1"; shift
  "$@" -n "$NS" --dry-run=client -o yaml \
    | kubeseal --format yaml > "$OUT/${name}.sealed.yaml"
  echo "==> $OUT/${name}.sealed.yaml"
}

seal backend-secrets \
  kubectl create secret generic backend-secrets \
    --from-literal=JWT_SECRET="${JWT_SECRET:?Ustaw JWT_SECRET}"

seal postgres-credentials \
  kubectl create secret generic postgres-credentials \
    --from-literal=username="${PG_USER:?Ustaw PG_USER}" \
    --from-literal=password="${PG_PASSWORD:?Ustaw PG_PASSWORD}" \
    --from-literal=POSTGRES_USER="${PG_USER}" \
    --from-literal=POSTGRES_PASSWORD="${PG_PASSWORD}" \
    --from-literal=POSTGRES_DB="${PG_DB:-pm}"

seal redis-credentials \
  kubectl create secret generic redis-credentials \
    --from-literal=redis-password="${REDIS_PASSWORD:?Ustaw REDIS_PASSWORD}"

echo "Gotowe. Zacommituj *.sealed.yaml i zaaplikuj: kubectl apply -f $OUT/"
