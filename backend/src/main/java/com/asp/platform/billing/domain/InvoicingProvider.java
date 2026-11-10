package com.asp.platform.billing.domain;

/// A certified-invoicing service (InvoiceXpress, Moloni, Vendus, KeyInvoice ...). ASP keeps a snapshot; the provider
/// assigns series, number, ATCUD, QR code and hash, and holds the fiscal truth.
public interface InvoicingProvider {

    /// The name in the configuration and on the stored documents, for example "sandbox".
    String id();

    /// False for the stand-in used when the provider is none: every call that would reach it fails.
    default boolean configured() {
        return true;
    }

    /// True when the documents are not fiscal documents.
    boolean sandbox();

    /// Issues the document. Idempotent on [DocumentDraft#externalRef()].
    ///
    /// @throws ProviderRejectedException    when the provider refuses it (definitive)
    /// @throws ProviderUnavailableException when the provider cannot be reached or does not answer
    IssuedDocument issue(DocumentDraft draft);

    /// Annuls an issued invoice at the provider. Idempotent.
    ///
    /// @throws ProviderRejectedException    when the provider refuses
    /// @throws ProviderUnavailableException when the provider cannot be reached
    void voidDocument(String providerDocumentId, String reason);

    /// The PDF of the document, from the provider (a real adapter fetches it by its document id).
    byte[] pdf(Invoice document);
}
