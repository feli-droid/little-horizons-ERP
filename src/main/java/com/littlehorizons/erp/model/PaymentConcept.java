package com.littlehorizons.erp.model;

import java.math.BigDecimal;

/** An item the school charges for (tuition, uniform, field trip, ...). */
public record PaymentConcept(int id, String code, String name, String description, BigDecimal baseAmount,
                             boolean recurring, boolean active) {
}