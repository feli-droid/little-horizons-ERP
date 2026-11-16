package com.littlehorizons.erp.model;

/** Status of a loan. */
public enum LoanStatus {
    ACTIVE("Activo"),
    RETURNED("Devuelto"),
    OVERDUE("Vencido");

    private final String dbValue;

    LoanStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static LoanStatus fromDb(String value) {
        for (LoanStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown loan status: " + value);
    }
}