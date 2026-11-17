package com.littlehorizons.erp.model;

import java.math.BigDecimal;

/** Academic average of one student for a year, used by the ranking and risk reports. */
public record StudentRanking(int studentId, String documentNumber, String studentName, String gradeName,
                             BigDecimal average, int gradesCount) {
}