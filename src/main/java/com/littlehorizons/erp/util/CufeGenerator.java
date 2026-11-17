package com.littlehorizons.erp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

/** Builds the simulated CUFE (hash of the invoice data) and the text a QR code would contain. */
public final class CufeGenerator {

    private CufeGenerator() { }

    /** SHA-384 (96 hexadecimal characters) of invoice number, date, total, school NIT and client document. */
    public static String cufe(String invoiceNumber, LocalDate date, BigDecimal total, String nit, String clientDocument) {
        String raw = invoiceNumber + date + total.setScale(2, RoundingMode.HALF_UP).toPlainString() + nit + clientDocument;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-384");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(raw.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-384 is not available in this Java runtime", e);
        }
    }

    /** The string a QR code would hold; the program prints it as text (no image is generated). */
    public static String qrContent(String invoiceNumber, LocalDate date, String nit, String clientDocument,
                                   BigDecimal total, String cufe) {
        return "NumFac: " + invoiceNumber + " | FecFac: " + date + " | NitFac: " + nit + " | DocAdq: " + clientDocument
                + " | ValFac: " + total.setScale(2, RoundingMode.HALF_UP).toPlainString() + " | CUFE: " + cufe
                + " | QR simulado";
    }
}