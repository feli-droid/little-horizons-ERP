package com.littlehorizons.erp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A student with charges pending for more than a month. */
public record DebtorSummary(int studentId, String documentNumber, String studentName, String guardianName,
                            String guardianPhone, long pendingCharges, BigDecimal totalDebt,
                            LocalDate oldestDueDate, long daysOverdue) {
}