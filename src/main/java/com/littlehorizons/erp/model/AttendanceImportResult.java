package com.littlehorizons.erp.model;

/** Outcome of a CSV attendance import: how many data lines were saved and how many were rejected. */
public record AttendanceImportResult(int imported, int failed) {

    /** Data lines read from the file (header and blank lines are not counted). */
    public int total() {
        return imported + failed;
    }
}