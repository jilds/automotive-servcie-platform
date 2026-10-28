package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/// An approval on the link racing a line change of the same work order: the work order lock lets exactly one win.
/// Either the quote is approved with the old lines and the line change supersedes nothing, or the line change
/// supersedes the quote first and the approval answers 409 `quote-not-open`.
class QuoteConcurrencyTests extends QuoteTestSupport {

    private static final int ROUNDS = 6;

    /// Another transaction holds the work order row (as a slow line change would): a line change and a staff
    /// decision wait for the lock's 3 s timeout and answer 409 `try-again`, changing nothing.
    @Test
    void should_answer_try_again_while_another_transaction_holds_the_work_order() throws Exception {
        Shop g = garage("Orcamento Trinco");
        String wo = quotableOrder(g, false);
        String quoteId = idOfQuote(sendQuote(g, wo));
        DataSource dataSource = jdbc.getDataSource();
        assertThat(dataSource).isNotNull();

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement bind = holder.prepareStatement("select set_config('app.tenant_id', ?, true)");
                    PreparedStatement lock = holder.prepareStatement(
                            "select id from work_order where id = ?::uuid for update")) {
                bind.setString(1, g.id());
                bind.execute();
                lock.setString(1, wo);
                try (ResultSet rs = lock.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                }

                long started = System.nanoTime();
                call(post("/api/v1/work-orders/" + wo + "/lines").content("""
                        {"type":"LABOR","description":"Extra","quantity":1,"unitPriceCents":500,"vatRate":23}"""),
                        g.owner())
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("try-again"));
                decide(g.owner(), wo, quoteId, """
                        {"decision":"APPROVED","note":"Ao telefone","version":0}""")
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("try-again"));
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(15));
            } finally {
                holder.rollback();
            }
        }

        assertThat(storedStatus(g, quoteId)).isEqualTo("SENT");
        assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_APPROVAL");
    }

    @Test
    void should_let_either_the_approval_or_the_line_change_win_never_both() throws Exception {
        Shop g = garage("Orcamento Corrida");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String wo = quotableOrder(g, false);
                String sent = sendQuote(g, wo);
                String token = tokenOfQuote(sent);
                String quoteId = idOfQuote(sent);

                CountDownLatch start = new CountDownLatch(1);
                Future<Integer> approval = pool.submit(() -> {
                    start.await();
                    return approveOnLink(g, token).andReturn().getResponse().getStatus();
                });
                Future<String> lineChange = pool.submit(() -> {
                    start.await();
                    return call(post("/api/v1/work-orders/" + wo + "/lines").content("""
                            {"type":"LABOR","description":"Extra","quantity":1,"unitPriceCents":500,"vatRate":23}"""),
                            g.owner()).andReturn().getResponse().getContentAsString();
                });
                start.countDown();
                int approvalStatus = approval.get(30, TimeUnit.SECONDS);
                String changed = lineChange.get(30, TimeUnit.SECONDS);

                assertThat(JsonPath.<List<Object>>read(changed, "$.lines")).hasSize(2);
                String stored = storedStatus(g, quoteId);
                String quoteOnOrder = JsonPath.read(changed, "$.quote.status");
                if (approvalStatus == 200) {
                    assertThat(stored).isEqualTo("APPROVED");
                    assertThat(quoteOnOrder).isEqualTo("APPROVED");
                    assertThat(workOrderStatus(g, wo)).isEqualTo("IN_PROGRESS");
                } else {
                    assertThat(approvalStatus).isEqualTo(409);
                    assertThat(stored).isEqualTo("SUPERSEDED");
                    assertThat(quoteOnOrder).isEqualTo("SUPERSEDED");
                    assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_APPROVAL");
                }
                // The snapshot keeps the one line it was sent with.
                assertThat(this.<Integer>asWorkshop(g, connection -> {
                    try (var select = connection.prepareStatement(
                            "select count(*) from quote_line where quote_id = ?::uuid")) {
                        select.setString(1, quoteId);
                        var rs = select.executeQuery();
                        rs.next();
                        return rs.getInt(1);
                    }
                })).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
