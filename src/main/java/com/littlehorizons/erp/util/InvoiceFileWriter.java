package com.littlehorizons.erp.util;

import com.littlehorizons.erp.model.InstitutionSettings;
import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.InvoiceLine;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Writes an invoice as a plain text file in the invoices folder (the project uses .txt instead of PDF). */
public final class InvoiceFileWriter {

    private static final Path FOLDER = Path.of("invoices");
    private static final String DOUBLE_LINE = "=".repeat(78);
    private static final String SINGLE_LINE = "-".repeat(78);

    private InvoiceFileWriter() { }

    /** @return the path of the file written, e.g. invoices/factura_000123.txt */
    public static Path write(Invoice invoice, InstitutionSettings school) throws IOException {
        Files.createDirectories(FOLDER);
        Path file = FOLDER.resolve(fileName(invoice.number()));
        Files.writeString(file, render(invoice, school), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    /** "LH-000123" becomes "factura_000123.txt". */
    public static String fileName(String invoiceNumber) {
        return "factura_" + invoiceNumber.substring(invoiceNumber.lastIndexOf('-') + 1) + ".txt";
    }

    private static String render(Invoice invoice, InstitutionSettings school) {
        StringBuilder text = new StringBuilder();
        text.append(DOUBLE_LINE).append('\n');
        text.append(" FACTURA ELECTRÓNICA DE VENTA (SIMULADA)\n");
        text.append(DOUBLE_LINE).append('\n');
        text.append(" Razón social : ").append(school.legalName()).append('\n');
        text.append(" NIT          : ").append(school.nit()).append('\n');
        text.append(" Dirección    : ").append(school.address()).append('\n');
        text.append(" Teléfono     : ").append(school.phone()).append('\n');
        text.append(" Correo       : ").append(school.email()).append('\n');
        text.append(SINGLE_LINE).append('\n');
        text.append(" Factura No.  : ").append(invoice.number()).append('\n');
        text.append(" Fecha emisión: ").append(invoice.issueDate()).append('\n');
        text.append(" Resolución   : ").append(invoice.resolutionNumber()).append(" del ").append(school.resolutionDate())
                .append(" | Prefijo ").append(school.invoicePrefix())
                .append(" | Rango ").append(school.rangeFrom()).append(" a ").append(school.rangeTo())
                .append(" (simulada)\n");
        text.append(SINGLE_LINE).append('\n');
        text.append(" CLIENTE\n");
        text.append(" Nombre       : ").append(invoice.clientName()).append('\n');
        text.append(" Documento    : ").append(invoice.clientDocument()).append('\n');
        text.append(" Dirección    : ").append(invoice.clientAddress() == null ? "-" : invoice.clientAddress()).append('\n');
        text.append(SINGLE_LINE).append('\n');
        text.append(String.format(" %-44s %4s %14s %14s%n", "Descripción", "Cant", "Valor unit.", "Subtotal"));
        text.append(SINGLE_LINE).append('\n');
        for (InvoiceLine line : invoice.lines()) {
            text.append(String.format(" %-44s %4d %14s %14s%n", truncate(line.description(), 44), line.quantity(),
                    Money.format(line.unitValue()), Money.format(line.subtotal())));
        }
        text.append(SINGLE_LINE).append('\n');
        text.append(String.format(" %-61s %14s%n", "Subtotal", Money.format(invoice.subtotal())));
        text.append(String.format(" %-61s %14s%n", "IVA (" + percent(school.vatPercent()) + "%)", Money.format(invoice.vat())));
        text.append(String.format(" %-61s %14s%n", "TOTAL", Money.format(invoice.total())));
        text.append(SINGLE_LINE).append('\n');
        text.append(" CUFE:\n ").append(invoice.cufe()).append('\n');
        text.append(" Código QR (simulado, contenido):\n ").append(invoice.qrContent()).append('\n');
        text.append(DOUBLE_LINE).append('\n');
        text.append(" Documento generado por el sistema con fines académicos. CUFE, QR y resolución simulados.\n");
        return text.toString();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private static String percent(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}