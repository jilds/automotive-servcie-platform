package com.asp.platform.workshop.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.workshop.domain.PhotoStore;

/// Keeps the bytes of the photos in `work_order_photo_data` (bytea), in the caller's transaction. Row-level security
/// limits every statement to the current workshop and `tenant_id` comes from the connection. The metadata row
/// (`work_order_photo`) must exist first: the bytes reference it.
@Repository
class DbPhotoStore implements PhotoStore {

    private final JdbcClient jdbc;

    DbPhotoStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void put(UUID photoId, byte[] bytes) {
        jdbc.sql("insert into work_order_photo_data (photo_id, data) values (:id, :data)")
                .param("id", photoId)
                .param("data", bytes)
                .update();
    }

    @Override
    public Optional<byte[]> get(UUID photoId) {
        return jdbc.sql("select data from work_order_photo_data where photo_id = :id")
                .param("id", photoId)
                .query(byte[].class)
                .optional();
    }

    @Override
    public void delete(UUID photoId) {
        jdbc.sql("delete from work_order_photo_data where photo_id = :id").param("id", photoId).update();
    }
}
