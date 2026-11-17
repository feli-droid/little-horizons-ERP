package com.littlehorizons.erp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** An electronic invoice (simulated) issued to a guardian. */
public record Invoice(int id, String number, int guardianId, String clientName, String clientDocument,
                      String clientAddress, LocalDate issueDate, BigDecimal subtotal, BigDecimal vat,
                      BigDecimal total, String cufe, String qrContent, String resolutionNumber,
                      String fileName, String filePath, List<InvoiceLine> lines) {
}