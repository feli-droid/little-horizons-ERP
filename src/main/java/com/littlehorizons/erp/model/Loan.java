package com.littlehorizons.erp.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A loan of a resource to a student or a teacher. */
public record Loan(int id, BorrowerType borrowerType, int resourceId, String resourceCode, String resourceName,
                   int borrowerId, String borrowerName, String borrowerDocument, LocalDate loanDate,
                   LocalDate dueDate, LocalDate returnDate, LoanStatus status, String observations) {

    /** Days past the due date (0 if not late), measured against the return date or against today. */
    public long daysLate(LocalDate today) {
        LocalDate end = returnDate != null ? returnDate : today;
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, end));
    }
}