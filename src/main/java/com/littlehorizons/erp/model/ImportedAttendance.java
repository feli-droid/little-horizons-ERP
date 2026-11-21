package com.littlehorizons.erp.model;

import java.time.LocalDate;

/** One attendance record taken from a valid line of the import file. */
public record ImportedAttendance(int studentId, int subjectId, LocalDate date, AttendanceStatus status) {
}