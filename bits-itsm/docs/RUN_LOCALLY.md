# Running BITSolve on your own laptop

You do **not** need Java, Maven, Node or PostgreSQL installed for the easy route. Docker runs everything
(the database, the app and a fake mail inbox) inside containers.

## Route A: Docker (recommended, about 15 minutes the first time)

1. **Install Docker Desktop** from https://www.docker.com/products/docker-desktop/ (Windows or macOS).
   * Windows: accept the WSL 2 prompt if it appears, then restart.
   * Open Docker Desktop once and wait until it says **Engine running**.
2. **Get the code.** Either download the branch as a ZIP from GitHub
   (Code → Download ZIP on the `bitsolve-final` branch) and unzip it, or
   `git clone -b bitsolve-final https://github.com/kushgupta7/oops.git`.
3. **Open a terminal in the `bits-itsm` folder** (the one containing `docker-compose.yml`).
   * Windows: open the folder in File Explorer, type `cmd` in the address bar, press Enter.
   * macOS: right-click the folder → New Terminal at Folder.
4. **Start it:**
   ```
   docker compose up --build
   ```
   The first run downloads images and builds the app (5–10 minutes). It is ready when the log shows
   `Started BitsolveBackendApplication`.
5. **Open http://localhost:8080** and log in with any demo account. The password is `BitsolveDemo2026`.

   | Role | E-mail |
   |---|---|
   | Administrator | `admin01@hyderabad.bits-pilani.ac.in` |
   | Technician | `technician01@hyderabad.bits-pilani.ac.in` … `technician04@…` |
   | Student | `student01@hyderabad.bits-pilani.ac.in` … `student04@…` |

   E-mails the app sends appear at **http://localhost:8025** (MailHog).

**Stopping:** press `Ctrl+C` in the terminal, or run `docker compose down`.
**Start fresh (wipe all data):** `docker compose down -v`, then `docker compose up --build` again.

### If something goes wrong

| Symptom | Fix |
|---|---|
| `docker: command not found` / `cannot connect to the Docker daemon` | Docker Desktop is not running. Open it and wait for "Engine running". |
| `port is already allocated` (8080, 8025) | Another program uses that port. Close it, or change `"8080:8080"` to `"9090:8080"` in `docker-compose.yml` and use http://localhost:9090. |
| Page loads but login fails | Wait until the log says `Started BitsolveBackendApplication`; the demo users are created on first start. |
| Code changed but the app looks old | Run `docker compose up --build` (the `--build` makes Docker rebuild). |

## Route B: without Docker (for developing the code)

Install **JDK 25** (Temurin), **Node 24** and **PostgreSQL 16**. Create the database once:

```
createuser -P bitsolve          # password: bitsolve-dev
createdb -O bitsolve bitsolve
```

Backend (terminal 1, in `bits-itsm/backend`):

```
# macOS / Linux
DB_USER=bitsolve DB_PASSWORD=bitsolve-dev SPRING_PROFILES_ACTIVE=dev SESSION_COOKIE_SECURE=false \
BITSOLVE_DEMO_SEED_ENABLED=true DEMO_USER_PASSWORD=BitsolveDemo2026 ./mvnw spring-boot:run
```
```
:: Windows (cmd)
set DB_USER=bitsolve& set DB_PASSWORD=bitsolve-dev& set SPRING_PROFILES_ACTIVE=dev& set SESSION_COOKIE_SECURE=false& set BITSOLVE_DEMO_SEED_ENABLED=true& set DEMO_USER_PASSWORD=BitsolveDemo2026& mvnw.cmd spring-boot:run
```

Frontend with live reload (terminal 2, in `bits-itsm/frontend`): `npm ci`, then `npm run dev`, and open the
address Vite prints (it forwards `/api` calls to the backend on port 8080).

## Running the tests

* **No installation:** every push to GitHub runs the full build in GitHub Actions (Actions tab of the repo):
  frontend tests and build, all backend tests against a real PostgreSQL, and a Docker smoke test.
* **Locally (Route B tools installed):** `cd bits-itsm/frontend && npm test`, then
  `cd bits-itsm/backend && ./mvnw verify`. The PostgreSQL integration tests run only when
  `BITSOLVE_DB_TESTS=true` and `BITSOLVE_TEST_DB_URL` point at a **disposable** database (they delete data).
