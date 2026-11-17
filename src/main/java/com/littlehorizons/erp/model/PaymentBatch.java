package com.littlehorizons.erp.model;

import java.time.LocalDate;
import java.util.List;

/** Everything saved together when a guardian pays: the payments of several lines and their invoice. */
public record PaymentBatch(int guardianId, PaymentMethod method, LocalDate date, List<PaymentLine> lines) {
}