package com.asp.platform.catalogue.domain;

import java.util.UUID;

import com.asp.platform.shared.StaleDataException;

/// A standard service of the workshop, such as "Oil change" or "Brake pads, front axle".
public final class ServiceItem {

    private final UUID id;
    private final long version;
    private ServiceDetails details;

    /// A new service, not saved yet.
    public ServiceItem(UUID id, ServiceDetails details) {
        this(id, details, 0);
    }

    /// @param version how many times the stored record has changed; edits name the version they read
    public ServiceItem(UUID id, ServiceDetails details, long version) {
        this.id = id;
        this.details = details;
        this.version = version;
    }

    public void update(ServiceDetails newDetails) {
        this.details = newDetails;
    }

    /// @throws StaleDataException when someone else saved the service since the editor read it
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    /// @throws ServiceInactiveException when the service may no longer be added to work orders or packages
    public void requireActive() {
        if (!details.active()) {
            throw new ServiceInactiveException();
        }
    }

    public UUID id() {
        return id;
    }

    public long version() {
        return version;
    }

    public ServiceDetails details() {
        return details;
    }
}
