package com.littlehorizons.erp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A payment made by a guardian for a student. */
public record Payment(int id, int studentId, String studentName, int guardianId, String guardianName,
                      String conceptName, BigDecimal amount, LocalDate date, PaymentMethod method,
                      String receiptNumber, String period) {
}