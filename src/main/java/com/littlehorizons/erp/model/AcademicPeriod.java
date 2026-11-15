package com.littlehorizons.erp.model;

/** Academic periods of the school year; FINAL is the end-of-year grade. */
public enum AcademicPeriod {
    I("I"),
    II("II"),
    III("III"),
    IV("IV"),
    FINAL("Final");

    private final String dbValue;

    AcademicPeriod(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static AcademicPeriod fromDb(String value) {
        for (AcademicPeriod period : values()) {
            if (period.dbValue.equals(value)) {
                return period;
            }
        }
        throw new IllegalArgumentException("Unknown academic period: " + value);
    }
}