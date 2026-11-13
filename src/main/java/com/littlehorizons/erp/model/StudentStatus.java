package com.littlehorizons.erp.model;

/** Life-cycle status of a student in the school. */
public enum StudentStatus {
    ACTIVE("Activo"),
    GRADUATED("Graduado"),
    WITHDRAWN("Retirado");

    private final String dbValue;

    StudentStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static StudentStatus fromDb(String value) {
        for (StudentStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown student status: " + value);
    }
}