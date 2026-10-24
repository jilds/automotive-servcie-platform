# Plan — One generic save for the `Jpa*Repository` adapters

Status: **done 2026-10-24** (agreed 2026-10-23). Behaviour-preserving refactor: 15 `Jpa*Repository` adapters, 12 moved to
`Upserts`, 3 left as they are (see the inventory). Proposed commit: `refactor(persistence): share the upsert of the Jpa adapters`.

## Why

Every aggregate has a framework-free domain object and a JPA entity in `infrastructure` (ADR 0001). Twelve of the
fifteen `Jpa*Repository` adapters therefore repeated the same `save`:

```java
XEntity entity = jpa.findById(x.id())
        .map(existing -> { existing.apply(x); return existing; })
        .orElseGet(() -> jpa.save(new XEntity(x)));
jpa.flush();               // so the returned version is the stored one (optimistic locking)
return entity.toDomain();
```

The copies had drifted: some flush, some do not, two use `saveAndFlush`, two wrap the call to turn a unique-constraint
violation into a coded 409, each with its own copy of the constraint-name check. One helper gives a single place for the
flush/version rule, so a new aggregate gets optimistic locking right by default.

## Inventory (2026-10-23, done 2026-10-24)

| Adapter | Today | After |
|---|---|---|
| `catalogue/JpaServiceItemRepository` | upsert + flush | helper |
| `catalogue/JpaServicePackageRepository` | upsert + flush (entity syncs its items in `apply`) | helper |
| `customer/JpaCustomerRepository` | upsert + flush | helper |
| `identity/JpaTenantRepository` | upsert + flush | helper |
| `identity/JpaUserRepository` | upsert + flush | helper |
| `stock/JpaReservationRepository` | upsert + flush (next "reserved?" query must see it) | helper |
| `workshop/JpaWorkOrderRepository` | upsert, `saveAndFlush` (entity syncs its lines) | helper |
| `stock/JpaPurchaseOrderRepository` | upsert, `saveAndFlush` (entity syncs its lines) | helper |
| `customer/JpaVehicleRepository` | upsert + flush in try/catch → `duplicate-plate` | helper inside its try/catch + shared constraint check |
| `stock/JpaProductRepository` | upsert + flush in try/catch → `duplicate-sku` | helper inside its try/catch + shared constraint check |
| `identity/JpaActivationTokenRepository` | upsert, **no flush** | helper, flush off (keep today's SQL timing) |
| `identity/JpaPlatformAdminRepository` | upsert, **no flush** | helper, flush off |
| `identity/JpaTenantLogoRepository` | `void save(tenantId, logo)`, keyed by tenant | **leave as is** (different shape) |
| `stock/JpaStockMovementRepository` | `void add(movement)`, insert only (append-only ledger) | **leave as is** (insert only) |
| `workshop/JpaMechanicChangeRepository` | `jpa.save(new …)`, insert only (changes are never edited) | **leave as is** (insert only) |

`saveAndFlush(entity)` and `findById → apply / save(new) → flush` behave the same for these entities (managed ones are
updated in place; a new one goes through `save`), so the two `saveAndFlush` adapters moved to the helper; their
existing tests pass unchanged.

Six top-level entities have no `@Version` (activation token, platform admin, stock reservation, tenant logo, stock movement,
mechanic change). Their id is assigned by the domain, so Spring Data cannot tell they are new and **merges** them
instead of persisting: `save` returns a managed copy, not the instance passed in. `Upserts` therefore reads a new entity
back from what `save` returns, as the adapters did before.

## Design

New package `com.asp.platform.shared.persistence` (JPA/Spring Data allowed; infrastructure only):

```java
/// A JPA entity that mirrors one domain object. Entities already have both methods; they only add `implements`.
public interface DomainEntity<D> {

    /// Copies the domain state onto this managed entity (children included, in place).
    void apply(D domain);

    D toDomain();
}

/// The insert-or-update every adapter repeats.
public final class Upserts {

    /// Updates the stored entity, or persists a new one, then flushes so the returned domain object carries the
    /// stored `version` and constraint violations surface here instead of at commit.
    public static <D, E extends DomainEntity<D>, ID> D upsert(JpaRepository<E, ID> jpa, ID id, D domain,
            Function<? super D, ? extends E> newEntity) {
        return upsert(jpa, id, domain, newEntity, true);
    }

    /// Same, without the flush (adapters whose callers rely on the write happening at commit).
    public static <D, E extends DomainEntity<D>, ID> D upsertWithoutFlush(JpaRepository<E, ID> jpa, ID id, D domain,
            Function<? super D, ? extends E> newEntity) {
        return upsert(jpa, id, domain, newEntity, false);
    }

    private static <D, E extends DomainEntity<D>, ID> D upsert(JpaRepository<E, ID> jpa, ID id, D domain,
            Function<? super D, ? extends E> newEntity, boolean flush) { … }
}

/// Finds a named unique constraint in a `DataIntegrityViolationException` (Hibernate's constraint name, or the
/// PostgreSQL message as a fallback): the logic `JpaVehicleRepository` and `JpaProductRepository` each copy today.
public final class Constraints {
    public static boolean violated(DataIntegrityViolationException e, String constraintName) { … }
}
```

An adapter then reads:

```java
@Override
public Customer save(Customer customer) {
    return Upserts.upsert(jpa, customer.id(), customer, CustomerEntity::new);
}
```

Composition over a base class: adapters keep implementing their domain port and keep their other queries; no generic
abstract repository, no change to the `SpringDataX` interfaces.

**Module rules.** `shared` is a Spring Modulith module whose root package is its public API; a sub-package is
internal. Publish `shared.persistence` with `@NamedInterface("persistence")` in its `package-info.java` (plus
`@NullMarked`), so `ModularityTests` accepts the dependency from each module's `infrastructure`.

**Layer rules.** Add to `LayerRulesTests`: only classes in `com.asp.platform.*.infrastructure..` (and the package itself) may
depend on `com.asp.platform.shared.persistence..` (keeps JPA out of domain, application and api, which also do not see it
through `shared`).

**Docs.** CLAUDE.md §3: `shared` gains `shared.persistence` (JPA helpers for infrastructure only); the adapter pattern
for new aggregates is "entity implements `DomainEntity`, adapter calls `Upserts.upsert`". Keep the §3 note that
`shared` stays small.

## Steps

0. Characterization test first: `stock/infrastructure/ProductSkuRaceTests` (insert and update race on the SKU →
   `DuplicateSkuException`), green on the old code.
1. Add `DomainEntity`, `Upserts`, `Constraints` + `package-info` (`@NamedInterface`, `@NullMarked`).
2. `UpsertsTests` on the Testcontainers database through real aggregates, not a test entity (`ddl-auto: validate`
   would fail the shared Spring context): the versioned service item (new → version 0, written before commit;
   update → version + 1) and the versionless platform admin (insert and update; without flush the row appears only
   at commit). `ConstraintsTests` as a plain unit test (named constraint found through Hibernate's
   `ConstraintViolationException` and through the `SQLException` message; another constraint not).
3. Migrate the adapters one module at a time (customer, identity, catalogue, stock, workshop): add `implements
   DomainEntity<X>` to the entity, replace the `save` body, move the vehicle/product constraint checks to
   `Constraints.violated`. Run `./mvnw -q -pl backend verify` after each module.
4. Add the layer rule; update CLAUDE.md §3.
5. Full `./mvnw -q clean verify` (on a copy while the IntelliJ app uses `backend/target`).

## Acceptance

- No behaviour change: every existing backend test passes unchanged (optimistic-locking tests `stale-data`, plate/SKU
  races → 409, work order and package child sync, reservation visibility).
- `ModularityTests` and `LayerRulesTests` green, including the new rule.
- Each migrated `save` is a single statement (vehicle/product: the call inside their existing try/catch).
- `JpaTenantLogoRepository`, `JpaStockMovementRepository`, `JpaMechanicChangeRepository` unchanged, each with a one-line
  comment why.

## Effort and ROI

About 0.5–1 day. Saves ~5–8 lines in each of 12 adapters and two copies of the constraint check; the main value is
one enforced pattern for new aggregates (booking and shop add more). Risk low: covered by the existing API tests.
Recommendation: **plan next sprint**, after the booking/shop modules' first adapters exist, so they are migrated with
the rest.

## Result (2026-10-24)

Done as planned, with the corrections above (three adapters left, versionless entities merged, real-aggregate tests,
entity `apply` / `toDomain` made public for the interface, no `@Transactional` in `shared.persistence`). Every
existing backend test passes unchanged: `./mvnw clean verify` → BUILD SUCCESS, 303 backend tests, 0 failures
(new: `ProductSkuRaceTests` 2, `UpsertsTests` 3, `ConstraintsTests` 3, one `LayerRulesTests` rule).
