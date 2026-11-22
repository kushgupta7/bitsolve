# BITSolve: handoff summary (for continuing in Claude Code)

Last updated: 22 Nov 2026, about 00:00 IST. **Deadline: 22.11.2026 (today).**

## 1. What this is
- **Course:** BITS Pilani Hyderabad CS F213 (OOP), group project "BITSolve": Intelligent IT Service Management & Incident Resolution.
- **Owner:** Kush Gupta (GitHub `kushgupta7`) is "Person 4" on the team: services, intelligent features, QA/DevOps. His father Vikas has been driving this chat.
- **Stack:**
  - Java 25 and Spring Boot 4.1.2 (Web MVC, Data JPA/Hibernate 7, Security with session + CSRF, Validation, Mail, Scheduling).
  - Flyway migrations V1–V8 and PostgreSQL 16.
  - React 19, Vite and Recharts.
  - Docker Compose (app + Postgres + MailHog) and GitHub Actions CI.

## 2. Where the code is (source of truth)
| Location | What | Status |
|---|---|---|
| **`github.com/kushgupta7/bitsolve`, branch `main`** | **Final version, use this** | CI green (commit `1df81d8`) |
| `github.com/kushgupta7/oops`, branch `bitsolve-final` | Identical copy (Kush's fork of the team repo) | CI green |
| `github.com/kushgupta7/oops`, branch `final-stage` | **Abandoned** version that merged Aditya's upstream commit `a3a59e6` (automatic classification). The merge caused the test failures. | Do not use |
| `bitsolve-final.zip` (sent in chat) | `git archive` of `1df81d8`, top folder `oops-main/` | Same as `main` |

The project lives in the `bits-itsm/` subfolder. The repo root has `README.md`, `.github/workflows/ci.yml` and this file.

**Decision:** we use our own pre-merge copy (Kush's "oops-main-final" zip plus our work) and **do not merge** Aditya's auto-classification (`a3a59e6`). It made incidents CLASSIFIED on creation and broke the team's tests. If the team insists on it, it has to be re-integrated deliberately.

## 3. How to run / test
- **Locally (only Docker Desktop needed):**
  1. In `bits-itsm/`, run `docker compose up --build`.
  2. Open http://localhost:8080. MailHog is at http://localhost:8025.
  3. Full guide with troubleshooting: `bits-itsm/docs/RUN_LOCALLY.md`.
- **Fresh data after code changes:** run `docker compose down -v` and then `docker compose up --build`. The demo setup only runs on an empty database.
- **Demo logins (password `BitsolveDemo2026` for all):**
  - `admin01@hyderabad.bits-pilani.ac.in`
  - `technician01`–`04@…`: 01 hardware, 02 software, 03 database, 04 network as the original skill. The demo gives each 4–5 skills.
  - `student01`–`04@…`
  - Students can also self-register (institutional e-mail only).
- **First start:** demo accounts are committed a few seconds *after* "Started BitsolveBackendApplication" (together with 60 seeded incidents). A login that fails right away should be retried after about 10 seconds.
- **CI** (`.github/workflows/ci.yml`) runs on every push:
  1. `build-and-test`:
     - Rejects case-colliding paths.
     - Frontend: `npm ci && npm test && npm run build`.
     - Backend: `./mvnw -B -ntp -Pcoverage verify` against a Postgres 16 service, with `BITSOLVE_DB_TESTS=true`.
     - Failure annotations come from logs and surefire reports.
  2. `docker-compose-smoke`:
     - `docker compose up --build`, then checks health, the UI, the student login (with retry), the incident list, and that the report-form options (categories and services) are non-empty.
- **Reading CI from a sandbox:** use `gh api repos/kushgupta7/bitsolve/commits/<sha>/check-runs` and `…/check-runs/<id>/annotations`. The `/actions` API paths were blocked through the proxy.
- **In the Claude cloud sandbox:** Maven Central is blocked, so the real build runs only on GitHub Actions. A stub harness at `/home/claude/harness` (`compile.sh`, `test.sh`) compiles and runs about 145 unit tests without Spring. It does not exist on a fresh machine.
- **Tests:** about 196 `@Test` methods, plus 5 frontend tests. 59 documented test cases (TC01–TC59) are in `docs/TEST_CASES.md`.

## 4. What the system does (current feature set)
- **Portals and registration:**
  - Three role portals with different themes: Student (teal), Technician (indigo), Admin (navy/amber).
  - Live home pages.
  - Student self-registration (`RegistrationController`, `Register.jsx`, `UserFactory`).
- **Lifecycle:**
  - Implemented with the **State pattern** (`incident/state/*`, `IncidentLifecycle`, `AbstractIncidentState` with an EnumMap transition table).
  - NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS ⇄ AWAITING_INFO → RESOLVED → VERIFIED → CLOSED, plus reopen.
  - AWAITING_INFO pauses the SLA and extends the deadline when the incident resumes.
  - Auto-close after 72 h.
- **Reporting:**
  - The student enters title, category, service, description and an optional number of affected people.
  - **Impact and urgency are NOT chosen by the student** (changed this session). The admin assesses them in the *Classify* form. Until then the backend stores a provisional MEDIUM/MEDIUM, and the UI shows "Assessed at classification".
  - The API still accepts impact/urgency on create (optional) for backward compatibility with tests.
- **Priority:**
  - 4 runtime-switchable **Strategy** implementations.
  - Criticality comes from the service's catalog entry.
  - `AffectedUsersRule`: more than 100 affected people raises priority by one level.
  - Per-service SLA factor (0.25–4).
- **Assignment:**
  - Strategies: Skill, Load, RoundRobin, PriorityBased and SmartRecommendation, plus an audited fallback (`AssignmentStrategies` registry).
  - Transfer and collaboration requests need admin approval.
- **Technician availability (added this session):** technicians choose Available / Busy / Unavailable on their home page.
  - Endpoint: `PUT /api/technician/availability`.
  - Only AVAILABLE technicians get new work.
  - Tests TC56–TC57.
- **SLA:**
  - Policies per priority, background `SlaMonitor` (warning at 80%, breach), escalation of unacknowledged P1 incidents via **Chain of Responsibility**.
  - SLA breaches, escalations and major incidents send a simulated SMS through an **Adapter**.
- **Notifications (Observer, `EventBus`):** in-app, e-mail outbox with retry (MailHog in Docker), and SMS. Plus an immutable audit trail and per-incident history.
- **Search, reports, insights:**
  - 9 combinable search criteria.
  - Insights page (Recharts), CSV export and print-to-PDF.
- **Intelligent features (now 6):**
  1. Duplicate detection
  2. Major-incident correlation
  3. P1 auto-escalation
  4. Smart technician recommendation
  5. SLA-risk prediction
  6. Auto-categorisation suggestion
- **Removed this session (at the user's request):**
  - The Knowledge Base page.
  - Suggested resolutions (`ResolutionSuggester`, `/technician/knowledge`, `/…/suggested-resolutions`).
- **Communication:**
  - There is **no live chat**. Two-way messaging is a timeline on each incident:
    - The technician *Request Additional Information* → the student replies.
    - The student *Add information* at any time.
    - The technician adds student-visible notes.
  - The user was told a real chat view would be extra work. No decision has been made yet.
- **Exceptions:** `BitsolveException` hierarchy covering the brief's 12 situations, with JSON errors `{timestamp,status,code,message,path}`.
- **Design patterns:** State, Strategy, Observer, Factory, Adapter, Chain of Responsibility, Repository (`docs/DESIGN_PATTERNS.md`).

## 4a. Workflow and navigation clean-up (22 Nov, morning)
- **Admin:** *Classify & Assign* is one step: confirm category, assess impact/urgency, "Assign a technician automatically" is ticked by default (frontend calls `classify` then `auto-assign`).
- **Technician:** one **Start Work** button (calls `acknowledge` then `start`), instead of two dialogs.
- **Admin menu:** Dashboard (shows "N new incidents to classify"), All Incidents, Insights & Analytics (+ status/priority/category tables, CSV), Requests (transfer + collaboration), Users & Technicians (accounts + skills/capacity/workload), IT Services, Incident Categories, SLA Policies, Assignment Strategies, Audit Logs (+ escalations), Notifications.
  - Removed: Manage Technicians, Collaboration/Assignment Requests as separate pages, Escalations, Reports & Analytics, Settings.
- **Technician menu:** Dashboard (availability + at-risk list), My Incidents (owned + collaborating), Requests, Notifications, Profile (+ workload). Removed: Assigned/Team/Investigation duplicates, My Workload.
- **Student menu:** Dashboard (= my incidents), Report Incident, Notifications, Profile.
- **Other UI changes:** the "Signed in as" band is now a compact sign-out row, and long guidance text was shortened.
- **CI:** `bits-itsm/scripts/smoke_flow.py` runs the main workflow end to end against the Docker app: report, classify+assign, start, transfer candidates, availability, resolve, verify.

## 4b. Practicality pass (22 Nov, morning)
- **NEW reports:** the student can withdraw one (with a reason) and the admin can close one as a duplicate or invalid. Both use `POST /api/incidents/{id}/withdraw` and NEW → CLOSED (`NewState`); these incidents have no resolution and are not counted as open.
- **AWAITING_INFO:** the primary technician can **Resume without reply** (`POST /{id}/resume`), so an incident is no longer stuck if the student never answers.
- **Disabling technicians:** a technician who still owns open incidents can't be disabled (409, "Reassign them first").
- **Passwords:**
  - Users change their own password on Profile (`POST /api/auth/password`).
  - The admin can reset one in Users & Technicians.
  - One `PasswordPolicy` (12+ characters, not the e-mail name) applies everywhere; admin-created accounts used to allow 8 characters.
  - The login page says to ask the admin for a reset.
- **Dashboards:**
  - Admin: "Classify now" opens All Incidents filtered to NEW; "N verified ready to close" opens a VERIFIED filter.
  - Technician: "N new assignments waiting for you to start", and a note when they go Busy/Unavailable while still owning open incidents.
- **Report form:** after submitting, it offers "View my incidents".
- **Tests:** TC60–TC64 added; `smoke_flow.py` now also covers the information request + resume and a withdrawn report.

## 4c. "Half-implemented" audit (22 Nov, morning)
- **Bug fixed:** closing any form inside an incident (transfer, collaborator, info request, note, resolution, request review) used to blank the incident window. The nested `<dialog>`'s close event bubbled to the parent's `onClose`; every dialog now ignores close events from its children.
- **New CI step `scripts/ui_crawl.mjs`** (Playwright against the Docker app). It logs in as student, technician and admin, opens every menu page, incident details and every action form (36 screens), and fails on:
  - any dropdown without real options;
  - any on-screen error;
  - any JavaScript error or failed API call;
  - an incident window left blank.
  - It publishes a `::notice` listing the screens it covered.
- **`smoke_flow.py` now also covers:**
  - collaboration request → admin approves → collaborator added;
  - transfer request → admin approves → primary changes;
  - e-mail actually arriving in MailHog.
- **Profile:** shows the student ID (`/api/auth/me` now includes `studentNumber`).

## 5. Demo-only configuration (important)
- `DemoUserSeeder`: 9 accounts. `DemoIncidentSeeder`: 60 incidents over 30 days. Both run only with profile `dev` and `bitsolve.demo.seed-enabled=true`.
- **`DemoShowcaseSeeder`** (new; `BITSOLVE_DEMO_SHOWCASE=true` is set in docker-compose):
  - **Why it exists:** in production, a new IT service stays **disabled until an admin sets its criticality**. That was the cause of the empty "Affected Service" dropdown.
  - **Services:** enables every unconfigured service with a criticality. HIGH: Wi-Fi, Network, Authentication, Student Portal. MEDIUM: Email, Lab Computers. LOW: Library, Other.
  - **Technician skills:** gives the 4 demo technicians 4–5 skills each, so every skill is held by at least 2 people. That was the cause of the empty transfer dropdown: a transfer target must have the incident's required skill.
  - It changes only untouched data.
  - Test: `DemoShowcaseSeederTest`.

## 6. Known gotchas / lessons
- **Hibernate:** never call `saveAndFlush` on an already-managed entity after adding cascaded children. It merges them and copies the new history row as an empty object. Use `flush()`. This only mattered in the merged `final-stage` version.
- **File names:** no two paths may differ only in letter case. Windows extraction breaks, and CI now enforces this. The team's old `docs/architecture.md` was renamed to `docs/ARCHITECTURE_OVERVIEW.md`.
- **Line endings:** `backend/.gitattributes` forces LF for `mvnw`, so a Windows clone still builds in Docker.
- **Test isolation:** Postgres tests share one database. `PostgresAuthenticationTests` counts only seeded demo accounts, because other tests register users.
- **Kush's laptop (Windows):**
  - Docker Desktop needed WSL. `wsl --install` gave 403 at first because the network blocked the download; the options were a hotspot, `--web-download`, the Microsoft Store, or the GitHub MSI. WSL then installed (with Ubuntu, which is harmless).
  - Extract the zip into an **empty** folder.

## 7. Open items / next steps
1. **Report PDF is stale.** `docs/report.md`, `report.html` and `[GROUP_NO]_OOP_Report.pdf` still have placeholders (`[GROUP_NO]`, `[NAME1..4]`) and still mention the old 7 intelligent features, suggested resolutions and student-chosen impact/urgency.
   - Needed: the group number and the four member names / IDs.
   - Then regenerate with `docs/build_report.py` (it assembles the docs into `report.md`/`report.html`), and print to PDF with headless Chromium/Playwright.
   - The source docs (README, REQUIREMENTS, PRIORITY_ALGORITHM, DEMO_SCRIPT, PLAN, DECISIONS, TEST_CASES, ARCHITECTURE + diagram) are already updated.
2. Fill in `[NAME…]` placeholders in `docs/TEAM.md`, `docs/DEMO_SCRIPT.md` and the README.
3. Decide with the team about Aditya's automatic classification: left out on purpose.
4. Optional: a real chat view between student and technician, if wanted.
5. Optional: open a PR from `kushgupta7/bitsolve` (or `oops:bitsolve-final`) to the team repo `adityagupta-bits/oops`, or submit from the `bitsolve` repo.
6. Kush should run it once locally (`docker compose down -v && docker compose up --build`) and click through `docs/DEMO_SCRIPT.md` before the demo.

## 8. Key files
- **Backend** (`bits-itsm/backend/src/main/java/com/bits/itsm/`):
  - `incident/`: `Incident`, `IncidentService`, `IncidentController`, `IncidentData`, `CatalogService`, `state/`.
  - `priority/`, `assignment/` (`AssignmentService`, `AssignmentController`, strategies, `WorkloadService`).
  - `sla/`, `escalation/`, `notification/` (+ `sms/`), `event/`, `audit/`, `intelligence/`.
  - `api/` (`InsightsService`, `ServicesController`, `AdminManagementController`).
  - `auth/`, `exception/`.
  - `user/` (seeders incl. `DemoShowcaseSeeder`, `Technician`, `UserFactory`).
- **Frontend** (`bits-itsm/frontend/src/`):
  - `ReportIncident.jsx`, `IncidentManagementActions.jsx` (admin classify with impact/urgency), `HomePanels.jsx` (availability toggle).
  - `pages/TechnicianOperations.jsx` (transfer/collaboration dialogs), `pages/TechnicianDashboard.jsx`, `pages/AdminDashboard.jsx`.
  - `MyIncidents.jsx`, `Insights.jsx`.
- **Config:**
  - `bits-itsm/docker-compose.yml`, `bits-itsm/Dockerfile`, `.github/workflows/ci.yml`.
  - `backend/src/main/resources/application*.properties`, `db/migration/V1–V8`.
