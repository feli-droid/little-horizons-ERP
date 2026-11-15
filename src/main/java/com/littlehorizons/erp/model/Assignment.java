package com.littlehorizons.erp.model;

/** A teacher assigned to a subject for an academic year and section. */
public record Assignment(int id, int teacherId, String teacherName, Subject subject, int year, String section) {
}