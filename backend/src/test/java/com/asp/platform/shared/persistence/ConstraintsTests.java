package com.asp.platform.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintsTests {

    private static final String SKU = "product_tenant_sku_key";
    private static final String PLATE = "vehicle_tenant_id_plate_key";

    @Test
    void should_find_the_constraint_named_by_hibernate_in_the_cause_chain() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
                        "PRODUCT_TENANT_SKU_KEY"));

        assertThat(Constraints.violated(e, SKU)).isTrue();
        assertThat(Constraints.violated(e, PLATE)).isFalse();
    }

    @Test
    void should_find_the_constraint_in_the_database_message_when_hibernate_names_none() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException(new SQLException(
                        "ERROR: duplicate key value violates unique constraint \"Vehicle_Tenant_Id_Plate_Key\"")));

        assertThat(Constraints.violated(e, PLATE)).isTrue();
        assertThat(Constraints.violated(e, SKU)).isFalse();
    }

    @Test
    void should_not_find_a_constraint_when_no_cause_names_one() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("value too long");

        assertThat(Constraints.violated(e, SKU)).isFalse();
    }
}
