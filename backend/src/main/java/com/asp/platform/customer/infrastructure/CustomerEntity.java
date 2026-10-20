package com.asp.platform.customer.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.Address;
import com.asp.platform.customer.domain.ContactDetails;
import com.asp.platform.customer.domain.Customer;

/// `tenant_id` is not mapped: the database fills it from the connection's workshop and row-level security
/// checks it on every read and write.
@Entity
@Table(name = "customer")
class CustomerEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private @Nullable String nif;
    private @Nullable String phone;
    private @Nullable String email;

    @Column(name = "address_street")
    private @Nullable String addressStreet;

    @Column(name = "address_detail")
    private @Nullable String addressDetail;

    @Column(name = "postal_code")
    private @Nullable String postalCode;

    private @Nullable String locality;

    @Column(length = 2)
    private @Nullable String country;

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected CustomerEntity() {
    }

    CustomerEntity(Customer customer) {
        this.id = customer.id();
        apply(customer);
    }

    void apply(Customer customer) {
        ContactDetails c = customer.contact();
        this.name = c.name();
        this.nif = c.nif();
        this.phone = c.phone();
        this.email = c.email();
        Address a = c.address();
        this.addressStreet = a == null ? null : a.street();
        this.addressDetail = a == null ? null : a.detail();
        this.postalCode = a == null ? null : a.postalCode();
        this.locality = a == null ? null : a.locality();
        this.country = a == null ? null : a.country();
        this.marketingConsent = c.marketingConsent();
    }

    Customer toDomain() {
        return new Customer(id, new ContactDetails(name, nif, phone, email, address(), marketingConsent));
    }

    /// Rows migrated from the old free-text address may have only a street; they read as no address
    /// until someone completes it.
    private @Nullable Address address() {
        if (addressStreet == null || postalCode == null || locality == null) {
            return null;
        }
        return new Address(addressStreet, addressDetail, postalCode, locality,
                country == null ? Address.PORTUGAL : country);
    }
}
