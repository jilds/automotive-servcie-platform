package com.asp.platform.stock.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

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
import com.asp.platform.stock.domain.DuplicateSkuException;
import com.asp.platform.stock.domain.Product;
import com.asp.platform.stock.domain.ProductDetails;
import com.asp.platform.stock.domain.ProductRepository;

/// Two requests saving the same SKU at the same time both pass the service's "SKU taken?" check. These tests skip
/// that check and save straight through the repository, as the second of the two requests would: the unique index
/// must surface as [DuplicateSkuException] (409 `duplicate-sku`), not a 500.
class ProductSkuRaceTests extends ApiTestSupport {

    @Autowired
    private ProductRepository products;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_raise_duplicate_sku_when_a_concurrent_insert_took_the_sku() throws Exception {
        String owner = signup("Oficina Corrida SKU A");
        createProduct(owner, "OF-712");
        signIn(owner);

        Product duplicate = new Product(UUID.randomUUID(), details("of-712"));

        assertThatThrownBy(() -> inTransaction(() -> products.save(duplicate)))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("of-712");
    }

    @Test
    void should_raise_duplicate_sku_when_a_concurrent_update_took_the_sku() throws Exception {
        String owner = signup("Oficina Corrida SKU B");
        createProduct(owner, "OF-712");
        UUID otherId = UUID.fromString(createProduct(owner, "BP-100"));
        signIn(owner);

        assertThatThrownBy(() -> inTransaction(() -> {
            Product other = products.findById(otherId).orElseThrow();
            other.update(details("OF-712"));
            return products.save(other);
        })).isInstanceOf(DuplicateSkuException.class);

        assertThat(inTransaction(() -> products.findById(otherId).orElseThrow()).details().sku())
                .isEqualTo("BP-100");
    }

    private String createProduct(String token, String sku) throws Exception {
        return idOf(call(post("/api/v1/stock/products").content("""
                {"sku":"%s","name":"Peça","costCents":100,"salePriceCents":200,"vatRate":23}""".formatted(sku)),
                token));
    }

    /// Binds the workshop for row-level security the way a request with this token would.
    private void signIn(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(_ -> work.get());
    }

    private static ProductDetails details(String sku) {
        return new ProductDetails(sku, "Peça", null, "un", 100, 200, BigDecimal.valueOf(23), BigDecimal.ZERO, null,
                true, false);
    }
}
