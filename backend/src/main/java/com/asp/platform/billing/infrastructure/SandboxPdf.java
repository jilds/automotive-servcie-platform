package com.asp.platform.billing.infrastructure;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.asp.platform.billing.domain.CustomerSnapshot;
import com.asp.platform.billing.domain.DocumentKind;
import com.asp.platform.billing.domain.FiscalAddress;
import com.asp.platform.billing.domain.Invoice;
import com.asp.platform.billing.domain.InvoiceLine;
import com.asp.platform.billing.domain.InvoiceTotals;
import com.asp.platform.billing.domain.IssuedDocument;
import com.asp.platform.billing.domain.VatSummaryRow;

/// A one-page PDF of a sandbox document, written by hand (no library): a grey diagonal watermark "SEM VALIDADE FISCAL"
/// and the line "SANDBOX - nao e um documento fiscal", so nobody mistakes it for a fiscal document. Text is
/// WinAnsi (Latin-1) in the standard Helvetica fonts. A long document shows its first lines and says how many are left.
final class SandboxPdf {

    private static final int MAX_LINES = 40;

    private SandboxPdf() {
    }

    static byte[] render(Invoice invoice) {
        List<String> text = new ArrayList<>();
        IssuedDocument issued = invoice.issued();
        String type = invoice.kind() == DocumentKind.INVOICE ? "Fatura" : "Nota de credito";
        text.add(type + " " + (issued == null ? "(por emitir)" : issued.fullNumber()));
        if (issued != null) {
            text.add("ATCUD: " + issued.atcud() + "   Emitido em: " + issued.issuedAt());
        }
        text.add("Emitente: " + invoice.issuer().name() + (invoice.issuer().nif() == null ? ""
                : "   NIF " + invoice.issuer().nif()));
        CustomerSnapshot customer = invoice.customer();
        text.add("Cliente: " + customer.name() + (customer.nif() == null ? "" : "   NIF " + customer.nif()));
        FiscalAddress address = customer.address();
        if (address != null) {
            text.add("Morada: " + address.street() + ", " + address.postalCode() + " " + address.locality() + " ("
                    + address.country() + ")");
        }
        if (invoice.plate() != null) {
            text.add("Viatura: " + invoice.plate() + "   Ordem de trabalho: " + invoice.workOrderNumber());
        }
        if (invoice.reason() != null) {
            text.add("Motivo: " + invoice.reason());
        }
        text.add("");
        text.add("Linha  Descricao                               Qtd    Preco    IVA%     Total");
        List<InvoiceLine> lines = invoice.lines();
        for (int i = 0; i < Math.min(lines.size(), MAX_LINES); i++) {
            InvoiceLine l = lines.get(i);
            text.add("%d  %s  x%s  %s  %s%%  %s".formatted(l.position(), l.description(), l.quantity().toPlainString(),
                    money(l.unitPriceCents()), l.vatRate().toPlainString(), money(l.totalCents())));
        }
        if (lines.size() > MAX_LINES) {
            text.add("... mais " + (lines.size() - MAX_LINES) + " linhas");
        }
        text.add("");
        InvoiceTotals totals = invoice.totals();
        for (VatSummaryRow row : totals.vatSummary()) {
            text.add("IVA %s%%: base %s  imposto %s".formatted(row.vatRate().toPlainString(), money(row.netCents()),
                    money(row.vatCents())));
        }
        text.add("Total sem IVA: " + money(totals.netCents()) + "   IVA: " + money(totals.vatCents())
                + "   TOTAL: " + money(totals.totalCents()) + " EUR");
        if (issued != null) {
            text.add("QR: " + issued.qrPayload());
        }

        StringBuilder content = new StringBuilder();
        content.append("q 0.85 g BT /F2 52 Tf 0.7071 0.7071 -0.7071 0.7071 95 230 Tm (SEM VALIDADE FISCAL) Tj ET Q\n");
        content.append("0 g BT /F2 12 Tf 50 800 Td (SANDBOX - nao e um documento fiscal) Tj ET\n");
        int y = 775;
        for (String line : text) {
            content.append("BT /F1 9 Tf 50 ").append(y).append(" Td (").append(escape(line)).append(") Tj ET\n");
            y -= 14;
        }
        content.append("BT /F2 10 Tf 50 40 Td (SEM VALIDADE FISCAL - documento de teste) Tj ET\n");
        return pdf(content.toString());
    }

    private static byte[] pdf(String content) {
        byte[] stream = content.getBytes(StandardCharsets.ISO_8859_1);
        List<byte[]> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.ISO_8859_1),
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".getBytes(StandardCharsets.ISO_8859_1),
                ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> "
                        + "/Contents 4 0 R >>").getBytes(StandardCharsets.ISO_8859_1),
                concat(("<< /Length " + stream.length + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1), stream,
                        "endstream".getBytes(StandardCharsets.ISO_8859_1)),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
                        .getBytes(StandardCharsets.ISO_8859_1),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
                        .getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
        int[] offsets = new int[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = out.size();
            out.writeBytes(((i + 1) + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
            out.writeBytes(objects.get(i));
            out.writeBytes("\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append("%010d 00000 n \n".formatted(offset));
        }
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        out.writeBytes(table.toString().getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    /// A PDF string: backslash and parentheses escaped, characters outside Latin-1 and controls replaced.
    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\\' || c == '(' || c == ')') {
                out.append('\\').append(c);
            } else if (c < 32 || c > 255) {
                out.append('?');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String money(long cents) {
        return String.format(Locale.ROOT, "%d,%02d", cents / 100, Math.abs(cents % 100));
    }

}
