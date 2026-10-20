# ASP – Automotive Service Platform

Multi-tenant workshop ERP for Portuguese repair shops. This is the release 1 foundation: workshops sign up, register customers and vehicles, check cars in, and move work orders across the Kanban board. Mechanics get a phone-friendly view of their own jobs without prices.

One repository, one Maven build: the Angular app is packaged inside the Spring Boot jar, so a single jar serves the API and the UI on port 8080. Project docs (scope, architecture, step notes) are in [`../docs`](../docs); architecture decisions are in [`docs/adr`](docs/adr).

## Run

You need **JDK 26** and **Docker**. Maven and Node are not needed globally: the Maven wrapper is in the repo and the build installs a pinned Node into `frontend/target`.

```bash
# Build everything and run all tests (backend tests need Docker for Testcontainers)
./mvnw clean verify

# Packaged: database + one jar serving API and UI on http://localhost:8080
docker compose up -d
java -jar backend/target/backend-0.1.0-SNAPSHOT.jar
```

Hot reload while developing:

```bash
./mvnw -pl backend spring-boot:run      # starts compose.yaml, API on :8080
cd frontend && npm install && npm start # UI on http://localhost:4200, proxies /api and /actuator to :8080
```

Or run `TestAspApplication` (backend test sources) from the IDE: it starts the app against a throw-away PostgreSQL, no compose needed.

Open the app, choose **Create an account**, and you are the owner of a new workshop.

Single-side builds: `./mvnw -pl backend verify`, `./mvnw -pl frontend verify`.

## Layout

```
pom.xml                 aggregator: frontend, then backend
compose.yaml            local PostgreSQL 18.6 (127.0.0.1:5432), roles from compose/postgres/init.sql
docs/adr/               architecture decisions
backend/                Spring Boot 4.1, Java 26
  src/main/java/com/asp/platform/
    config/             app-wide wiring: security, persistence (tenancy), web, time
    shared/             shared domain types: Role, CurrentUser, base exceptions
    identity/           signup, login, users          ┐ each feature is a Spring Modulith module:
    customer/           customers and vehicles        │ public API in the module root,
    workshop/           work orders, board, history   ┘ api / application / domain / infrastructure inside
  src/main/resources/   application.yml, db/migration (Flyway)
  src/test/java/…       per-feature tests, ModularityTests, LayerRulesTests, TestcontainersConfiguration
frontend/               Angular 22 PWA
  src/app/
    core/api/           typed API client and models
    core/i18n/          languages (pt-PT default, en, pt-BR), translate pipe, API error codes → messages
    core/auth/          session, token interceptor, route guards
    shared/             money pipe, language switcher, app shell
    identity/ customer/ workshop/   pages, same names as the backend features
```

Rules the build enforces: `ModularityTests` (no module reaches into another module's internals, no cycles) and `LayerRulesTests` (api → application → domain ← infrastructure, no Spring or JPA in the domain, JPA entities only in infrastructure, `@Transactional` only in application, features never import `config`).

## How tenant isolation works

Every domain table has a `tenant_id`. The app connects as `asp_app`, which cannot bypass row-level security, and sets `app.tenant_id` from the signed-in user's token on every connection (`config/persistence`). PostgreSQL then only returns that workshop's rows, even if a query forgets to filter, and fills `tenant_id` on insert. Migrations run as `asp_owner`. `tenant` and `app_user` are read before login, so they are filtered in code instead. See [ADR 0002](docs/adr/0002-tenant-isolation-with-row-level-security.md).

## Languages

The UI is in Portuguese (Portugal) by default, with English and Portuguese (Brazil) in the switcher on the top bar and login page. The choice is remembered in the browser. Texts live in `frontend/src/app/core/i18n/messages.*.ts`; English defines the keys, and the build fails if another language misses one.

## Configuration

| Variable | Default (dev only) |
| --- | --- |
| `ASP_DB_URL` | `jdbc:postgresql://localhost:5432/asp` |
| `ASP_DB_APP_USER` / `ASP_DB_APP_PASSWORD` | `asp_app` / `asp_app` |
| `ASP_DB_OWNER_USER` / `ASP_DB_OWNER_PASSWORD` | `asp_owner` / `asp_owner` |
| `ASP_JWT_SECRET` | a dev secret; set a random 32+ character value in any real environment |

`/actuator/health` and `/actuator/info` (version, build time, git commit) are open; OpenAPI docs are at `/swagger-ui.html`.

## API

Errors are RFC 9457 problem details with a stable `type` and `code` (for example `duplicate-plate`), which the UI translates; validation errors list the failing fields under `errors` (nested fields as `address.postalCode`).

A customer's address is optional. When it is sent, `street`, `postalCode` and `locality` are required, `detail` (floor, door) is optional, and `country` is a two-letter code that defaults to `PT`. Portuguese postal codes must look like `1100-053`.

| Method | Path | Who |
| --- | --- | --- |
| POST | `/api/v1/auth/signup`, `/api/v1/auth/login` | anyone |
| GET, POST | `/api/v1/users` | Owner |
| GET, POST, PUT | `/api/v1/customers`, `/api/v1/customers/{id}`, `/api/v1/customers/{id}/vehicles` | Owner, Front desk |
| POST | `/api/v1/vehicles` | Owner, Front desk |
| GET | `/api/v1/vehicles?plate=`, `/api/v1/vehicles/{id}`, `/api/v1/vehicles/{id}/work-orders` | everyone |
| POST | `/api/v1/work-orders` (check in) | Owner, Front desk |
| GET | `/api/v1/work-orders/board`, `/api/v1/work-orders/{id}` | everyone (mechanics: own jobs) |
| POST, DELETE | `/api/v1/work-orders/{id}/lines` | Owner, Front desk |
| PATCH | `/api/v1/work-orders/{id}/status` | everyone (mechanics: In progress, Awaiting parts, Ready) |
| PUT | `/api/v1/work-orders/{id}/mechanic` | Owner, Front desk |
