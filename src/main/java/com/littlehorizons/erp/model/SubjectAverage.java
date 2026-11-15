package com.littlehorizons.erp.model;

import java.math.BigDecimal;

/** Average score of a subject and how many grades it is based on. */
public record SubjectAverage(String subjectName, BigDecimal average, long gradeCount) {
}