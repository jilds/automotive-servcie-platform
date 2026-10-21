package com.asp.platform.customer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.asp.platform.ApiTestSupport;
import com.asp.platform.customer.domain.DuplicatePlateException;
import com.asp.platform.customer.domain.PlateFormats;
import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.customer.domain.VehicleDetails;
import com.asp.platform.customer.domain.VehicleRegistration;
import com.asp.platform.customer.domain.VehicleRepository;

/// Two requests saving the same plate at the same time both pass the service's "plate taken?" check. These
/// tests skip that check and save straight through the repository, as the second of the two requests would:
/// the unique constraint must surface as [DuplicatePlateException] (409 `duplicate-plate`), not a 500.
class VehiclePlateRaceTests extends ApiTestSupport {

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private PlateFormats plates;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_raise_duplicate_plate_when_a_concurrent_insert_took_the_plate() throws Exception {
        String owner = signup("Oficina Corrida A");
        UUID customerId = UUID.fromString(createCustomer(owner, "Ana"));
        createVehicle(owner, customerId.toString(), "12-AB-34");
        signIn(owner);

        Vehicle duplicate = Vehicle.register(UUID.randomUUID(), customerId, details("12AB34"),
                new VehicleRegistration("PT", null), null, plates);

        assertThatThrownBy(() -> inTransaction(() -> vehicles.save(duplicate)))
                .isInstanceOf(DuplicatePlateException.class)
                .hasMessageContaining("12AB34");
    }

    @Test
    void should_raise_duplicate_plate_when_a_concurrent_update_took_the_plate() throws Exception {
        String owner = signup("Oficina Corrida B");
        String customerId = createCustomer(owner, "Rui");
        createVehicle(owner, customerId, "12-AB-34");
        UUID otherId = UUID.fromString(createVehicle(owner, customerId, "56-CD-78"));
        signIn(owner);

        assertThatThrownBy(() -> inTransaction(() -> {
            Vehicle other = vehicles.findById(otherId).orElseThrow();
            other.edit(details("12AB34"), other.registration(), null, plates);
            return vehicles.save(other);
        })).isInstanceOf(DuplicatePlateException.class);

        assertThat(inTransaction(() -> vehicles.findById(otherId).orElseThrow()).details().plate())
                .isEqualTo("56CD78");
    }

    /// Binds the workshop for row-level security the way a request with this token would.
    private void signIn(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(_ -> work.get());
    }

    private static VehicleDetails details(String plate) {
        return new VehicleDetails(plate, null, "Renault", "Clio", 2019, null);
    }
}
