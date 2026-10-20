# 0001. Full-stack layout and package-by-feature structure

- Status: accepted
- Date: 2026-10-20

## Context

ASP ships a Spring Boot API and an Angular app that release together. The team's agent kit (Package Structure standard, dimensions FEAT, LAYER, MOD, CFG and FULL) defines the layout for a "full stack, one project" solution, with electricity-analyzer as the reference.

## Decision

- One Maven reactor: a root aggregator `pom.xml` with modules `frontend` then `backend`. `./mvnw clean verify` is the only build entry point.
- `frontend/pom.xml` drives the Angular build with `frontend-maven-plugin` 2.0.2 and a pinned Node v24.16.0 / npm 11.16.0 installed into `frontend/target`. `npm test` runs in the `test` phase, `npm run build` in `prepare-package`.
- The backend packages `../frontend/dist` as `static/`; `SpaForwardingController` forwards Angular routes to `index.html`. Build info and the git commit show up in `/actuator/info`.
- Backend packages are by feature: `identity`, `customer`, `workshop`, each a Spring Modulith module with `api`, `application`, `domain` and `infrastructure` inside. App-wide wiring sits in `config/<concern>`; `shared` holds shared domain types only.
- Every feature keeps a framework-free domain and separate JPA entities in `infrastructure`, including the simpler CRUD features, so one set of ArchUnit rules covers the whole codebase and no feature needs the JPA-on-domain escape hatch.
- The frontend uses `core/api`, `core/auth`, `shared` and one folder per backend feature with the same name.

## Consequences

- Module and layer boundaries fail the build when broken (`ModularityTests`, `LayerRulesTests`).
- More mapping code than JPA-annotated domain classes (an entity and an adapter per aggregate). Accepted for clear boundaries; revisit per feature only with a new ADR.
- Cross-module calls go through a facade (`CustomerManagement`). Release 2 should prefer domain events where a module only needs to react.
