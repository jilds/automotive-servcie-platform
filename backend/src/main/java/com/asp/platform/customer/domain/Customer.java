package com.asp.platform.customer.domain;

import java.util.UUID;

import com.asp.platform.shared.StaleDataException;

/// A person or company that brings vehicles to the workshop.
public final class Customer {

    private final UUID id;
    private final long version;
    private ContactDetails contact;

    /// A new customer, not saved yet.
    public Customer(UUID id, ContactDetails contact) {
        this(id, contact, 0);
    }

    /// @param version how many times the stored record has changed; edits must name the version they read
    public Customer(UUID id, ContactDetails contact, long version) {
        this.id = id;
        this.contact = contact;
        this.version = version;
    }

    public void update(ContactDetails contact) {
        this.contact = contact;
    }

    /// @param expectedVersion the version the editor read
    /// @throws StaleDataException when someone else saved the customer since
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    public UUID id() {
        return id;
    }

    public long version() {
        return version;
    }

    public ContactDetails contact() {
        return contact;
    }
}
