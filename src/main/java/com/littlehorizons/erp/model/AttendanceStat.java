package com.littlehorizons.erp.model;

/** Attendance totals of one grade: all records and how many were Present or Late. */
public record AttendanceStat(String gradeName, long totalRecords, long attended) {

    /** Attendance percentage (0-100); 0 when there are no records. */
    public double percentage() {
        return totalRecords == 0 ? 0.0 : attended * 100.0 / totalRecords;
    }
}