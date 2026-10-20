package com.asp.identity.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A workshop subscribed to ASP. */
@Entity
@Table(name = "tenant")
class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String nif;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Tenant() {
    }

    Tenant(String name, String nif, String country, String timezone) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.nif = nif;
        this.country = country;
        this.timezone = timezone;
        this.createdAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getCountry() {
        return country;
    }
}
