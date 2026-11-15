package com.littlehorizons.erp.model;

/** Attendance status of a student in a class. */
public enum AttendanceStatus {
    PRESENT("Presente", "P"),
    ABSENT("Ausente", "A"),
    LATE("Tardanza", "T"),
    MEDICAL_EXCUSE("Excusa Médica", "E");

    private final String dbValue;
    private final String code;

    AttendanceStatus(String dbValue, String code) {
        this.dbValue = dbValue;
        this.code = code;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getCode() {
        return code;
    }

    public static AttendanceStatus fromDb(String value) {
        for (AttendanceStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown attendance status: " + value);
    }

    /** Finds a status by its one-letter code (P, A, T, E), ignoring case; null if no match. */
    public static AttendanceStatus fromCode(String code) {
        for (AttendanceStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}