package com.asp.platform.workshop.domain;

import java.util.Optional;
import java.util.UUID;

/// Where the bytes of the photos are kept. The database is the only implementation today; an object store (S3) can
/// replace it without touching the API. Implementations run in the caller's transaction and are limited to the
/// current workshop by row-level security.
public interface PhotoStore {

    void put(UUID photoId, byte[] bytes);

    Optional<byte[]> get(UUID photoId);

    void delete(UUID photoId);
}
