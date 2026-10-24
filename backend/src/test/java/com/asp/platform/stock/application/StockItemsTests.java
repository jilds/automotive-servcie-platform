package com.asp.platform.stock.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.asp.platform.ApiTestSupport;
import com.asp.platform.stock.StockItem;
import com.asp.platform.stock.StockReservations;

/// `StockReservations.items`: several products in one call, for other modules to show them (the catalogue's
/// packages). Products no longer sold are included; unknown ids and other workshops' products are left out.
class StockItemsTests extends ApiTestSupport {

    private static final String FILTER = """
            {"sku":"OF-712","name":"Filtro de óleo","unit":"un","costCents":450,"salePriceCents":900,"vatRate":23}""";
    private static final String PADS = """
            {"sku":"BP-100","name":"Pastilhas de travão","costCents":2000,"salePriceCents":3500,"vatRate":23}""";

    @Autowired
    private StockReservations stock;

    @Autowired
    private JwtDecoder jwtDecoder;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_read_several_products_including_inactive_ones_and_only_of_the_workshop() throws Exception {
        String owner = signup("Oficina Itens Stock");
        UUID filter = UUID.fromString(idOf(call(post("/api/v1/stock/products").content(FILTER), owner)));
        UUID pads = UUID.fromString(idOf(call(post("/api/v1/stock/products").content(PADS), owner)));
        call(put("/api/v1/stock/products/" + pads).content(withVersion(PADS.replace("\"vatRate\":23",
                "\"vatRate\":23,\"active\":false"), 0)), owner).andExpect(status().isOk());
        String other = signup("Oficina Itens Stock Outra");
        UUID foreign = UUID.fromString(idOf(call(post("/api/v1/stock/products").content(FILTER), other)));

        signIn(owner);
        Map<UUID, StockItem> items = stock.items(List.of(filter, pads, foreign, UUID.randomUUID()));

        assertThat(items).containsOnlyKeys(filter, pads);
        assertThat(items.get(filter).active()).isTrue();
        assertThat(items.get(filter).salePriceCents()).isEqualTo(900);
        assertThat(items.get(filter).sku()).isEqualTo("OF-712");
        assertThat(items.get(pads).active()).isFalse();
        assertThat(stock.items(List.of())).isEmpty();
    }

    /// Binds the workshop for row-level security the way a request with this token would.
    private void signIn(String token) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtDecoder.decode(token),
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }
}
