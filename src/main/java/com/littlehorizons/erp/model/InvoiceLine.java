package com.littlehorizons.erp.model;

import java.math.BigDecimal;

/** One line of an invoice. */
public record InvoiceLine(String description, int quantity, BigDecimal unitValue, BigDecimal subtotal) {
}