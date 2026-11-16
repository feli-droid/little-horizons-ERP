package com.littlehorizons.erp.model;

/** A student participating in an event; the position is only set in events with competition. */
public record EventParticipant(int id, int eventId, int studentId, String studentName, String studentDocument,
                               ParticipationRole role, EventPosition position) {
}