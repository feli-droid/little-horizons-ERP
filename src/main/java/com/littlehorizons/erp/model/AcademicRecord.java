package com.littlehorizons.erp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A grade (0.0 - 5.0) of a student in a subject for one academic period. */
public record AcademicRecord(int id, int studentId, int subjectId, String subjectName, String gradeName,
                             int teacherId, int year, AcademicPeriod period, BigDecimal score,
                             String observations, LocalDate recordDate) {
}