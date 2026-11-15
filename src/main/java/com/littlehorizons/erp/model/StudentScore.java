package com.littlehorizons.erp.model;

import java.math.BigDecimal;

/** A student together with a score (used to list students who need support). */
public record StudentScore(EnrolledStudent student, BigDecimal score) {
}