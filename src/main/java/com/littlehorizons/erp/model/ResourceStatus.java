package com.littlehorizons.erp.model;

/** Availability of a school resource. */
public enum ResourceStatus {
    AVAILABLE("Disponible"),
    ON_LOAN("Prestado"),
    IN_MAINTENANCE("En Mantenimiento"),
    DISCARDED("Dado de Baja");

    private final String dbValue;

    ResourceStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static ResourceStatus fromDb(String value) {
        for (ResourceStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown resource status: " + value);
    }
}