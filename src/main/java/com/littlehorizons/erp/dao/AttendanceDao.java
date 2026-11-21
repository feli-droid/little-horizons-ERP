package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.AbsenceSummary;
import com.littlehorizons.erp.model.AttendanceEntry;
import com.littlehorizons.erp.model.AttendanceStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Persistence operations for daily attendance. */
public interface AttendanceDao {

    /** Attendance already recorded for a subject on a date, by student id. */
    Map<Integer, AttendanceStatus> findByCourseAndDate(int subjectId, LocalDate date);

    /** Saves (inserts or updates) the attendance of several students in one transaction. */
    void saveAll(int subjectId, LocalDate date, int teacherId, Map<Integer, AttendanceStatus> statuses);

    /** Students with more than {@code maxAbsences} absences between the two dates. */
    List<AbsenceSummary> findAbsenceReport(LocalDate from, LocalDate to, int maxAbsences);

    List<AttendanceEntry> findByStudent(int studentId, LocalDate from, LocalDate to);

    /** Full name of each of the given students, by student id. */
    Map<Integer, String> findStudentNames(Collection<Integer> studentIds);

    boolean studentExists(int studentId);

    boolean subjectExists(int subjectId);

    /** Saves one imported attendance record; the teacher who registered it stays NULL. */
    void insertImported(int studentId, int subjectId, LocalDate date, AttendanceStatus status);
}