package com.asp.platform.shared;

/// Someone else saved the record after the caller read it: the caller's change would overwrite theirs. The client
/// reloads the record and tries again. Mapped to 409 `stale-data`.
public class StaleDataException extends ConflictException {

    public static final String CODE = "stale-data";

    public StaleDataException() {
        super(CODE, "Someone else changed this record; reload it and try again");
    }

    /// @param expected the version the caller read
    /// @param current  the version stored now
    /// @throws StaleDataException when they differ
    public static void check(long expected, long current) {
        if (expected != current) {
            throw new StaleDataException();
        }
    }
}
