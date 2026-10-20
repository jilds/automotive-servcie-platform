package com.asp.platform.customer.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Row-level security limits every query to the current workshop.
public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID id);

    /// Matches name, NIF or phone; everything when the query is null.
    List<Customer> search(@Nullable String query);
}
