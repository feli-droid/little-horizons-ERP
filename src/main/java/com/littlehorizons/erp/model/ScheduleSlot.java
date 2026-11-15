package com.littlehorizons.erp.model;

import java.time.Duration;
import java.time.LocalTime;

/** A weekly class slot of an assignment. */
public record ScheduleSlot(int id, int assignmentId, SchoolDay day, LocalTime start, LocalTime end,
                           String description) {

    public long minutes() {
        return Duration.between(start, end).toMinutes();
    }
}