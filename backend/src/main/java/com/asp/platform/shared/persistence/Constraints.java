package com.asp.platform.shared.persistence;

import java.sql.SQLException;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;

/// Tells which database constraint a failed write broke, so an adapter can turn a unique-constraint race into its
/// coded 409.
public final class Constraints {

    private Constraints() {
    }

    /// Whether the named constraint caused the failure: Hibernate's constraint name, or else the database message.
    ///
    /// @param constraintName the constraint name; case is ignored
    public static boolean violated(DataIntegrityViolationException e, String constraintName) {
        String name = constraintName.toLowerCase(Locale.ROOT);
        for (@Nullable Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && name.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            if (cause instanceof SQLException sql && sql.getMessage() != null
                    && sql.getMessage().toLowerCase(Locale.ROOT).contains(name)) {
                return true;
            }
        }
        return false;
    }
}
