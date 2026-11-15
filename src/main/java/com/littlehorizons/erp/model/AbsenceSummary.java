package com.littlehorizons.erp.model;

/** Number of absences of a student in a period of time. */
public record AbsenceSummary(int studentId, String documentNumber, String fullName, long absences) {
}