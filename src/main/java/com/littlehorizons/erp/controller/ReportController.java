package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.ReportDao;
import com.littlehorizons.erp.model.AttendanceStat;
import com.littlehorizons.erp.model.ClubOccupancy;
import com.littlehorizons.erp.model.MonthlyIncome;
import com.littlehorizons.erp.model.StudentRanking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Builds the statistics of the reports module from raw DAO data using Streams. */
public class ReportController {

    /** Grades under this value need academic support (same rule as the academic module). */
    private static final BigDecimal SUPPORT_THRESHOLD = new BigDecimal("3.0");
    private static final int DEFAULT_TOP = 10;

    private final ReportDao reportDao;

    public ReportController(ReportDao reportDao) {
        this.reportDao = reportDao;
    }

    public int currentYear() {
        return LocalDate.now().getYear();
    }

    public int defaultTop() {
        return DEFAULT_TOP;
    }

    /** Best students of the year, highest average first. */
    public List<StudentRanking> topStudents(int year, int limit) {
        return reportDao.findStudentAverages(year).stream()
                .sorted(Comparator.comparing(StudentRanking::average).reversed()
                        .thenComparing(StudentRanking::studentName))
                .limit(limit)
                .toList();
    }

    /** Students whose average is below the support threshold, lowest first. */
    public List<StudentRanking> studentsAtRisk(int year) {
        return reportDao.findStudentAverages(year).stream()
                .filter(s -> s.average().compareTo(SUPPORT_THRESHOLD) < 0)
                .sorted(Comparator.comparing(StudentRanking::average))
                .toList();
    }

    /** Average of the student averages of each grade, in grade-name order of appearance. */
    public Map<String, BigDecimal> averageByGrade(int year) {
        Map<String, List<StudentRanking>> byGrade = reportDao.findStudentAverages(year).stream()
                .collect(Collectors.groupingBy(StudentRanking::gradeName, TreeMap::new, Collectors.toList()));
        Map<String, BigDecimal> result = new TreeMap<>();
        byGrade.forEach((grade, students) -> {
            BigDecimal sum = students.stream().map(StudentRanking::average).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(grade, sum.divide(BigDecimal.valueOf(students.size()), 2, RoundingMode.HALF_UP));
        });
        return result;
    }

    public List<AttendanceStat> attendanceByGrade() {
        return reportDao.findAttendanceByGrade();
    }

    /** Clubs ordered from the fullest to the emptiest. */
    public List<ClubOccupancy> clubOccupancy() {
        return reportDao.findClubOccupancy().stream()
                .sorted(Comparator.comparingDouble(ClubOccupancy::occupancyPercent).reversed())
                .toList();
    }

    public List<MonthlyIncome> monthlyIncome(int year) {
        return reportDao.findMonthlyIncome(year);
    }

    public BigDecimal totalIncome(List<MonthlyIncome> months) {
        return months.stream().map(MonthlyIncome::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, Long> resourcesByStatus() {
        return reportDao.countResourcesByStatus();
    }

    public Map<String, Long> generalCounters() {
        return reportDao.findGeneralCounters();
    }
}