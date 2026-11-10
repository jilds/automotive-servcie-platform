package com.asp.platform.billing.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.asp.platform.billing.domain.DocumentDraft;
import com.asp.platform.billing.domain.DocumentKind;
import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoicingProvider;
import com.asp.platform.billing.domain.IssuedDocument;
import com.asp.platform.billing.domain.ProviderRejectedException;
import com.asp.platform.billing.domain.ProviderUnavailableException;

/// The invoicing provider used until a real one is chosen: offline and deterministic, and **not a fiscal document**.
/// Numbers come from a ledger table per workshop, series and kind, and the ledger makes the call idempotent on the
/// external reference (the document id), also across restarts. Faults are scripted through [SandboxScenario].
@Component
@ConditionalOnProperty(name = "app.billing.invoicing.provider", havingValue = "sandbox", matchIfMissing = true)
class SandboxInvoicingProvider implements InvoicingProvider {

    static final String ID = "sandbox";
    static final String SERIES = "SANDBOX";
    private static final int ATTEMPTS = 5;

    private final JdbcClient jdbc;
    private final SandboxScenario scenario;
    private final Clock clock;

    SandboxInvoicingProvider(JdbcClient jdbc, SandboxScenario scenario, Clock clock) {
        this.jdbc = jdbc;
        this.scenario = scenario;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean sandbox() {
        return true;
    }

    @Override
    public IssuedDocument issue(DocumentDraft draft) {
        scenario.awaitInvoicingHold();
        SandboxScenario.Fault fault = scenario.nextInvoicing();
        if (fault == SandboxScenario.Fault.REJECT) {
            throw new ProviderRejectedException("sandbox-rejected", "The sandbox refused the document (scripted)");
        }
        if (fault == SandboxScenario.Fault.UNAVAILABLE) {
            throw new ProviderUnavailableException("The sandbox did not answer (scripted)");
        }
        IssuedDocument issued = ledger(draft.externalRef(), draft.kind(), draft.totals().totalCents());
        if (fault == SandboxScenario.Fault.UNAVAILABLE_AFTER_ISSUE) {
            throw new ProviderUnavailableException("The sandbox issued the document but the answer was lost (scripted)");
        }
        if (fault == SandboxScenario.Fault.WRONG_TOTAL) {
            return new IssuedDocument(issued.providerDocumentId(), issued.series(), issued.sequence(), issued.fullNumber(),
                    issued.atcud(), issued.qrPayload(), issued.hashFragment(), issued.issuedAt(),
                    issued.totalCents() + 1);
        }
        return issued;
    }

    @Override
    public void voidDocument(String providerDocumentId, String reason) {
        scenario.awaitVoidHold();
        SandboxScenario.Fault fault = scenario.nextVoid();
        if (fault == SandboxScenario.Fault.REJECT) {
            throw new ProviderRejectedException("sandbox-rejected", "The sandbox refused to void the document (scripted)");
        }
        if (fault != null) {
            throw new ProviderUnavailableException("The sandbox did not answer (scripted)");
        }
        jdbc.sql("update invoicing_sandbox_document set voided_at = :at where id = :id and voided_at is null")
                .param("at", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("id", UUID.fromString(providerDocumentId)).update();
    }

    @Override
    public byte[] pdf(Invoice document) {
        return SandboxPdf.render(document);
    }

    /// Reads the ledger entry of the reference, or takes the next number. Two documents racing for a number make one
    /// insert fail on the unique key: it reads again and tries the next number.
    private IssuedDocument ledger(UUID reference, DocumentKind kind, long totalCents) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Optional<IssuedDocument> existing = find(reference, kind, totalCents);
            if (existing.isPresent()) {
                return existing.get();
            }
            try {
                jdbc.sql("""
                        insert into invoicing_sandbox_document (id, kind, series, number, issued_at)
                        select :id, :kind, :series, coalesce(max(number), 0) + 1, :at
                        from invoicing_sandbox_document where kind = :kind and series = :series""")
                        .param("id", reference).param("kind", kind.name()).param("series", SERIES)
                        .param("at", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).update();
            } catch (DuplicateKeyException e) {
                // Another call took the number or the reference: read again.
            }
        }
        throw new ProviderUnavailableException("The sandbox ledger is busy");
    }

    private Optional<IssuedDocument> find(UUID reference, DocumentKind kind, long totalCents) {
        return jdbc.sql("select number, issued_at from invoicing_sandbox_document where id = :id")
                .param("id", reference)
                .query((rs, _) -> {
                    int number = rs.getInt("number");
                    Instant issuedAt = rs.getObject("issued_at", OffsetDateTime.class).toInstant();
                    String type = kind == DocumentKind.INVOICE ? "FT" : "NC";
                    String full = type + " " + SERIES + "/" + number;
                    return new IssuedDocument(reference.toString(), SERIES, number, full, "SANDBOX-" + number,
                            "SANDBOX*NOT-A-FISCAL-DOCUMENT*" + full + "*" + totalCents, "SBOX", issuedAt, totalCents);
                }).optional();
    }
}
