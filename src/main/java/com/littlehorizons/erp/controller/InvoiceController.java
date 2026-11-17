package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.InvoiceDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.InstitutionSettings;
import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.InvoiceLine;
import com.littlehorizons.erp.model.Guardian;
import com.littlehorizons.erp.model.PaymentLine;
import com.littlehorizons.erp.util.CufeGenerator;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.InvoiceFileWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** Builds electronic invoices (simulated CUFE and QR) and writes them as text files. */
public class InvoiceController {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final InvoiceDao invoiceDao;

    public InvoiceController(InvoiceDao invoiceDao) {
        this.invoiceDao = invoiceDao;
    }

    /**
     * Builds the invoice of a payment (not saved yet): consecutive number inside the authorized range,
     * totals with VAT, simulated CUFE and the text of the simulated QR code.
     */
    public Invoice buildInvoice(Guardian client, LocalDate date, List<PaymentLine> lines) throws BusinessException {
        InstitutionSettings school = invoiceDao.findSettings();
        int sequence = invoiceDao.nextSequence(school.invoicePrefix());
        if (sequence > school.rangeTo() || sequence < school.rangeFrom()) {
            throw new BusinessException("La numeración de facturas está fuera del rango autorizado ("
                    + school.rangeFrom() + " a " + school.rangeTo() + ").");
        }
        String number = school.invoicePrefix() + "-" + String.format("%06d", sequence);
        BigDecimal subtotal = lines.stream().map(PaymentLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = subtotal.multiply(school.vatPercent()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(vat);
        String cufe = CufeGenerator.cufe(number, date, total, school.nit(), client.getDocumentNumber());
        String qr = CufeGenerator.qrContent(number, date, school.nit(), client.getDocumentNumber(), total, cufe);
        List<InvoiceLine> invoiceLines = lines.stream()
                .map(line -> new InvoiceLine(line.description(), 1, line.amount(), line.amount()))
                .toList();
        return new Invoice(0, number, client.getId(), client.getFullName(), client.getDocumentNumber(),
                client.getAddress(), date, subtotal, vat, total, cufe, qr, school.resolutionNumber(), null, null,
                invoiceLines);
    }

    /** Reads the invoice from the database and (re)writes its text file; returns the path of the file. */
    public Path writeFile(String invoiceNumber) throws BusinessException {
        Invoice invoice = getByNumber(invoiceNumber);
        InstitutionSettings school = invoiceDao.findSettings();
        try {
            Path path = InvoiceFileWriter.write(invoice, school);
            invoiceDao.updateFileInfo(invoice.id(), path.getFileName().toString(), path.toString());
            return path;
        } catch (IOException e) {
            ErrorLogger.error("Could not write invoice file " + invoiceNumber, e);
            throw new BusinessException("No se pudo escribir el archivo de la factura " + invoiceNumber
                    + ". Puede regenerarlo desde la opción de consulta de facturas.");
        }
    }

    /** Accepts "LH-000012" or just "12". */
    public String resolveNumber(String input) {
        String clean = input.trim().toUpperCase();
        if (clean.matches("\\d{1,6}")) {
            return invoiceDao.findSettings().invoicePrefix() + "-" + String.format("%06d", Integer.parseInt(clean));
        }
        return clean;
    }

    public Invoice getByNumber(String invoiceNumber) throws BusinessException {
        String number = resolveNumber(invoiceNumber);
        return invoiceDao.findByNumber(number)
                .orElseThrow(() -> new BusinessException("No existe la factura " + number + "."));
    }

    public List<Invoice> latest(int limit) {
        return invoiceDao.findLatest(limit);
    }
}