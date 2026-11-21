package com.littlehorizons.erp.model;

/** Attendance of one student in a year: how many records of each status it has. */
public record AttendanceSummary(int studentId, String fullName, long present, long absent, long late,
                                long medicalExcuse) {

    public long total() {
        return present + absent + late + medicalExcuse;
    }
}