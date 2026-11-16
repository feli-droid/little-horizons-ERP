package com.littlehorizons.erp.model;

/** Position obtained by a competitor in an event with competition. */
public enum EventPosition {
    FIRST("1er Lugar"),
    SECOND("2do Lugar"),
    THIRD("3er Lugar"),
    PARTICIPANT("Participante");

    private final String dbValue;

    EventPosition(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    /** Podium positions can be given to only one competitor per event. */
    public boolean isPodium() {
        return this != PARTICIPANT;
    }

    public static EventPosition fromDb(String value) {
        if (value == null) {
            return null;
        }
        for (EventPosition position : values()) {
            if (position.dbValue.equals(value)) {
                return position;
            }
        }
        throw new IllegalArgumentException("Unknown event position: " + value);
    }
}