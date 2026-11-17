package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.InstitutionSettings;
import com.littlehorizons.erp.model.Invoice;

import java.util.List;
import java.util.Optional;

/** Persistence operations for invoices and the school invoicing settings. */
public interface InvoiceDao {

    InstitutionSettings findSettings();

    /** Next consecutive number for invoices with the given prefix. */
    int nextSequence(String prefix);

    /** Finds an invoice with its lines. */
    Optional<Invoice> findByNumber(String number);

    /** Most recent invoices (without lines). */
    List<Invoice> findLatest(int limit);

    void updateFileInfo(int invoiceId, String fileName, String filePath);
}