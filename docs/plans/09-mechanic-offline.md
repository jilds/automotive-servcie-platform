# 09: Mechanic offline mode

Status: **planned**. Commit `feat(pwa): let mechanics keep working offline`.

## Today

- `ngsw-config.json` caches the app shell and fonts, but has no `dataGroups`.
- The service worker is registered, but nothing uses `SwUpdate`.
- There is no IndexedDB code.

## Build

- **`dataGroups`:** the mechanic's board and the work orders they open, using a freshness strategy with a short
  timeout and a fallback to the cache.
- **Outbox:** a small IndexedDB wrapper, with no new library unless needed. It queues:
  - timer start and stop
  - checklist results
  - photo uploads (stored as blobs until sent)
- **Idempotency:**
  - Each queued call sends an `Idempotency-Key` header.
  - The backend keeps the keys for 24 h per workshop in a table with RLS. A repeated key returns the first answer.
  - A cleanup job runs with ShedLock.
- **Replay:** the outbox replays on `online` and at start-up. A 409 `stale-data` reloads the order and shows
  `error.stale-data`.
- **Banner:** "Offline — N changes pending", in three languages.
- **Updates:** a `SwUpdate` prompt, "Nova versão disponível", with a reload button.

## Tests

- **Unit:** the outbox and the replay order.
- **Backend:** the idempotency filter.
- **Manual:** DevTools offline, at 390 px. Start a timer offline, tick the checklist, take a photo, go back online, and
  check the server state.
