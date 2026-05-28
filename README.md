## Państwa Miasta

Multiplayerowa gra słowna „Państwa Miasta”. Projekt składa się z **backendu REST** (Express) oraz **frontendu** (React + Vite). Gracze tworzą/łączą się do pokoju, host ustawia rundy i kategorie, a stan gry jest odświeżany przez polling.

## Architektura (w skrócie)
- **Frontend**: aplikacja web w `frontend/` (React + Vite)
- **Backend**: API w `backendTest/` (Express) na porcie `3000` z prefiksem `/api`
- **Komunikacja**: REST (np. `GET /api/rooms`, `GET /api/rooms/:code` do pollingu)

Ważne: frontend ma obecnie na stałe ustawione API pod `http://localhost:3000/api` w `frontend/src/services/api.ts`. Przy uruchomieniu na Kubernetes/Minikube musisz to zmienić (patrz sekcja „Minikube”).

## Wymagania
- **Lokalnie (dev)**: Node.js 18+, npm
- **Minikube**: `minikube`, `kubectl`, Docker

## Uruchamianie lokalne (dev)

### Backend

```bash
cd backendTest
npm install
node index.js
```

Backend: `http://localhost:3000/api`

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend: `http://localhost:5173`

## Uruchomienie w klastrze (Minikube)

Poniżej jest „minimalny” przepis na uruchomienie w Minikube z budowaniem obrazów bezpośrednio w Dockerze Minikube (`minikube docker-env`). W repo nie ma gotowych manifestów K8s, więc umieszczone są jako snippet do skopiowania.

### 1) Start Minikube

```powershell
minikube start
kubectl config use-context minikube
```

### 2) Budowanie obrazów w Dockerze Minikube

PowerShell:

```powershell
& minikube -p minikube docker-env --shell powershell | Invoke-Expression
docker version
```

Od tego momentu `docker build ...` buduje obrazy „w środku” Minikube (nie musisz ich publikować do zewnętrznego registry).

### 3) Dockerfile (jeśli jeszcze nie masz)

W projekcie nie ma obecnie `Dockerfile`. Poniżej przykładowe wersje do skopiowania.

Backend (`backendTest/Dockerfile`):

```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY . .
EXPOSE 3000
CMD ["node", "index.js"]
```

Frontend (`frontend/Dockerfile`) — wariant dev (najprostszy, nieprodukcyjny):

```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
```

### 4) Zbuduj obrazy

```powershell
docker build -t panstwa-miasta-backend:local -f backendTest/Dockerfile backendTest
docker build -t panstwa-miasta-frontend:local -f frontend/Dockerfile frontend
```

### 5) Manifesty Kubernetes (do skopiowania)

Utwórz plik np. `k8s.yaml` i wklej poniższe (lub rozbij na osobne pliki).

**Backend (Service + Deployment)**:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: panstwa-miasta-backend
spec:
  selector:
    app: panstwa-miasta-backend
  ports:
    - name: http
      port: 3000
      targetPort: 3000
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: panstwa-miasta-backend
spec:
  replicas: 1
  selector:
    matchLabels:
      app: panstwa-miasta-backend
  template:
    metadata:
      labels:
        app: panstwa-miasta-backend
    spec:
      containers:
        - name: backend
          image: panstwa-miasta-backend:local
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 3000
```

**Frontend (Service + Deployment)**:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: panstwa-miasta-frontend
spec:
  selector:
    app: panstwa-miasta-frontend
  ports:
    - name: http
      port: 5173
      targetPort: 5173
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: panstwa-miasta-frontend
spec:
  replicas: 1
  selector:
    matchLabels:
      app: panstwa-miasta-frontend
  template:
    metadata:
      labels:
        app: panstwa-miasta-frontend
    spec:
      containers:
        - name: frontend
          image: panstwa-miasta-frontend:local
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 5173
```

Zastosuj manifesty:

```powershell
kubectl apply -f .\k8s.yaml
kubectl get pods
kubectl get svc
```

### 6) Dostęp do aplikacji

Frontend:

```powershell
minikube service panstwa-miasta-frontend
```

Backend (do testów):

```powershell
minikube service panstwa-miasta-backend
```

### 7) Konfiguracja adresu API w frontendzie (ważne)

Obecnie frontend ma stałe `API_URL = 'http://localhost:3000/api'` w `frontend/src/services/api.ts`.\n+\n+Na Kubernetes `localhost` oznacza **kontener frontendu**, a nie backend. Żeby to działało w klastrze, ustaw API na adres backendu w klastrze, np. `http://panstwa-miasta-backend:3000/api`.\n+\n+Najprostsza opcja na teraz: zmienić stałą w kodzie (docelowo warto przerobić na zmienną środowiskową np. `VITE_API_URL`).\n+\n+Jeśli tymczasowo chcesz ominąć zmianę w kodzie, możesz też odpalić oba serwisy i używać port-forward do backendu na swój `localhost:3000`, ale to jest rozwiązanie „dev only”.\n+\n+Port-forward backendu:\n+\n+```powershell\n+kubectl port-forward deploy/panstwa-miasta-backend 3000:3000\n+```\n+
### Debug

```powershell
kubectl logs deploy/panstwa-miasta-backend
kubectl logs deploy/panstwa-miasta-frontend
kubectl describe pod -l app=panstwa-miasta-backend
```

