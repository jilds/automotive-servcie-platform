package com.asp.platform.customer.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.asp.platform.customer.domain.Customer;
import com.asp.platform.customer.domain.CustomerRepository;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaCustomerRepository implements CustomerRepository {

    private final SpringDataCustomerRepository jpa;

    JpaCustomerRepository(SpringDataCustomerRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Customer save(Customer customer) {
        return Upserts.upsert(jpa, customer.id(), customer, CustomerEntity::new);
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return jpa.findById(id).map(CustomerEntity::toDomain);
    }

    @Override
    public List<Customer> search(@Nullable String query) {
        return jpa.search(query).stream().map(CustomerEntity::toDomain).toList();
    }
}
