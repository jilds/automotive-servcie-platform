package com.asp.platform.customer.domain;

import java.util.UUID;

/// A person or company that brings vehicles to the workshop.
public final class Customer {

    private final UUID id;
    private ContactDetails contact;

    public Customer(UUID id, ContactDetails contact) {
        this.id = id;
        this.contact = contact;
    }

    public void update(ContactDetails contact) {
        this.contact = contact;
    }

    public UUID id() {
        return id;
    }

    public ContactDetails contact() {
        return contact;
    }
}
