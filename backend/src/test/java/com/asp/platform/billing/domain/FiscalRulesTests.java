package com.asp.platform.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.asp.platform.billing.domain.FiscalRules.Party;
import com.asp.platform.billing.domain.FiscalRules.PartyAddress;
import com.asp.platform.shared.InvalidInputException;

class FiscalRulesTests {

    private static final long LIMIT = 100_000;
    private static final long BELOW = 7_688;
    private static final long ABOVE = 200_000;
    private static final PartyAddress PT_ADDRESS = new PartyAddress("Rua A, 1", null, "1100-053", "Lisboa", "PT");

    private static CustomerSnapshot customer(boolean finalConsumer, Party override, Party owner, String country,
            long total) {
        return FiscalRules.customerFor(finalConsumer, override, owner, country, total, LIMIT);
    }

    @Test
    void should_check_the_nif_before_asking_for_a_nif_or_an_address() {
        // Above the limit with a bad NIF and no address: the NIF error comes first.
        assertThatThrownBy(() -> customer(false, new Party("Rui", "123456788", null), null, "PT", ABOVE))
                .isInstanceOf(InvalidCustomerNifException.class);
        // No NIF above the limit: the NIF is asked for before the address.
        assertThatThrownBy(() -> customer(false, new Party("Rui", null, null), null, "PT", ABOVE))
                .isInstanceOf(CustomerNifRequiredException.class);
        assertThatThrownBy(() -> customer(false, new Party("Rui", null, PT_ADDRESS), null, "PT", ABOVE))
                .isInstanceOf(CustomerNifRequiredException.class);
        assertThatThrownBy(() -> customer(false, new Party("Rui", "123456789", null), null, "PT", ABOVE))
                .isInstanceOf(CustomerAddressRequiredException.class);
        assertThat(customer(false, new Party("Rui", "123456789", PT_ADDRESS), null, "PT", ABOVE).nif())
                .isEqualTo("123456789");
    }

    @Test
    void should_accept_no_nif_and_no_address_up_to_the_limit() {
        CustomerSnapshot snapshot = customer(false, new Party("Rui", " ", null), null, "PT", LIMIT);

        assertThat(snapshot.nif()).isNull();
        assertThat(snapshot.address()).isNull();
        assertThat(snapshot.finalConsumer()).isFalse();
    }

    @Test
    void should_not_apply_the_portuguese_nif_rule_to_another_country() {
        // A Brazilian workshop, and a Brazilian customer of a Portuguese workshop: the NIF is stored as entered.
        assertThat(customer(false, new Party("Joao", "12345678000195", null), null, "BR", BELOW).nif())
                .isEqualTo("12345678000195");
        PartyAddress brazil = new PartyAddress("Rua B, 2", null, "01310-100", "Sao Paulo", "BR");
        assertThat(customer(false, new Party("Joao", "12345678000195", brazil), null, "PT", BELOW).address().country())
                .isEqualTo("BR");
    }

    @Test
    void should_use_the_workshop_country_for_an_address_without_one() {
        PartyAddress noCountry = new PartyAddress("Rua A, 1", null, "1100-053", "Lisboa", null);

        assertThat(customer(false, new Party("Rui", "123456789", noCountry), null, "PT", BELOW).address().country())
                .isEqualTo("PT");
        // Not Portugal: the Portuguese postal code format is not imposed.
        PartyAddress other = new PartyAddress("Rua B, 2", null, "01310", "Sao Paulo", null);
        assertThat(customer(false, new Party("Joao", null, other), null, "BR", BELOW).address().country())
                .isEqualTo("BR");
    }

    @Test
    void should_check_the_postal_code_of_a_portuguese_address() {
        PartyAddress bad = new PartyAddress("Rua A, 1", null, "1100", "Lisboa", null);

        assertThatThrownBy(() -> customer(false, new Party("Rui", null, bad), null, "PT", BELOW))
                .isInstanceOfSatisfying(InvalidInputException.class, e -> {
                    assertThat(e.code()).isEqualTo("validation");
                    assertThat(e.field()).isEqualTo("customer.address.postalCode");
                });
        assertThatThrownBy(() -> customer(false, new Party("Rui", null,
                new PartyAddress("Rua A, 1", null, "1100", "Lisboa", "PT")), null, "BR", BELOW))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void should_ignore_every_other_party_for_a_final_consumer() {
        Party override = new Party("Rui", "123456788", PT_ADDRESS);
        Party owner = new Party("Ana", "123456789", PT_ADDRESS);

        CustomerSnapshot snapshot = customer(true, override, owner, "PT", BELOW);

        assertThat(snapshot.name()).isEqualTo(CustomerSnapshot.FINAL_CONSUMER_NAME);
        assertThat(snapshot.nif()).isNull();
        assertThat(snapshot.address()).isNull();
        assertThat(snapshot.finalConsumer()).isTrue();
    }

    @Test
    void should_prefer_the_override_to_the_owner() {
        Party override = new Party(" Frota Lda ", "509999999", PT_ADDRESS);
        Party owner = new Party("Ana", "123456789", null);

        assertThat(customer(false, override, owner, "PT", BELOW).name()).isEqualTo("Frota Lda");
        assertThat(customer(false, null, owner, "PT", BELOW).name()).isEqualTo("Ana");
        assertThat(customer(false, null, null, "PT", BELOW).name()).isEqualTo(CustomerSnapshot.FINAL_CONSUMER_NAME);
    }

    @Test
    void should_refuse_a_name_over_120_characters_with_its_own_code() {
        assertThat(customer(false, new Party("x".repeat(120), null, null), null, "PT", BELOW).name()).hasSize(120);

        assertThatThrownBy(() -> customer(false, new Party("x".repeat(121), null, null), null, "PT", BELOW))
                .isInstanceOfSatisfying(InvalidInputException.class, e -> {
                    assertThat(e.code()).isEqualTo("customer-name-too-long");
                    assertThat(e.field()).isEqualTo("customer.name");
                });
        // A long name on the vehicle owner is refused the same way, not left to the database.
        assertThatThrownBy(() -> customer(false, null, new Party("y".repeat(200), null, null), "PT", BELOW))
                .isInstanceOf(CustomerNameTooLongException.class);
    }
}
