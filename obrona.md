# Obrona — manifesty Kubernetes (`k8s/`)

Opis działania każdego pliku manifestu w projekcie **Państwa Miasta** oraz powiązana teoria z laboratoriów.

---

## `00-namespace.yaml`

**Rodzaj zasobu:** `Namespace`  
**Nazwa:** `pm-app`

### Działanie

Manifest tworzy logiczną przestrzeń nazw `pm-app`, w której uruchamiane są wszystkie komponenty aplikacji (PostgreSQL, backend, frontend, Ingress). Każdy kolejny manifest w folderze `k8s/` deklaruje pole `namespace: pm-app`, dzięki czemu zasoby trafiają do tej samej izolowanej „komórki” klastra.

Etykieta `labels.name: pm-app` umożliwia filtrowanie i identyfikację namespace'u (np. `kubectl get ns -l name=pm-app`). Bez tego manifestu pozostałe zasoby musiałyby trafiać do domyślnego namespace'u `default`, co utrudniałoby zarządzanie i separację projektu od innych wdrożeń w klastrze.

Po `kubectl apply -f k8s/00-namespace.yaml` (lub `kubectl apply -f k8s/`) Kubernetes rejestruje namespace; dopiero wtedy można w nim tworzyć Pody, Services, StatefulSety itd.

### Teoria

Przestrzenie nazw (ang. *Namespaces*) służą do logicznej izolacji zasobów w ramach jednego klastra. Pozwalają na oddzielenie środowisk (np. dev, test, prod) lub projektów. Przykład: jeden namespace może być środowiskiem testowym (`test`), drugi produkcyjnym (`prod`). Zasoby w różnych namespace'ach nie kolidują ze sobą i mogą mieć takie same nazwy.

---

## `10-postgres-secret.yaml`

**Rodzaj zasobu:** `Secret` (typ `Opaque`)  
**Nazwa:** `postgres-credentials`  
**Namespace:** `pm-app`

### Działanie

Secret przechowuje wrażliwe dane dostępowe do PostgreSQL w formie zakodowanej base64:

| Klucz | Wartość (plain text) | Base64 w pliku |
|-------|----------------------|----------------|
| `POSTGRES_USER` | `pm` | `cG0=` |
| `POSTGRES_PASSWORD` | `pmpass` | `cG1wYXNz` |
| `POSTGRES_DB` | `pm` | `cG0=` |

Secret **nie uruchamia** bazy — jest tylko magazynem danych. Wartości są wstrzykiwane do kontenerów przez:

- **StatefulSet `postgres`** — zmienne `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` (inicjalizacja bazy przy pierwszym starcie obrazu `postgres:16`),
- **Deployment `backend`** — zmienne `SPRING_DATASOURCE_USERNAME` i `SPRING_DATASOURCE_PASSWORD` (połączenie Spring Boot z JDBC).

Dzięki Secret hasła nie trafiają na stałe do ConfigMap ani do obrazu Docker; można je zmienić w jednym miejscu (`kubectl apply` po edycji pliku) bez przebudowy obrazów.

### Teoria

Secret służy do przechowywania danych, które wymagają szczególnej ochrony, takich jak hasła, tokeny, klucze prywatne lub certyfikaty TLS. W odróżnieniu od ConfigMap, wartości przechowywane w Secret są zakodowane w formacie base64, co nie zapewnia pełnego bezpieczeństwa, ale ogranicza ich przypadkowe ujawnienie. Dodatkowo Kubernetes umożliwia integrację Secret z zewnętrznymi systemami zarządzania tajemnicami (np. HashiCorp Vault, AWS Secrets Manager), co pozwala na jeszcze wyższy poziom ochrony.

---

## `11-postgres-service.yaml`

**Rodzaj zasobu:** `Service` (Headless)  
**Nazwa:** `postgres`  
**Namespace:** `pm-app`

### Działanie

Service typu **Headless** — pole `clusterIP: None` oznacza brak wspólnego wirtualnego IP i brak load balancingu między Podami. Zamiast tego DNS w klastrze zwraca **bezpośrednie adresy IP** Podów spełniających selektor `app: postgres`.

Selektor `selector.app: postgres` wiąże Service z Podami StatefulSetu `postgres`. Port `5432` (TCP) mapuje ruch na `targetPort: 5432` w kontenerze.

Dla pojedynczej repliki StatefulSetu (`postgres-0`) stabilna nazwa DNS to:

```
postgres-0.postgres.pm-app.svc.cluster.local
```

Składniki:
- `postgres-0` — nazwa Poda (StatefulSet nadaje przewidywalne nazwy),
- `postgres` — nazwa tego Service (Headless),
- `pm-app` — namespace,
- `svc.cluster.local` — domena usług klastra.

Backend łączy się z bazą właśnie pod tym hostem (wpisanym w ConfigMap `backend-config`).

### Teoria

**Service** to zasób, który zapewnia stabilny punkt dostępu do Podów. Dzięki selektorom (*selector*) Service wysyła ruch do odpowiednich Podów, niezależnie od ich zmiennego IP. Service kieruje ruch (np. HTTP) do aktualnych instancji Podów, bazując na etykietach. Najczęściej stosowane typy to:
- **ClusterIP** — dostęp tylko wewnątrz klastra (np. backend → baza danych),
- **NodePort** — udostępnia aplikację na porcie zewnętrznym (np. dostęp przez przeglądarkę),
- **LoadBalancer** — zewnętrzny adres IP (w chmurach publicznych).

**Headless Service** w Kubernetes to specjalny rodzaj usługi, która nie posiada przypisanego adresu IP typu ClusterIP. Jego głównym celem nie jest równoważenie obciążenia ani zapewnianie pojedynczego punktu dostępowego, ale umożliwienie bezpośredniego rozpoznawania i komunikacji z konkretnymi instancjami Podów w klastrze. W standardowym Service typu ClusterIP Kubernetes przydziela jeden adres IP, a żądania kierowane na tę usługę są rozdzielane (*load balanced*) pomiędzy dostępne Pody zgodnie z wybraną polityką. W przypadku Headless Service parametr `clusterIP` jest jawnie ustawiony na wartość `None`, co oznacza, że nie jest przydzielany żaden wspólny adres IP, a DNS zwraca listę adresów IP wszystkich Podów spełniających selektor. Dzięki temu aplikacje lub komponenty systemu mogą komunikować się bezpośrednio z konkretnym Podem, korzystając z jego unikalnej nazwy DNS. Jest to szczególnie przydatne w środowiskach, w których istotna jest znajomość tożsamości instancji, np. przy replikacji baz danych lub w systemach rozproszonych.

---

## `12-postgres-statefulset.yaml`

**Rodzaj zasobu:** `StatefulSet`  
**Nazwa:** `postgres`  
**Namespace:** `pm-app`

### Działanie

StatefulSet uruchamia **jedną replikę** (`replicas: 1`) bazy PostgreSQL 16 w Podzie o stałej nazwie `postgres-0`.

**Powiązanie z Service:** pole `serviceName: "postgres"` łączy StatefulSet z Headless Service z pliku `11-postgres-service.yaml` — to wymagane, aby DNS `postgres-0.postgres...` działał poprawnie.

**Kontener:**
- Obraz `postgres:16`, port `5432`.
- Zmienne środowiskowe z Secret `postgres-credentials` (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`) — przy pierwszym starcie PostgreSQL tworzy użytkownika i bazę `pm`.
- `PGDATA=/var/lib/postgresql/data/pgdata` — dane w podkatalogu (uniknięcie konfliktu z `lost+found` na zamontowanym wolumenie).

**Trwałość danych (`volumeClaimTemplates`):**
- Dla każdego Poda StatefulSet automatycznie tworzy PVC o nazwie `postgres-data-postgres-0`.
- `accessModes: ReadWriteOnce` — wolumen przypisany do jednego węzła, jeden Pod na raz.
- `storageClassName: standard` — dynamiczne provisionowanie PV (typowe w minikube).
- `storage: 2Gi` — rozmiar żądanej przestrzeni.

Montowanie: wolumen `postgres-data` → `/var/lib/postgresql/data` w kontenerze.

**Sondy:** `readinessProbe` z `pg_isready` — Pod dostaje status Ready dopiero gdy PostgreSQL przyjmuje połączenia (backend nie startuje „na pustą” bazę, jeśli czeka na readiness całego stacku).

**Zasoby:** `requests` 200m CPU / 256Mi RAM, `limits` 1 CPU / 1Gi RAM — scheduler wie, ile zasobów zarezerwować; przekroczenie limitu pamięci może skutkować zabiciem kontenera (OOM).

Po restarcie Poda **ten sam PVC** jest ponownie podpinany do `postgres-0` — dane gry (tabele `rooms`, `players`) zostają na dysku.

### Teoria

**StatefulSet:** Aplikacje stanowe (*stateful*) to takie, które muszą przechowywać i zachowywać informacje pomiędzy kolejnymi uruchomieniami lub w trakcie ich działania. Informacje te mogą obejmować zarówno dane wprowadzone przez użytkowników, jak i dane konfiguracyjne lub wewnętrzne stany pracy aplikacji.

**PersistentVolumeClaim** to żądanie przestrzeni dyskowej składane przez użytkownika lub aplikację. Określa, jakiego rodzaju wolumenu i o jakiej pojemności potrzebuje aplikacja.

**PersistentVolume** to abstrakcja reprezentująca fizyczny lub wirtualny zasób dyskowy dostępny w klastrze Kubernetes. Może to być katalog na hoście, zasób sieciowy (NFS), dysk chmurowy lub inne medium magazynujące. PV tworzone są zwykle przez administratora lub dynamicznie poprzez tzw. StorageClass. Dla uproszczenia w środowisku MicroK8s/minikube najczęściej pracujemy z lokalnymi wolumenami.

**StorageClass** definiuje sposób tworzenia oraz konfiguracji wolumenów dyskowych wykorzystywanych przez PersistentVolume (PV). W tradycyjnym podejściu administratorzy klastra przygotowywali wolumeny PV ręcznie, określając ich rozmiar i lokalizację. Takie podejście jest jednak nieefektywne w środowiskach dynamicznych, gdzie aplikacje są często wdrażane i usuwane w sposób automatyczny. W celu uproszczenia zarządzania pamięcią masową oraz umożliwienia jej dynamicznego przydzielania, Kubernetes wprowadził mechanizm StorageClass, który w połączeniu z dynamic provisioning umożliwia automatyczne tworzenie PersistentVolume w odpowiedzi na zgłoszenia PersistentVolumeClaim (PVC). Deklarując StorageClass, określa się między innymi wykorzystywany mechanizm tworzenia wolumenów (*provisioner*), politykę odzyskiwania danych po usunięciu wolumenu (*reclaimPolicy*), a także tryb przypisywania wolumenów (*volumeBindingMode*).

**Sposoby przechowywania danych w PersistentVolume:**
- **hostPath** — wykorzystywany głównie w środowiskach deweloperskich i edukacyjnych. Wolumen danych jest przechowywany bezpośrednio na dysku fizycznym węzła klastra. Jest to rozwiązanie proste, ale mało elastyczne, gdyż dane są przypisane do konkretnego węzła i nie są dostępne w przypadku jego awarii.
- **nfs** — wykorzystuje sieciowy system plików, umożliwiając współdzielenie danych pomiędzy wieloma węzłami klastra.
- **Rozwiązania chmurowe** (np. awsElasticBlockStore, gcePersistentDisk, azureDisk) — integracja z systemami dyskowymi w chmurze publicznej.
- **cephfs** — rozproszony system plików w zaawansowanych środowiskach.
- **local** — dane na konkretnym urządzeniu w ramach węzła.

**Polityka odzyskiwania danych (ReclaimPolicy):**
- **Retain** — wolumen pozostaje zachowany po usunięciu PVC; dane nie są automatycznie usuwane. Najbezpieczniejsza opcja dla baz danych.
- **Delete** — wolumen i dane usuwane po usunięciu PVC; stosowane przy danych tymczasowych.
- **Recycle** — przestarzała strategia czyszczenia katalogu; niewspierana w nowych wersjach Kubernetes.

**Żądania i limity pamięci:** Kontener może przekroczyć żądanie pamięci, jeśli węzeł ma dostępną pamięć. Kontener nie może jednak wykorzystać więcej pamięci niż wynosi jego limit. Jeśli kontener alokuje więcej pamięci niż wynosi jego limit, staje się kandydatem do zakończenia. Jeśli zakończony kontener może zostać ponownie uruchomiony, kubelet uruchamia go ponownie. Żądania i limity pamięci są powiązane z kontenerami, ale warto myśleć o Podzie jako o posiadającym żądanie i limit pamięci. Żądanie pamięci dla Podu jest sumą żądań pamięci dla wszystkich kontenerów w Podzie. Podobnie limit pamięci dla Podu jest sumą limitów wszystkich kontenerów w Podzie.

---

## `20-backend-configmap.yaml`

**Rodzaj zasobu:** `ConfigMap`  
**Nazwa:** `backend-config`  
**Namespace:** `pm-app`

### Działanie

ConfigMap przechowuje **niesekretną** konfigurację backendu Spring Boot w postaci par klucz–wartość:

| Klucz | Wartość | Znaczenie |
|-------|---------|-----------|
| `SERVER_PORT` | `3000` | Port HTTP aplikacji w kontenerze (Spring mapuje `SERVER_PORT` → `server.port`) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres-0.postgres.pm-app.svc.cluster.local:5432/pm` | JDBC URL do PostgreSQL w klastrze |

Deployment `backend` ładuje cały ConfigMap przez `envFrom.configMapRef` — obie zmienne trafiają do środowiska kontenera bez wpisywania ich w manifest Deploymentu.

Hasło i użytkownik bazy **nie** są w ConfigMap — pochodzą z Secret (`10-postgres-secret.yaml`), zgodnie z podziałem: ConfigMap = konfiguracja publiczna, Secret = dane wrażliwe.

Zmiana adresu bazy lub portu wymaga edycji ConfigMap i restartu Podów backendu (`kubectl rollout restart deployment/backend -n pm-app`).

### Teoria

ConfigMap służy do przechowywania niesekretnych informacji konfiguracyjnych w formie par klucz–wartość lub całych plików konfiguracyjnych. Najczęściej wykorzystywany jest do przekazywania ustawień konfiguracyjnych aplikacji backendowych, takich jak adresy baz danych, ustawienia trybu pracy, ścieżki API czy konfiguracja poziomu logowania.

---

## `21-backend-deployment.yaml`

**Rodzaj zasobu:** `Deployment`  
**Nazwa:** `backend`  
**Namespace:** `pm-app`

### Działanie

Deployment utrzymuje **2 repliki** (`replicas: 2`) Poda z aplikacją Spring Boot (obraz `pm-backend:3.0`). Selektor `app: backend` wiąże Deployment z Podami utworzonymi z szablonu (`template.metadata.labels.app: backend`). W tle Kubernetes tworzy **ReplicaSet**, który dba o to, aby zawsze działały dokładnie 2 instancje.

**Obraz:** `pm-backend:3.0`, `imagePullPolicy: Never` — obraz musi być zbudowany lokalnie w demonie Docker minikube (`docker build -t pm-backend:3.0 backend/`), bez pobierania z rejestru.

**Konfiguracja:**
- `envFrom` → ConfigMap `backend-config` (port, JDBC URL),
- `env` → Secret `postgres-credentials` (username, password do Spring DataSource).

**Sondy HTTP** na `/api/rooms:3000`:
- **readinessProbe** — Pod dostaje ruch dopiero gdy API odpowiada (ważne przy wolnym starcie JVM + Hibernate); `initialDelaySeconds: 25`, do 12 nieudanych prób.
- **livenessProbe** — restart kontenera, jeśli aplikacja „zawiesi się”; start po 60 s.

**Skalowalność:** dwie repliki obsługują ruch równolegle; stan gry jest w PostgreSQL, a auto-stop rundy (`roundEndsAt` + atomowy `UPDATE`) działa poprawnie przy wielu instancjach backendu.

**Zasoby:** requests 250m CPU / 256Mi RAM, limits 1 CPU / 512Mi RAM.

Przy `kubectl apply` lub zmianie obrazu Deployment wykonuje **rolling update** — kolejno wymienia stare Pody na nowe, bez całkowitego przestoju (przy poprawnie skonfigurowanych sondach).

### Teoria

**ReplicaSet** zapewnia utrzymanie zadanej liczby replik tego samego Poda. W razie awarii lub usunięcia jednego z nich zostanie natychmiast uruchomiony ponownie. Dzięki temu aplikacja działa stabilnie, niezależnie od problemów sprzętowych czy restartów węzłów.

**Deployment** to najczęściej używany zasób w Kubernetes. Deployment to menedżer ReplicaSetów. Umożliwia aktualizację aplikacji (*rolling update*), automatyczny rollback, skalowanie w górę i w dół (więcej/mniej instancji) oraz wersjonowanie wdrożeń. Deployment to zasób, którego używa się niemal zawsze w aplikacjach. Nie używamy Podów ani ReplicaSetów bezpośrednio, tylko przez Deploymenty.

**Żądania i limity pamięci:** Kontener może przekroczyć żądanie pamięci, jeśli węzeł ma dostępną pamięć. Kontener nie może jednak wykorzystać więcej pamięci niż wynosi jego limit. Jeśli kontener alokuje więcej pamięci niż wynosi jego limit, staje się kandydatem do zakończenia. Żądania i limity pamięci są powiązane z kontenerami, ale warto myśleć o Podzie jako o posiadającym żądanie i limit pamięci — sumę wartości wszystkich kontenerów w Podzie.

---

## `22-backend-service.yaml`

**Rodzaj zasobu:** `Service` (typ `ClusterIP`)  
**Nazwa:** `backend-svc`  
**Namespace:** `pm-app`

### Działanie

Service wystawia backend **wewnątrz klastra** pod stabilnym adresem DNS `backend-svc.pm-app.svc.cluster.local` (skrót: `backend-svc` w tym samym namespace).

- **Typ `ClusterIP`** — brak bezpośredniego dostępu z Internetu; ruch tylko z innych Podów/Services/Ingress w klastrze.
- **Selektor** `app: backend` — ruch trafia do wszystkich Podów Deploymentu `backend` (obie repliki).
- **Port** `3000` → `targetPort: 3000` — mapowanie na port HTTP Spring Boot w kontenerze.

Load balancing między 2 replikami backendu odbywa się automatycznie (domyślnie round-robin na poziomie Service/kube-proxy). Ingress (`40-ingress.yaml`) kieruje ścieżkę `/api` właśnie na ten Service, a nie bezpośrednio na Pody — dzięki temu zmiana IP Podów przy restarcie nie wymaga aktualizacji Ingressa.

### Teoria

Service to zasób, który zapewnia stabilny punkt dostępu do Podów. Dzięki selektorom (*selector*) Service wysyła ruch do odpowiednich Podów, niezależnie od ich zmiennego IP. Service kieruje ruch (np. HTTP) do aktualnych instancji Podów, bazując na etykietach. Najczęściej stosowane typy to:
- **ClusterIP** — dostęp tylko wewnątrz klastra (np. backend → baza danych),
- **NodePort** — udostępnia aplikację na porcie zewnętrznym (np. dostęp przez przeglądarkę),
- **LoadBalancer** — zewnętrzny adres IP (w chmurach publicznych).

---

## `30-frontend-deployment.yaml`

**Rodzaj zasobu:** `Deployment`  
**Nazwa:** `frontend`  
**Namespace:** `pm-app`

### Działanie

Deployment utrzymuje **2 repliki** frontendu — statyczna aplikacja React zbudowana przez Vite i serwowana przez **nginx** (obraz `pm-frontend:1.0`, multi-stage Dockerfile w `frontend/`).

- **Port kontenera:** `80` (nginx).
- **`imagePullPolicy: Never`** — obraz budowany lokalnie w minikube (`docker build -t pm-frontend:1.0 frontend/`).

Frontend jest **bezstanowy** (*stateless*): nie korzysta z PVC; każda replika serwuje identyczne pliki HTML/JS/CSS. Sesja gracza (nick, `playerId`) jest w `localStorage` przeglądarki, stan gry — w backendzie/PostgreSQL.

**Sondy** na `/healthz:80` (endpoint zdefiniowany w `frontend/nginx.conf`):
- **readinessProbe** — szybki start (3 s),
- **livenessProbe** — wykrycie zawieszonego nginx.

**Zasoby:** requests 50m CPU / 64Mi RAM, limits 200m CPU / 128Mi RAM (mniejsze niż backend — nginx + statyczne pliki zużywają mniej pamięci).

Dwie repliki zwiększają dostępność i pozwalają Ingressowi rozdzielać ruch HTTP między instancje.

### Teoria

**ReplicaSet** zapewnia utrzymanie zadanej liczby replik tego samego Poda. W razie awarii lub usunięcia jednego z nich zostanie natychmiast uruchomiony ponownie.

**Deployment** to menedżer ReplicaSetów. Umożliwia aktualizację aplikacji (*rolling update*), automatyczny rollback, skalowanie w górę i w dół oraz wersjonowanie wdrożeń. Nie używamy Podów ani ReplicaSetów bezpośrednio, tylko przez Deploymenty.

**Żądania i limity pamięci:** Kontener nie może wykorzystać więcej pamięci niż wynosi jego limit; przekroczenie limitu może skutkować zakończeniem procesu (OOM). Żądanie i limit Podu to suma wartości wszystkich kontenerów w Podzie.

---

## `31-frontend-service.yaml`

**Rodzaj zasobu:** `Service` (typ `ClusterIP`)  
**Nazwa:** `frontend-svc`  
**Namespace:** `pm-app`

### Działanie

Service wystawia frontend w klastrze pod nazwą `frontend-svc.pm-app.svc.cluster.local`.

- **Typ `ClusterIP`** — dostęp wewnątrz klastra (głównie z Ingress Controller).
- **Selektor** `app: frontend` — ruch do obu replik Deploymentu `frontend`.
- **Port** `80` → `targetPort: 80` — nginx w kontenerze.

Ingress kieruje ścieżkę `/` (cała aplikacja SPA poza `/api`) na ten Service. Przeglądarka użytkownika ładuje `index.html` i assety; żądania API idą na `/api` → `backend-svc` (osobna reguła w Ingress).

### Teoria

Service to zasób, który zapewnia stabilny punkt dostępu do Podów. Dzięki selektorom Service wysyła ruch do odpowiednich Podów, niezależnie od ich zmiennego IP. Typ **ClusterIP** zapewnia dostęp tylko wewnątrz klastra — odpowiedni dla frontendu obsługiwanego przez Ingress, bez bezpośredniego wystawiania Podów na zewnątrz.

---

## `40-ingress.yaml`

**Rodzaj zasobu:** `Ingress`  
**Nazwa:** `pm-ingress`  
**Namespace:** `pm-app`

### Działanie

Ingress definiuje **reguły routingu HTTP** dla hosta `pm.local`. Wymaga działającego **Ingress Controller** (w minikube: addon `ingress`, NGINX Ingress Controller) oraz — w tym projekcie — `minikube tunnel` i wpisu `127.0.0.1 pm.local` w `/etc/hosts`.

**`ingressClassName: nginx`** — reguły obsługuje kontroler NGINX.

**Reguły dla hosta `pm.local`:**

| Ścieżka | pathType | Service docelowy | Port | Efekt |
|---------|----------|------------------|------|--------|
| `/api` | Prefix | `backend-svc` | 3000 | API Spring Boot (REST) |
| `/` | Prefix | `frontend-svc` | 80 | SPA React (nginx) |

Kolejność ma znaczenie: `/api` jest bardziej specyficzna i matchuje się przed `/`, dzięki czemu `GET pm.local/api/rooms` trafia do backendu, a `GET pm.local/` — do frontendu.

**Przepływ żądania użytkownika:**
1. Przeglądarka → `http://pm.local/...`
2. `minikube tunnel` → Ingress Controller (LoadBalancer, EXTERNAL-IP 127.0.0.1)
3. Ingress `pm-ingress` wybiera Service według ścieżki
4. Service (`backend-svc` lub `frontend-svc`) rozdziela ruch między repliki Deploymentu (round-robin)

Jeden adres/host (`pm.local`) obsługuje całą aplikację — frontend i backend — bez osobnych portów NodePort dla każdej warstwy.

### Teoria

Ingress w Kubernetes to deklaratywny zasób, który opisuje reguły routingu ruchu przychodzącego – czyli mówi Kubernetesowi: „jeśli ktoś wchodzi na adres `/app1`, przekaż to zapytanie do usługi `service-app1`”. Reguły te są zapisane w manifestach YAML i działają tylko wtedy, gdy w klastrze działa dodatkowy komponent – **Ingress Controller**.

Ingress Controller to program, który interpretuje zasoby Ingress i przekłada je na rzeczywiste reguły reverse proxy. W praktyce najczęściej wykorzystywany jest NGINX Ingress Controller, który wewnętrznie korzysta z mechanizmu reverse proxy znanego z tradycyjnych konfiguracji serwera NGINX. Po jego uruchomieniu klaster Kubernetes zaczyna obsługiwać ruch HTTP przez wspólny punkt wejścia – a użytkownik może definiować, jak ten ruch ma być przekierowywany.

Ingress Controller pełni nie tylko rolę bramy HTTP, ale również funkcję load balancera. Jeśli Ingress przekierowuje ruch do serwisu (Service), a ten serwis obsługuje kilka replik aplikacji (czyli Podów stworzonych np. przez Deployment), Ingress Controller rozdziela żądania HTTP pomiędzy te repliki. Oznacza to, że każde zapytanie może trafić do innego Poda, co zwiększa wydajność, dostępność i umożliwia skalowanie poziome aplikacji. Domyślnie wykorzystywany mechanizm równoważenia obciążenia to strategia **round-robin** – kolejne żądania trafiają po kolei do różnych instancji. Ten proces przebiega całkowicie automatycznie i jest transparentny dla użytkownika końcowego.

Dzięki Ingressowi możliwe jest m.in.:
- obsłużenie wielu aplikacji pod jednym adresem IP,
- publikacja każdej aplikacji pod osobną ścieżką URL (`/app1`, `/app2`),
- rozdzielanie aplikacji według nazw hostów (`app1.local`, `admin.local`),
- centralna konfiguracja HTTPS z wykorzystaniem certyfikatów TLS,
- automatyczne rozkładanie ruchu pomiędzy wiele instancji tej samej aplikacji.

---

## `rbac/50-serviceaccounts.yaml`

**Rodzaj zasobu:** `ServiceAccount` (×2)  
**Nazwy:** `pm-backend`, `pm-frontend`  
**Namespace:** `pm-app`

### Działanie

Tworzy dedykowane konta serwisowe dla podów backendu i frontendu, każde z `automountServiceAccountToken: false`. Deploymenty wskazują te konta przez `spec.template.spec.serviceAccountName`, więc pody nie używają już domyślnego konta `default`.

Kluczowa zasada: aplikacja (Spring Boot, nginx) **nie komunikuje się z API Kubernetes** — rozmawia tylko z PostgreSQL i obsługuje ruch HTTP. Dlatego token JWT konta serwisowego nie jest montowany do kontenera (`automountServiceAccountToken: false`), a samo konto nie ma żadnego `RoleBinding`. To realizacja zasady najmniejszych uprawnień (least privilege): nawet po przejęciu poda atakujący nie dostaje żadnego dostępu do klastra.

Weryfikacja braku tokenu:

```bash
kubectl exec -n pm-app deploy/backend -- ls /var/run/secrets/kubernetes.io/serviceaccount
# ls: cannot access ...: No such file or directory
```

---

## `rbac/51-viewer.yaml`

**Rodzaj zasobu:** `Role` + `RoleBinding`  
**Nazwy:** `pm-viewer`, `pm-viewer-binding`  
**Namespace:** `pm-app`

### Działanie

`Role pm-viewer` definiuje uprawnienia tylko do odczytu (`get`, `list`, `watch`) na zasobach diagnostycznych namespace'u `pm-app`: pody i ich logi (`pods/log`), serwisy, configmapy, endpointy, PVC, eventy, a także deploymenty/replicasety/statefulsety (`apps`) i ingressy (`networking.k8s.io`). Reguły **świadomie pomijają `secrets`** — osoba z tą rolą nie zobaczy haseł do bazy.

`RoleBinding pm-viewer-binding` przypisuje tę rolę podmiotowi `kind: User name: pm-viewer`. Ponieważ to `RoleBinding` (a nie `ClusterRoleBinding`), uprawnienia działają wyłącznie w `pm-app`.

Weryfikacja (impersonacja `--as`):

```bash
kubectl auth can-i list pods   --as=pm-viewer -n pm-app    # yes
kubectl auth can-i get secrets --as=pm-viewer -n pm-app    # no
kubectl auth can-i list pods   --as=pm-viewer -n default   # no
```

---

## `rbac/52-operator.yaml`

**Rodzaj zasobu:** `Role` + `RoleBinding`  
**Nazwy:** `pm-operator`, `pm-operator-binding`  
**Namespace:** `pm-app`

### Działanie

`Role pm-operator` rozszerza uprawnienia viewer-a o operacje obsługowe: `patch`/`update` na deploymentach i `deployments/scale` (restart i skalowanie) oraz `delete` na podach (wymuszenie odtworzenia poda). Nadal brak dostępu do `secrets` i brak prawa `delete` na deploymentach (żeby operator nie skasował całej aplikacji).

`RoleBinding pm-operator-binding` przypisuje rolę podmiotowi `kind: User name: pm-operator`, również tylko w `pm-app`.

Weryfikacja:

```bash
kubectl auth can-i patch deployments --as=pm-operator -n pm-app   # yes
kubectl auth can-i delete pods       --as=pm-operator -n pm-app   # yes
kubectl auth can-i get secrets       --as=pm-operator -n pm-app   # no
kubectl auth can-i delete deployments --as=pm-operator -n pm-app  # no
```

### Teoria

RBAC (Role-Based Access Control) reguluje dostęp do zasobów klastra na podstawie ról. Uprawnienia opisuje się jako kombinację `verbs` (np. `get`, `list`, `watch`, `create`, `delete`), `apiGroups` (np. `""` dla zasobów core, `apps`, `networking.k8s.io`) i `resources` (np. `pods`, `pods/log`, `deployments`). Można też zawęzić regułę do konkretnych obiektów przez `resourceNames`.

Uprawnienia są **wyłącznie addytywne** — nie istnieją reguły „deny”. Brak przyznanego uprawnienia oznacza brak dostępu.

- `Role` ustala uprawnienia w obrębie jednej przestrzeni nazw; `ClusterRole` — w zakresie całego klastra (lub dla zasobów cluster-scoped).
- `RoleBinding` przypisuje rolę podmiotom (`User`, `Group`, `ServiceAccount`) w danym namespace; `ClusterRoleBinding` — w całym klastrze.

W tym projekcie wszystkie role są namespaced (`Role`/`RoleBinding`), bo dotyczą wyłącznie `pm-app`. Podmiotami ról „ludzkich” są obiekty `User` (Kubernetes nie zarządza userami — przy weryfikacji używamy impersonacji `kubectl --as=...`). Pody korzystają natomiast z `ServiceAccount` bez przypisanych ról (least privilege). W minikube RBAC jest włączony domyślnie, więc krok `microk8s enable rbac` z laboratorium nie jest potrzebny.

---

## Podsumowanie — przepływ zależności

```
00-namespace (pm-app)
    │
    ├── 10-secret ──────────────┬──► 12-statefulset (postgres-0 + PVC)
    │                           └──► 21-deployment (backend)
    ├── 11-service (headless) ───────► DNS postgres-0.postgres...
    ├── 20-configmap ────────────────► 21-deployment (backend)
    ├── 21-deployment (backend ×2)
    ├── 22-service (backend-svc) ◄───┐
    ├── 30-deployment (frontend ×2)  │
    ├── 31-service (frontend-svc) ◄──┤
    ├── 40-ingress (pm.local) ───────┴──► /api → backend-svc, / → frontend-svc
    └── rbac/
        ├── 50-serviceaccounts ──► pm-backend / pm-frontend (no token) ◄── 21/30-deployment
        ├── 51-viewer (Role+Binding) ──► User pm-viewer  (read-only, bez secrets)
        └── 52-operator (Role+Binding) ► User pm-operator (restart/scale, bez secrets)
```
