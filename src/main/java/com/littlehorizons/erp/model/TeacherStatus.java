package com.littlehorizons.erp.model;

/** Employment status of a teacher. */
public enum TeacherStatus {
    ACTIVE("Activo"),
    INACTIVE("Inactivo"),
    ON_LEAVE("Licencia");

    private final String dbValue;

    TeacherStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static TeacherStatus fromDb(String value) {
        for (TeacherStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown teacher status: " + value);
    }
}