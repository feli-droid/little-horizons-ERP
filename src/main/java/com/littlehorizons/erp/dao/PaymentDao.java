package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.BillingPeriod;
import com.littlehorizons.erp.model.Charge;
import com.littlehorizons.erp.model.DebtorSummary;
import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.Payment;
import com.littlehorizons.erp.model.PaymentBatch;
import com.littlehorizons.erp.model.PaymentConcept;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence operations for payment concepts, charges, payments and debtors. */
public interface PaymentDao {

    List<PaymentConcept> findConcepts(boolean onlyActive);

    Optional<PaymentConcept> findConceptByCode(String code);

    /** @return the generated concept id */
    int insertConcept(String code, String name, String description, BigDecimal baseAmount, boolean recurring);

    boolean updateConceptAmount(int conceptId, BigDecimal newAmount);

    /** @return the generated charge id */
    int insertCharge(int studentId, int conceptId, int year, BillingPeriod period, BigDecimal baseValue,
                     BigDecimal discount, String discountReason, LocalDate dueDate);

    /** True when the student already has a charge (not cancelled) of the concept with that due date. */
    boolean chargeExists(int studentId, int conceptId, LocalDate dueDate);

    List<Charge> findPendingCharges(int studentId);

    /** Next number for receipts of the year (REC-2026-000001, ...). */
    int nextReceiptSequence(int year);

    /**
     * Saves every payment of the batch, marks the paid charges and saves the invoice with its lines,
     * all in one transaction.
     *
     * @return the invoice id, or 0 when one of the charges was already paid (nothing is saved)
     */
    int registerPayments(PaymentBatch batch, Invoice invoice);

    List<Payment> findPaymentsByStudent(int studentId);

    /** Payments made by the guardian plus those made for any of their students. */
    List<Payment> findPaymentsByGuardian(int guardianId);

    /** Students whose oldest pending charge is due before the given day, with their total pending debt. */
    List<DebtorSummary> findDebtors(LocalDate dueBefore, LocalDate today);
}