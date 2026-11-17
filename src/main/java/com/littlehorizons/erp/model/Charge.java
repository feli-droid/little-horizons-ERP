package com.littlehorizons.erp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An amount to be paid by a student, possibly with a discount (siblings, scholarship). */
public record Charge(int id, int studentId, String studentName, int conceptId, String conceptName, int year,
                     BillingPeriod period, BigDecimal baseValue, BigDecimal discount, BigDecimal finalValue,
                     String discountReason, LocalDate dueDate, ChargeStatus status) {
}