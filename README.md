## Państwa Miasta

Multiplayerowa gra słowna „Państwa Miasta”. 

## Architektura (w skrócie)
- **Frontend**: aplikacja web w `frontend/` (React + Vite)
- **Backend**: API w `backendTest/` (Express) na porcie `3000` z prefiksem `/api`
- **Komunikacja**: REST (np. `GET /api/rooms`, `GET /api/rooms/:code` do pollingu)

frontend ma obecnie na stałe ustawione API pod `http://localhost:3000/api` w `frontend/src/services/api.ts`. 

## Wymagania
- **Lokalnie (dev)**: Node.js 18+, npm

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



