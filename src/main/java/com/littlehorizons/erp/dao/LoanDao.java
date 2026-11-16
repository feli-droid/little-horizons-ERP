package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.BorrowerType;
import com.littlehorizons.erp.model.Loan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence operations for loans to students and to teachers. */
public interface LoanDao {

    /**
     * Registers a loan and marks the resource as lent, in one transaction. The availability of the
     * resource is checked inside the transaction so it cannot be lent twice.
     *
     * @return false when the resource is not available
     */
    boolean lend(BorrowerType type, int borrowerId, int resourceId, LocalDate loanDate, LocalDate dueDate,
                 String observations);

    /** Closes a loan and makes the resource available again. @return false if the loan is not open */
    boolean registerReturn(BorrowerType type, int loanId, LocalDate returnDate, String observations);

    /** Marks as Vencido every Activo loan whose due date is before the given day. @return how many changed */
    int markOverdue(LocalDate today);

    Optional<Loan> findOpenByResource(int resourceId);

    /** Open loans (Activo or Vencido) of both borrower types. */
    List<Loan> findOpen();

    /** Loans not returned whose due date is before the given day. */
    List<Loan> findOverdue(LocalDate today);

    List<Loan> findByBorrower(BorrowerType type, int borrowerId);

    int countOpen(BorrowerType type, int borrowerId);

    int countOverdue(BorrowerType type, int borrowerId, LocalDate today);
}