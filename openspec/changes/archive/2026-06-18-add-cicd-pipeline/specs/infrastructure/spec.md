## ADDED Requirements

### Requirement: CI pipeline
The project SHALL provide a GitHub Actions CI workflow at `.github/workflows/ci.yml` that runs on every push (any branch) and every pull request to master. The workflow SHALL define two parallel jobs:

1. **`backend`** — JDK 17 + Maven, executes `mvn clean package -DskipTests` (compile verification), `mvn test -Dtest='!*IntegrationTest'` (unit tests), and `mvn test -Dtest='*IntegrationTest'` (integration tests). The integration tests SHALL run against MySQL 8 and Redis 6 GitHub Actions service containers, configured to match `src/test/resources/application-integration.yml` (`localhost:3306` with `root/root`, `localhost:6379`).
2. **`frontend`** — Node 18, executes `npm ci`, `npm run lint:check` (ESLint without `--fix`), and `npm run build`.

The workflow SHALL use `concurrency.cancel-in-progress: true` to skip superseded runs when a PR receives a new push. Maven (`~/.m2/repository`) and npm (`~/.npm`) caches SHALL be enabled to reduce run time.

The frontend `package.json` SHALL provide a separate `lint:check` script that runs ESLint without `--fix`, distinct from the existing `lint` script (which auto-fixes for local development).

#### Scenario: Backend unit tests run on push
- **WHEN** a developer pushes any commit to any branch
- **THEN** the `backend` job SHALL execute `mvn test -Dtest='!*IntegrationTest'`
- **AND** the job SHALL pass if all unit tests succeed

#### Scenario: Backend integration tests run with service containers
- **WHEN** the `backend` job executes the integration test step
- **THEN** MySQL 8 and Redis 6 service containers SHALL be started with passing healthchecks before the test step begins
- **AND** `mvn test -Dtest='*IntegrationTest'` SHALL connect to `localhost:3306` and `localhost:6379` and pass

#### Scenario: Frontend lint rejects unfixed violations
- **WHEN** a developer pushes code with ESLint violations
- **THEN** the `frontend` job SHALL fail at the `npm run lint:check` step
- **AND** the workflow SHALL NOT modify the developer's files (no `--fix` in CI)

#### Scenario: Frontend build verifies compilation
- **WHEN** the `frontend` job runs `npm run build`
- **THEN** Vite SHALL produce `dist/index.html`
- **AND** the step SHALL pass

#### Scenario: PR update cancels superseded run
- **WHEN** a developer pushes a new commit to an open PR
- **THEN** the previous in-flight CI run for that PR SHALL be canceled
- **AND** only the latest commit's run SHALL complete

#### Scenario: Backend job uses Maven cache
- **WHEN** the `backend` job starts
- **THEN** Maven dependencies from `~/.m2/repository` SHALL be cached across runs
- **AND** the cache key SHALL be derived from `hey-pickler-server/pom.xml`

#### Scenario: Frontend job uses npm cache
- **WHEN** the `frontend` job starts
- **THEN** npm dependencies from `~/.npm` SHALL be cached across runs
- **AND** the cache key SHALL be derived from `hey-pickler-admin/package-lock.json`

#### Scenario: CI workflow does not deploy
- **WHEN** either job completes successfully
- **THEN** the workflow SHALL NOT push artifacts, run deploy scripts, or modify production infrastructure
- **AND** deployment SHALL remain a manual operation via `deploy/scripts/install.sh` per `docs/RUNBOOK.md`
