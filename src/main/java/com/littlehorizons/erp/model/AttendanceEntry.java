package com.littlehorizons.erp.model;

import java.time.LocalDate;

/** One attendance mark of a student in a subject. */
public record AttendanceEntry(LocalDate date, String subjectName, AttendanceStatus status) {
}