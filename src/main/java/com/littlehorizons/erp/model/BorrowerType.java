package com.littlehorizons.erp.model;

/** Who borrows a resource; each type has its own loans table. */
public enum BorrowerType {
    STUDENT("Estudiante", 3),
    TEACHER("Profesor", 5);

    private final String label;
    private final int maxOpenLoans;

    BorrowerType(String label, int maxOpenLoans) {
        this.label = label;
        this.maxOpenLoans = maxOpenLoans;
    }

    public String getLabel() {
        return label;
    }

    /** Maximum number of loans a borrower of this type can have open at the same time. */
    public int getMaxOpenLoans() {
        return maxOpenLoans;
    }
}