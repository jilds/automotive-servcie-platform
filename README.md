# ASP – Automotive Service Platform

Multi-tenant workshop ERP for Portuguese repair shops. This is the release 1 foundation: workshops sign up, register customers and vehicles, check cars in, and move work orders across the Kanban board. Mechanics get a phone-friendly view of their own jobs without prices.

Project docs (scope, architecture, step notes) are in [`../docs`](../docs).

## What is in this foundation

| Area | Included |
| --- | --- |
| Backend | Java 26, Spring Boot 4.1, Spring Modulith modules (identity, customers, workshop, shared) |
| Database | PostgreSQL 17, Flyway migrations, row-level security per workshop |
| Security | Signup and login with JWT; roles Owner, Front desk, Mechanic; mechanics never receive prices |
| API | `/api/v1` REST, OpenAPI docs at `/swagger-ui.html` |
| Frontend | Angular 22 standalone app, installable PWA, works on desktop and phone |
| Tests | Backend integration tests on a real PostgreSQL (Testcontainers), module boundary test, Angular unit tests |

## Run it locally

You need **JDK 26**, **Node.js 24** (or 22.22.3+), and **Docker Desktop**.

```bash
# 1. Database (creates the asp database and the asp_owner / asp_app roles)
docker compose up -d

# 2. Backend on http://localhost:8080
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 3. Frontend on http://localhost:4200 (proxies /api to the backend)
cd web
npm install
npm start
```

Open http://localhost:4200, choose **Create an account**, and you are the owner of a new workshop.

## Run the tests

```bash
cd backend && ./mvnw test      # needs Docker running for Testcontainers
cd web && npx ng test --watch=false
```

## How tenant isolation works

Every domain table has a `tenant_id`. The app connects as `asp_app`, which cannot bypass row-level security, and sets `app.tenant_id` from the signed-in user's token on every connection. PostgreSQL then only returns that workshop's rows, even if a query forgets to filter. Migrations run as `asp_owner`. `tenant` and `app_user` are read before login, so they are filtered in code instead.

## Configuration

| Variable | Default (dev only) |
| --- | --- |
| `ASP_DB_URL` | `jdbc:postgresql://localhost:5432/asp` |
| `ASP_DB_APP_USER` / `ASP_DB_APP_PASSWORD` | `asp_app` / `asp_app` |
| `ASP_DB_OWNER_USER` / `ASP_DB_OWNER_PASSWORD` | `asp_owner` / `asp_owner` |
| `ASP_JWT_SECRET` | a dev secret; set a random 32+ byte value in any real environment |

## API overview

| Method | Path | Who |
| --- | --- | --- |
| POST | `/api/v1/auth/signup`, `/api/v1/auth/login` | anyone |
| GET, POST | `/api/v1/users` | Owner |
| GET, POST, PUT | `/api/v1/customers`, `/api/v1/customers/{id}` | Owner, Front desk |
| POST | `/api/v1/vehicles` | Owner, Front desk |
| GET | `/api/v1/vehicles?plate=`, `/api/v1/vehicles/{id}`, `/api/v1/vehicles/{id}/work-orders` | everyone |
| POST | `/api/v1/work-orders` (check in) | Owner, Front desk |
| GET | `/api/v1/work-orders/board`, `/api/v1/work-orders/{id}` | everyone (mechanics: own jobs) |
| POST, DELETE | `/api/v1/work-orders/{id}/lines` | Owner, Front desk |
| PATCH | `/api/v1/work-orders/{id}/status` | everyone (mechanics: In progress, Awaiting parts, Ready) |
| PUT | `/api/v1/work-orders/{id}/mechanic` | Owner, Front desk |
