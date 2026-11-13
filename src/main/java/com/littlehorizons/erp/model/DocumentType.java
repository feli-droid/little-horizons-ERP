package com.littlehorizons.erp.model;

/** Identity document types accepted by the school. */
public enum DocumentType {
    RC("RC", "Registro Civil"),
    TI("TI", "Tarjeta de Identidad"),
    CC("CC", "Cédula de Ciudadanía"),
    CE("CE", "Cédula de Extranjería"),
    PASSPORT("Pasaporte", "Pasaporte");

    private final String dbValue;
    private final String label;

    DocumentType(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getLabel() {
        return label;
    }

    public static DocumentType fromDb(String value) {
        for (DocumentType type : values()) {
            if (type.dbValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown document type: " + value);
    }
}