package com.littlehorizons.erp.model;

import java.time.LocalDate;

/** One event in which a student took part. */
public record StudentParticipation(String eventName, EventType type, LocalDate date, ParticipationRole role,
                                   EventPosition position) {
}