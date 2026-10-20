package com.asp.customers.internal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.asp.shared.tenancy.TenantScopedEntity;

@Entity
@Table(name = "customer")
class Customer extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String nif;
    private String phone;
    private String email;
    private String address;

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    protected Customer() {
    }

    Customer(String name, String nif, String phone, String email, String address, boolean marketingConsent) {
        update(name, nif, phone, email, address, marketingConsent);
    }

    void update(String name, String nif, String phone, String email, String address, boolean marketingConsent) {
        this.name = name;
        this.nif = nif;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.marketingConsent = marketingConsent;
    }

    UUID getId() { return id; }
    String getName() { return name; }
    String getNif() { return nif; }
    String getPhone() { return phone; }
    String getEmail() { return email; }
    String getAddress() { return address; }
    boolean isMarketingConsent() { return marketingConsent; }
}
