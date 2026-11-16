package com.littlehorizons.erp.model;

/** Status of a student's membership in a club. */
public enum MembershipStatus {
    ACTIVE("Activo"),
    WITHDRAWN("Retirado");

    private final String dbValue;

    MembershipStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static MembershipStatus fromDb(String value) {
        for (MembershipStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown membership status: " + value);
    }
}