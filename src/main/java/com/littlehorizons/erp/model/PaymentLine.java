package com.littlehorizons.erp.model;

import java.math.BigDecimal;

/** One item of a payment: either a pending charge (chargeId set) or a concept paid directly. */
public record PaymentLine(int studentId, String studentName, int conceptId, Integer chargeId, BigDecimal amount,
                          Integer year, String period, String description, String receiptNumber, String notes) {
}