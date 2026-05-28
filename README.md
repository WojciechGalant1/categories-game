# Państwa Miasta

Multiplayerowa gra słowna "Państwa Miasta" wdrożona na lokalnym klastrze Kubernetes z wykorzystaniem `minikube`. Projekt zbudowany według wzorców z Laboratoriów 2-5 oraz 8 (`lab/`).

## Architektura

```mermaid
flowchart LR
    user[Przegladarka] -->|http://localhost:8080| ingress[ingress-nginx]
    ingress -->|path /| feSvc[Service<br/>frontend-svc:80]
    ingress -->|path /api| beSvc[Service<br/>backend-svc:3000]
    feSvc --> feDep[Deployment frontend<br/>2 repliki<br/>nginx + Vite build]
    beSvc --> beDep[Deployment backend<br/>Express + MongoDB driver]
    beDep -->|"mongodb://...@mongo-0.mongo"| mongoHl[Headless Service<br/>mongo clusterIP:None]
    mongoHl --> mongoSs[StatefulSet mongo-0<br/>image mongo:6]
    mongoSs --> pvc[(PVC 2Gi<br/>storageClass standard)]
```

Wszystko żyje w namespace `stop-app`.

| Komponent  | Technologia                           | Folder           | 
| ---------- | ------------------------------------- | ---------------- | 
| Frontend   | React 19 + Vite + Tailwind + nginx    | `frontend/`      | 
| Backend    | Express 5 + driver `mongodb`          | `backendTest/`   | 
| Baza       | MongoDB 6 (StatefulSet + PVC)         | -                | 
| Ekspozycja | ingress-nginx, path-based             | `k8s/40-ingress` | 

Frontend komunikuje się z backendem przez **relatywny** prefix `/api` (zob. [`frontend/src/services/api.ts`](frontend/src/services/api.ts)) — ten sam build chodzi za Ingressem niezależnie od hosta.

## Struktura 

```
.
├── backendTest/        # Express + Mongo
│   ├── index.js
│   ├── package.json
│   ├── Dockerfile
│   └── openapi.yaml    # specyfikacja API
├── frontend/           # React/Vite + nginx
│   ├── src/
│   ├── Dockerfile      # multi-stage: node build -> nginx serve
│   └── nginx.conf      # SPA fallback (try_files ... /index.html)
├── k8s/                # manifesty Kubernetes (Lab 2-5 + 8)
│   ├── 00-namespace.yaml
│   ├── 10-mongo-secret.yaml
│   ├── 11-mongo-headless-service.yaml
│   ├── 12-mongo-statefulset.yaml
│   ├── 20-backend-configmap.yaml
│   ├── 21-backend-deployment.yaml
│   ├── 22-backend-service.yaml
│   ├── 30-frontend-deployment.yaml
│   ├── 31-frontend-service.yaml
│   └── 40-ingress.yaml
└── chat_export.json      # plik kontekstu LLM 
```

## Wymagania

- `minikube` >= 1.38, `kubectl`, Docker 
- Lokalnie do dev: Node.js 18+, npm

## Uruchomienie w minikube

### 1. Start klastra + addon Ingress

```bash
minikube start --memory=4096 --cpus=2 --driver=docker
minikube addons enable ingress
```

### 2. Build obrazów w demonie minikube

Dzięki temu Kubernetes znajduje obrazy lokalnie i nie próbuje ich ściągać z rejestru (manifesty mają `imagePullPolicy: Never`).

```bash
eval $(minikube -p minikube docker-env --shell bash)
docker build -t stop-backend:1.0 backendTest/
docker build -t stop-frontend:1.0 frontend/
```

### 3. Apply manifestów

```bash
kubectl apply -f k8s/
kubectl wait --for=condition=Ready pod --all -n stop-app --timeout=180s
kubectl get all,pvc,ingress -n stop-app
```

### 4. Patch ingress-nginx-controller na LoadBalancer

`minikube tunnel` przypisuje `EXTERNAL-IP` wyłącznie serwisom typu `LoadBalancer`. Domyślny addon tworzy serwis `NodePort`, więc patch jest wymagany jednorazowo po każdym świeżym starcie klastra:

```bash
kubectl patch svc ingress-nginx-controller \
  -n ingress-nginx \
  -p '{"spec":{"type":"LoadBalancer"}}'
```

### 5. Wpis w `/etc/hosts`

Po uruchomieniu tunnela (krok 6) serwis dostanie `EXTERNAL-IP = 127.0.0.1`. Dodaj wpis raz na stałe:

```bash
# WSL2
echo "127.0.0.1 stop.local" | sudo tee -a /etc/hosts

# Windows (przeglądarka Windows) — edytuj jako Administrator:
# C:\Windows\System32\drivers\etc\hosts
# 127.0.0.1 stop.local
```

### 6. Uruchomienie tunnela

W **osobnym terminalu** (może wymagać sudo na WSL2):

```bash
minikube tunnel
```

Zostaw terminal otwarty, tunnel działa dopóki proces żyje.  
Zweryfikuj, że serwis ma `EXTERNAL-IP`:

```bash
kubectl get svc -n ingress-nginx ingress-nginx-controller
# NAME                       TYPE           CLUSTER-IP     EXTERNAL-IP   PORT(S)
# ingress-nginx-controller   LoadBalancer   10.x.x.x       127.0.0.1     80:xxxxx/TCP,443:xxxxx/TCP
```

### 7. Weryfikacja end-to-end

```bash
curl http://stop.local/                              # frontend -> 200
curl http://stop.local/api/rooms                     # backend -> []
curl -X POST -H 'Content-Type: application/json' \
     -d '{"nick":"tester","isPublic":true}' \
     http://stop.local/api/rooms                     # backend -> {code, playerId}
curl http://stop.local/api/rooms                     # zwraca utworzony pokoj
kubectl exec -n stop-app mongo-0 -- mongosh -u admin -p adminpass \
     --authenticationDatabase admin --quiet \
     --eval 'db.getSiblingDB("stop").rooms.find({}).toArray()'  # dokument w bazie
```

Aplikacja w przeglądarce: <http://stop.local>

### Komendy diagnostyczne

```bash
kubectl get all,pvc,ingress -n stop-app
kubectl logs -n stop-app deploy/backend -f
kubectl logs -n stop-app statefulset/mongo
kubectl describe pod -n stop-app mongo-0
kubectl exec -it -n stop-app mongo-0 -- mongosh -u admin -p adminpass
kubectl top pod -n stop-app                            # wymaga `minikube addons enable metrics-server`
kubectl port-forward -n stop-app svc/backend-svc 3000:3000   # debug API bez Ingressa i tunnela
```

### Sprzątanie

```bash
kubectl delete -f k8s/
minikube delete
```

## Konfiguracja

Tabelka rzeczy, które mogą się chcieć zmienić.

| Co                          | Gdzie                                                                                       | Domyślnie                                              |
| --------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| Hasło i user Mongo          | [`k8s/10-mongo-secret.yaml`](k8s/10-mongo-secret.yaml) (base64)                             | `admin` / `adminpass`                                  |
| Nazwa bazy, port, host Mongo| [`k8s/20-backend-configmap.yaml`](k8s/20-backend-configmap.yaml)                            | `stop`, `27017`, `mongo-0.mongo.stop-app.svc...`       |
| Rozmiar wolumenu Mongo      | [`k8s/12-mongo-statefulset.yaml`](k8s/12-mongo-statefulset.yaml) (`volumeClaimTemplates`)   | 2Gi, `storageClassName: standard`                      |
| Liczba replik frontendu     | [`k8s/30-frontend-deployment.yaml`](k8s/30-frontend-deployment.yaml)                        | 2 (stateless)                                          |
| Liczba replik backendu      | [`k8s/21-backend-deployment.yaml`](k8s/21-backend-deployment.yaml)                          | 1 (timery auto-stop w pamięci procesu)                 |
| Requests / limits CPU + RAM | Wszystkie Deployment/StatefulSet                                                            | manifesty (Lab 8)                                 |
| Routing / host              | [`k8s/40-ingress.yaml`](k8s/40-ingress.yaml)                                                | `host: stop.local`, `/api` -> backend, `/` -> frontend  |

## API

Specyfikacja OpenAPI: [`backendTest/openapi.yaml`](backendTest/openapi.yaml).

Najważniejsze endpointy:

- `GET /api/rooms` - lista publicznych pokoi w lobby
- `POST /api/rooms` - utworzenie pokoju (`{nick, isPublic}`)
- `POST /api/rooms/:code/join` - dołączenie do pokoju
- `GET /api/rooms/:code` - pełny stan pokoju (używane do pollingu)
- `POST /api/rooms/:code/settings` - zmiana ustawień (host)
- `POST /api/rooms/:code/start` / `/stop` / `/answers` / `/vote` / `/next-round` / `/reset` - przebieg gry

Backend trzyma stan pokoi w kolekcji `rooms` w bazie `stop`, klucz dokumentu = `code` pokoju.

## Ograniczenia

- **Backend skaluje się tylko do 1 repliki.** Auto-stop rundy używa `setTimeout` w pamięci procesu — przy >1 replice każda miałaby własny zegar. Aby skalować, trzeba przenieść harmonogram do Mongo (`stopAt: Date`) i sprawdzać go przy każdym `GET /api/rooms/:code`.
- **Po crashu Poda backendu** trwająca runda zostaje w stanie `playing` aż host kliknie `next-round` — z tego samego powodu (timer ginie razem z procesem). Stan rozgrywki (gracze, odpowiedzi, wyniki) jest bezpieczny, siedzi w Mongo na PVC.
- **Reklady Mongo**: PVC `mongo-data-mongo-0` przeżywa restart Poda, ale `kubectl delete -f k8s/` usuwa też StatefulSet — PVC zostaje (`Retain` zachowanie standardowego StorageClassa w minikube) i zostanie ponownie zbindowany po re-applyu.
