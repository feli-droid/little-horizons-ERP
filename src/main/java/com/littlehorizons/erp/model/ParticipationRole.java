package com.littlehorizons.erp.model;

/** Role a student plays in an event. */
public enum ParticipationRole {
    PARTICIPANT("Participante"),
    COMPETITOR("Competidor"),
    ORGANIZER("Organizador"),
    ATTENDEE("Asistente");

    private final String dbValue;

    ParticipationRole(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static ParticipationRole fromDb(String value) {
        for (ParticipationRole role : values()) {
            if (role.dbValue.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown participation role: " + value);
    }
}