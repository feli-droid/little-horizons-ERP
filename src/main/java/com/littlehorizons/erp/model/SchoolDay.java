package com.littlehorizons.erp.model;

import java.time.DayOfWeek;

/** School days (Monday to Friday) as stored in the class schedule table. */
public enum SchoolDay {
    MONDAY("Lunes", "Lunes"),
    TUESDAY("Martes", "Martes"),
    WEDNESDAY("Miercoles", "Miércoles"),
    THURSDAY("Jueves", "Jueves"),
    FRIDAY("Viernes", "Viernes");

    private final String dbValue;
    private final String label;

    SchoolDay(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getLabel() {
        return label;
    }

    public static SchoolDay fromDb(String value) {
        for (SchoolDay day : values()) {
            if (day.dbValue.equals(value)) {
                return day;
            }
        }
        throw new IllegalArgumentException("Unknown school day: " + value);
    }

    public static SchoolDay fromDayOfWeek(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.getValue() - 1];
    }
}