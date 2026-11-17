package com.littlehorizons.erp.model;

/** Ways a guardian can pay. */
public enum PaymentMethod {
    CASH("Efectivo"),
    TRANSFER("Transferencia"),
    DEBIT_CARD("Tarjeta Débito"),
    CREDIT_CARD("Tarjeta Crédito"),
    CHECK("Cheque");

    private final String dbValue;

    PaymentMethod(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static PaymentMethod fromDb(String value) {
        for (PaymentMethod method : values()) {
            if (method.dbValue.equals(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + value);
    }
}