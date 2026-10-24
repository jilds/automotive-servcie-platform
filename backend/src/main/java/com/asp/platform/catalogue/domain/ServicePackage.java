package com.asp.platform.catalogue.domain;

import java.util.UUID;

import com.asp.platform.shared.StaleDataException;

/// Services sold together for one price.
public final class ServicePackage {

    private final UUID id;
    private final long version;
    private PackageDetails details;

    /// A new package, not saved yet.
    public ServicePackage(UUID id, PackageDetails details) {
        this(id, details, 0);
    }

    /// @param version how many times the stored record has changed; edits name the version they read
    public ServicePackage(UUID id, PackageDetails details, long version) {
        this.id = id;
        this.details = details;
        this.version = version;
    }

    public void update(PackageDetails newDetails) {
        this.details = newDetails;
    }

    /// @throws StaleDataException when someone else saved the package since the editor read it
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    /// @throws PackageInactiveException when the package may no longer be added to work orders
    public void requireActive() {
        if (!details.active()) {
            throw new PackageInactiveException();
        }
    }

    public UUID id() {
        return id;
    }

    public long version() {
        return version;
    }

    public PackageDetails details() {
        return details;
    }
}
