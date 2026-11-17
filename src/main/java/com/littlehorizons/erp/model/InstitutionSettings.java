package com.littlehorizons.erp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** School data and invoicing resolution (simulated) printed on every invoice. */
public record InstitutionSettings(String legalName, String nit, String address, String phone, String email,
                                  String invoicePrefix, String resolutionNumber, LocalDate resolutionDate,
                                  int rangeFrom, int rangeTo, BigDecimal vatPercent) {
}