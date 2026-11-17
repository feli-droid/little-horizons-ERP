package com.littlehorizons.erp.model;

/** Status of an amount charged to a student. */
public enum ChargeStatus {
    PENDING("Pendiente"),
    PAID("Pagado"),
    CANCELLED("Anulado");

    private final String dbValue;

    ChargeStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static ChargeStatus fromDb(String value) {
        for (ChargeStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown charge status: " + value);
    }
}