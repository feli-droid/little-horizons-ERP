package com.littlehorizons.erp.model;

import java.time.LocalDate;

/** An event of the school calendar. */
public record SchoolEvent(int id, String name, String description, EventType type, LocalDate date, String place,
                          boolean competition, int organizerId, String organizerName, boolean active,
                          int participantCount) {
}