package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.AttendanceStat;
import com.littlehorizons.erp.model.ClubOccupancy;
import com.littlehorizons.erp.model.MonthlyIncome;
import com.littlehorizons.erp.model.StudentRanking;

import java.util.List;
import java.util.Map;

/** Read-only queries that feed the reports and statistics module. */
public interface ReportDao {

    /** Average of every grade of each active student of the given academic year. */
    List<StudentRanking> findStudentAverages(int year);

    /** Attendance totals grouped by grade. */
    List<AttendanceStat> findAttendanceByGrade();

    /** Active clubs with their number of active members. */
    List<ClubOccupancy> findClubOccupancy();

    /** Collected money grouped by month of the given year. */
    List<MonthlyIncome> findMonthlyIncome(int year);

    /** Number of resources per status. */
    Map<String, Long> countResourcesByStatus();

    /** Headline counters of the institution (students, teachers, clubs, ...). */
    Map<String, Long> findGeneralCounters();
}