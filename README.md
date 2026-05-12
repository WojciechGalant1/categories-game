# Państwa Miasta

## Wymagania
- Node.js (wersja 18 lub nowsza)
- npm

## Uruchamianie lokalne

### 1. Serwer (Backend)

```bash
cd backendTest
npm install
node index.js
```

### 2. Aplikacja (Frontend)

```bash
cd frontend
npm install
npm run dev
```
Aplikacja domyślnie będzie dostępna pod adresem: [http://localhost:5173](http://localhost:5173)


#	Metoda	URL	Body (JSON)	Opis
1	GET	/rooms	—	Lista publicznych pokoi w lobby (do wyświetlenia na stronie głównej)
2	POST	/rooms	{ nick, isPublic }	Tworzenie pokoju. Zwraca { code, playerId }
3	POST	/rooms/{code}/join	{ nick }	Dołączenie do pokoju. Zwraca { code, playerId }
4	GET	/rooms/{code}	—	Polling – pełny stan pokoju (gracze, ustawienia, status gry, odpowiedzi, głosy, wyniki, pozostały czas). Frontend odpytuje ten endpoint co ~1s
5	POST	/rooms/{code}/settings	{ playerId, settings }	Zmiana ustawień (kategorie, czas, rundy). Tylko host
6	POST	/rooms/{code}/start	{ playerId }	Start gry / pierwszej rundy. Tylko host
7	POST	/rooms/{code}/stop	{ playerId }	Gracz wysyła odpowiedzi (deklaruje gotowość). Gdy wszyscy klikną → przejście do fazy reviewing
8	POST	/rooms/{code}/answers	{ playerId, answers }	Zapis odpowiedzi gracza. answers to obiekt { "Państwa": "Polska", "Miasta": "Poznań", ... }
9	POST	/rooms/{code}/vote	{ voterId, targetPlayerId, category, isValid }	Głosowanie na odpowiedź innego gracza (true = OK, false = odrzucone)
10	POST	/rooms/{code}/next-round	{ playerId }	Przejście do następnej rundy (lub zakończenie gry). Tylko host. Liczy punkty za bieżącą rundę
11	POST	/rooms/{code}/reset	{ playerId }	Powrót do lobby. Tylko host
