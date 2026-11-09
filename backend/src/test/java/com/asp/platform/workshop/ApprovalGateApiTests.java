package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/// Only the owner may move a work order out of AWAITING_APPROVAL until the order's latest quote is answered
/// (APPROVED or REJECTED, on the link or by phone). Everybody else gets 409 `approval-pending` for every target,
/// CANCELLED included; the automatic move on approval is unaffected.
class ApprovalGateApiTests extends QuoteTestSupport {

    private static final List<String> TARGETS = List.of("AWAITING_PARTS", "IN_PROGRESS", "CANCELLED");

    private ResultActions move(String token, String wo, String target) throws Exception {
        return call(patch("/api/v1/work-orders/" + wo + "/status").content("{\"status\":\"" + target + "\"}"), token);
    }

    private void assertBlocked(String token, Shop g, String wo) throws Exception {
        for (String target : TARGETS) {
            move(token, wo, target).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("approval-pending"));
        }
        assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_APPROVAL");
    }

    private void reject(Shop g, String wo, String quoteId) throws Exception {
        decide(g.owner(), wo, quoteId, "{\"decision\":\"REJECTED\",\"note\":\"Caro\",\"version\":0}")
                .andExpect(status().isOk());
    }

    /// An order in AWAITING_APPROVAL moved there by the owner, without any quote.
    private String awaitingWithoutQuote(Shop g) throws Exception {
        String wo = quotableOrder(g, false);
        move(g.owner(), wo, "AWAITING_APPROVAL").andExpect(status().isOk());
        return wo;
    }

    @Test
    void should_block_the_front_desk_without_a_quote() throws Exception {
        Shop g = garage("Gate Sem Orcamento");
        String desk = member(g, "FRONT_DESK");
        String wo = awaitingWithoutQuote(g);

        assertBlocked(desk, g, wo);
        call(get("/api/v1/work-orders/" + wo), desk)
                .andExpect(jsonPath("$.approvalPending").value(true))
                .andExpect(jsonPath("$.allowedNext.length()").value(0));
    }

    @Test
    void should_block_the_front_desk_while_the_quote_is_sent_and_show_it_in_the_response() throws Exception {
        Shop g = garage("Gate Enviado");
        String desk = member(g, "FRONT_DESK");
        String wo = quotableOrder(g, false);
        sendQuote(g, wo);

        assertBlocked(desk, g, wo);
        call(get("/api/v1/work-orders/" + wo), desk)
                .andExpect(jsonPath("$.approvalPending").value(true))
                .andExpect(jsonPath("$.allowedNext.length()").value(0));
        call(get("/api/v1/work-orders/" + wo), g.owner())
                .andExpect(jsonPath("$.approvalPending").value(false))
                .andExpect(jsonPath("$.allowedNext.length()").value(3));
    }

    @Test
    void should_block_the_front_desk_when_the_quote_expired_or_was_superseded() throws Exception {
        Shop g = garage("Gate Expirado");
        String desk = member(g, "FRONT_DESK");

        String expired = quotableOrder(g, false);
        pastValidity(g, idOfQuote(sendQuote(g, expired)));
        assertBlocked(desk, g, expired);

        String superseded = quotableOrder(g, false);
        sendQuote(g, superseded);
        labour(g, superseded, "Extra", 500, 23);
        assertThat(quoteStatuses(g, superseded)).containsExactly("SUPERSEDED");
        assertBlocked(desk, g, superseded);
        call(get("/api/v1/work-orders/" + superseded), desk).andExpect(jsonPath("$.approvalPending").value(true));
    }

    @Test
    void should_let_the_front_desk_move_after_the_customer_rejects_on_the_link() throws Exception {
        Shop g = garage("Gate Rejeita Link");
        String desk = member(g, "FRONT_DESK");
        String wo = quotableOrder(g, false);
        String token = tokenOfQuote(sendQuote(g, wo));
        assertBlocked(desk, g, wo);

        publicCall(post(publicQuote(g, token) + "/reject").content("{}")).andExpect(status().isOk());

        call(get("/api/v1/work-orders/" + wo), desk)
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.approvalPending").value(false))
                .andExpect(jsonPath("$.allowedNext.length()").value(3));
        move(desk, wo, "CANCELLED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void should_let_the_front_desk_move_after_a_phone_rejection() throws Exception {
        Shop g = garage("Gate Rejeita Telefone");
        String desk = member(g, "FRONT_DESK");
        String wo = quotableOrder(g, false);
        String quoteId = idOfQuote(sendQuote(g, wo));
        assertBlocked(desk, g, wo);

        reject(g, wo, quoteId);

        move(desk, wo, "IN_PROGRESS").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void should_move_automatically_on_approval_and_leave_nothing_pending() throws Exception {
        Shop g = garage("Gate Aprova Link");
        String desk = member(g, "FRONT_DESK");
        String wo = quotableOrder(g, false);
        String token = tokenOfQuote(sendQuote(g, wo));

        approveOnLink(g, token).andExpect(status().isOk());

        call(get("/api/v1/work-orders/" + wo), desk)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.approvalPending").value(false));

        String phone = quotableOrder(g, true);
        String quoteId = idOfQuote(sendQuote(g, phone));
        decide(desk, phone, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Ao balcao\",\"version\":0}")
                .andExpect(status().isOk());
        assertThat(workOrderStatus(g, phone)).isEqualTo("AWAITING_PARTS");
    }

    @Test
    void should_let_the_front_desk_move_after_a_phone_approval_when_the_order_was_moved_by_the_owner()
            throws Exception {
        Shop g = garage("Gate Aprova Telefone");
        String desk = member(g, "FRONT_DESK");
        String wo = quotableOrder(g, false);
        String quoteId = idOfQuote(sendQuote(g, wo));
        // The owner overrides: the order leaves the state, the quote is answered later.
        move(g.owner(), wo, "AWAITING_PARTS").andExpect(status().isOk());
        decide(g.owner(), wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Ok\",\"version\":0}")
                .andExpect(status().isOk());

        move(desk, wo, "IN_PROGRESS").andExpect(status().isOk());
    }

    @Test
    void should_let_the_owner_always_move_and_report_nothing_pending() throws Exception {
        Shop g = garage("Gate Dono");
        String noQuote = awaitingWithoutQuote(g);
        move(g.owner(), noQuote, "CANCELLED").andExpect(status().isOk());

        String sent = quotableOrder(g, false);
        sendQuote(g, sent);
        move(g.owner(), sent, "IN_PROGRESS").andExpect(status().isOk());

        String expired = quotableOrder(g, false);
        pastValidity(g, idOfQuote(sendQuote(g, expired)));
        move(g.owner(), expired, "AWAITING_PARTS").andExpect(status().isOk());
    }

    @Test
    void should_block_an_assigned_mechanic_too_and_keep_forbidden_for_cancel() throws Exception {
        Shop g = garage("Gate Mecanico");
        String mechanicEmail = uniqueEmail("mec");
        String mechanicId = invite(g.owner(), "MECHANIC", mechanicEmail);
        String mechanic = login(mechanicEmail, PASSWORD);
        String wo = workOrder(g, vehicle(g, true), mechanicId);
        labour(g, wo, "Revisao", 5000, 23);
        sendQuote(g, wo);

        for (String target : List.of("AWAITING_PARTS", "IN_PROGRESS")) {
            move(mechanic, wo, target).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("approval-pending"));
        }
        move(mechanic, wo, "CANCELLED").andExpect(status().isForbidden());
        call(get("/api/v1/work-orders/" + wo), mechanic)
                .andExpect(jsonPath("$.approvalPending").value(true))
                .andExpect(jsonPath("$.allowedNext.length()").value(0));

        // A mechanic looking at somebody else's job sees it read-only, not as waiting for approval.
        String otherEmail = uniqueEmail("mec");
        invite(g.owner(), "MECHANIC", otherEmail);
        String other = login(otherEmail, PASSWORD);
        call(get("/api/v1/work-orders/" + wo), other)
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.approvalPending").value(false))
                .andExpect(jsonPath("$.allowedNext.length()").value(0));
    }

    @Test
    void should_block_again_when_a_new_quote_follows_a_rejected_one() throws Exception {
        Shop g = garage("Gate Ultimo Orcamento");
        String desk = member(g, "FRONT_DESK");
        String wo = quotableOrder(g, false);
        reject(g, wo, idOfQuote(sendQuote(g, wo)));
        call(get("/api/v1/work-orders/" + wo), desk).andExpect(jsonPath("$.approvalPending").value(false));

        // Lines change (quote 1 is already decided, so nothing to supersede) and quote 2 goes out.
        labour(g, wo, "Extra", 500, 23);
        sendQuote(g, wo);

        assertBlocked(desk, g, wo);
        call(get("/api/v1/work-orders/" + wo), desk)
                .andExpect(jsonPath("$.quote.number").value(2))
                .andExpect(jsonPath("$.approvalPending").value(true));
    }

    @Test
    void should_not_flag_approval_pending_outside_awaiting_approval() throws Exception {
        Shop g = garage("Gate Outros Estados");
        String desk = member(g, "FRONT_DESK");
        String wo = quotableOrder(g, false);

        call(get("/api/v1/work-orders/" + wo), desk)
                .andExpect(jsonPath("$.status").value("DIAGNOSTICS"))
                .andExpect(jsonPath("$.approvalPending").value(false))
                .andExpect(jsonPath("$.allowedNext.length()").value(3));
        move(desk, wo, "IN_PROGRESS").andExpect(status().isOk());
    }

    @Test
    void should_give_a_consistent_result_when_the_customer_answer_races_a_front_desk_move() throws Exception {
        Shop g = garage("Gate Corrida");
        String desk = member(g, "FRONT_DESK");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                String wo = quotableOrder(g, false);
                String token = tokenOfQuote(sendQuote(g, wo));
                CountDownLatch start = new CountDownLatch(1);
                Future<Integer> approval = pool.submit(() -> {
                    start.await();
                    return approveOnLink(g, token).andReturn().getResponse().getStatus();
                });
                Future<String> moved = pool.submit(() -> {
                    start.await();
                    var response = move(desk, wo, "AWAITING_PARTS").andReturn().getResponse();
                    return response.getStatus() + ":" + response.getContentAsString();
                });
                start.countDown();
                assertThat(approval.get(30, TimeUnit.SECONDS)).isEqualTo(200);
                String result = moved.get(30, TimeUnit.SECONDS);
                if (result.startsWith("200:")) {
                    // The approval came first (to IN_PROGRESS), then the desk moved on.
                    assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_PARTS");
                } else {
                    assertThat(result).startsWith("409:").contains("approval-pending");
                    assertThat(workOrderStatus(g, wo)).isEqualTo("IN_PROGRESS");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void should_keep_workshops_apart() throws Exception {
        Shop a = garage("Gate Isolamento A");
        Shop b = garage("Gate Isolamento B");
        String deskB = member(b, "FRONT_DESK");
        String woA = awaitingWithoutQuote(a);
        String woB = quotableOrder(b, false);
        reject(b, woB, idOfQuote(sendQuote(b, woB)));

        // B's answered order moves; A's unanswered one stays blocked for A's desk and unknown to B's.
        move(deskB, woB, "IN_PROGRESS").andExpect(status().isOk());
        move(deskB, woA, "IN_PROGRESS").andExpect(status().isNotFound());
        assertThat(workOrderStatus(a, woA)).isEqualTo("AWAITING_APPROVAL");
        assertThat(JsonPath.<String>read(
                call(get("/api/v1/work-orders/" + woA), member(a, "FRONT_DESK")).andReturn().getResponse()
                        .getContentAsString(), "$.status")).isEqualTo("AWAITING_APPROVAL");
    }
}
