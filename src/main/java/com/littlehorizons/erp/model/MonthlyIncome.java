package com.littlehorizons.erp.model;

import java.math.BigDecimal;

/** Total collected in one month (format yyyy-MM) and the number of payments. */
public record MonthlyIncome(String month, int payments, BigDecimal total) {
}