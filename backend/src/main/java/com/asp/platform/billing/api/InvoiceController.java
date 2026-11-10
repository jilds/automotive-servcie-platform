package com.asp.platform.billing.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.billing.application.InvoiceIssuing;
import com.asp.platform.billing.application.InvoiceService;
import com.asp.platform.billing.application.IssueOutcome;
import com.asp.platform.billing.domain.DocumentKind;
import com.asp.platform.billing.domain.InvalidQueryException;
import com.asp.platform.billing.domain.InvoiceStatus;
import com.asp.platform.billing.domain.PaymentProgress;
import com.asp.platform.shared.CurrentUser;

/// Invoices and credit notes. Owner and front desk only: they carry prices and customer data, which mechanics never
/// get (docs/api/release-4.md "Invoicing and payments").
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
class InvoiceController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MIN_SEARCH_LENGTH = 2;

    private final InvoiceIssuing issuing;
    private final InvoiceService invoices;

    InvoiceController(InvoiceIssuing issuing, InvoiceService invoices) {
        this.issuing = issuing;
        this.invoices = invoices;
    }

    /// 201 when issued by this call, 200 for a replay of an issued one, 202 when the document is stored but the
    /// provider has not issued it (PENDING or FAILED).
    @PostMapping("/work-orders/{workOrderId}/invoices")
    ResponseEntity<InvoiceResponse> issue(CurrentUser user, @PathVariable UUID workOrderId,
            @Valid @RequestBody IssueInvoiceRequest request) {
        return respond(issuing.issue(user, workOrderId, request.toRequest()));
    }

    @GetMapping("/work-orders/{workOrderId}/invoices")
    List<InvoiceResponse> ofWorkOrder(@PathVariable UUID workOrderId) {
        return invoices.ofWorkOrder(workOrderId).stream().map(InvoiceResponse::of).toList();
    }

    @GetMapping("/invoices")
    InvoiceSummaryResponse.Page list(CurrentUser user, @RequestParam(required = false) @Nullable List<InvoiceStatus> status,
            @RequestParam(required = false) @Nullable DocumentKind kind,
            @RequestParam(required = false) @Nullable PaymentProgress paymentStatus,
            @RequestParam(required = false) @Nullable LocalDate from,
            @RequestParam(required = false) @Nullable LocalDate to,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (page < 0) {
            throw new InvalidQueryException("page");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidQueryException("size");
        }
        String search = q == null || q.isBlank() ? null : q.strip();
        if (search != null && search.length() < MIN_SEARCH_LENGTH) {
            throw new InvalidQueryException("q");
        }
        return InvoiceSummaryResponse.of(invoices.list(user.tenantId(), status == null ? List.of() : status, kind,
                paymentStatus, from, to, search, page, size));
    }

    @GetMapping("/invoices/{id}")
    InvoiceResponse get(@PathVariable UUID id) {
        return InvoiceResponse.of(invoices.get(id));
    }

    @GetMapping("/invoices/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        InvoiceService.Pdf pdf = invoices.pdf(id);
        String file = pdf.number().replaceAll("[^A-Za-z0-9._-]", "-") + ".pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file + "\"")
                .body(pdf.content());
    }

    @PostMapping("/invoices/{id}/retry")
    InvoiceResponse retry(CurrentUser user, @PathVariable UUID id) {
        return InvoiceResponse.of(issuing.retry(user.tenantId(), id).view());
    }

    @PostMapping("/invoices/{id}/discard")
    InvoiceResponse discard(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody CloseRequest request) {
        return InvoiceResponse.of(invoices.discard(user, id, request.reason(), request.version()));
    }

    @PostMapping("/invoices/{id}/void")
    InvoiceResponse voidInvoice(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody CloseRequest request) {
        return InvoiceResponse.of(invoices.voidInvoice(user, id, request.reason(), request.version()));
    }

    /// Same answers as issuing an invoice: 201, 200 for a replay, 202 when stored but not issued.
    @PostMapping("/invoices/{id}/credit-notes")
    ResponseEntity<InvoiceResponse> creditNote(CurrentUser user, @PathVariable UUID id,
            @Valid @RequestBody CreditNoteRequest request) {
        return respond(issuing.creditNote(user, id, request.requestId(), request.reason().strip()));
    }

    private static ResponseEntity<InvoiceResponse> respond(IssueOutcome outcome) {
        HttpStatus status = !outcome.issued() ? HttpStatus.ACCEPTED
                : outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(InvoiceResponse.of(outcome.view()));
    }

    /// Void or discard: why, and the version the caller read.
    record CloseRequest(@NotBlank @Size(max = 500) String reason, @NotNull Long version) {
    }

    record CreditNoteRequest(@NotNull UUID requestId, @NotBlank @Size(max = 500) String reason) {
    }
}
