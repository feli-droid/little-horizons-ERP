package com.littlehorizons.erp.model;

/** Academic period a charge or payment belongs to. */
public enum BillingPeriod {
    I("I"),
    II("II"),
    III("III"),
    IV("IV"),
    ANNUAL("Anual");

    private final String dbValue;

    BillingPeriod(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static BillingPeriod fromDb(String value) {
        for (BillingPeriod period : values()) {
            if (period.dbValue.equals(value)) {
                return period;
            }
        }
        throw new IllegalArgumentException("Unknown billing period: " + value);
    }
}