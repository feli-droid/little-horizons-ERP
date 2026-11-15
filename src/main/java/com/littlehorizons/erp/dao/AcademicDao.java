package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.AcademicRecord;
import com.littlehorizons.erp.model.EnrolledStudent;
import com.littlehorizons.erp.model.StudentScore;
import com.littlehorizons.erp.model.SubjectAverage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence operations for grades (academic records) and their averages. */
public interface AcademicDao {

    /** Active students enrolled in the subject during the year. */
    List<EnrolledStudent> findEnrolledStudents(int subjectId, int year);

    Optional<AcademicRecord> findScore(int studentId, int subjectId, int year, AcademicPeriod period);

    void insertScore(int studentId, int subjectId, int teacherId, int year, AcademicPeriod period,
                     BigDecimal score, String observations, LocalDate recordDate);

    void updateScore(int recordId, int teacherId, BigDecimal score, String observations, LocalDate recordDate);

    /** Every grade of the student since enrollment, oldest first. */
    List<AcademicRecord> findHistory(int studentId);

    /** Average per subject over periods I to IV of the year (the FINAL grade is excluded). */
    List<SubjectAverage> findStudentSubjectAverages(int studentId, int year);

    /** Average of all the subjects of the student in one period, if there are grades. */
    Optional<BigDecimal> findStudentPeriodAverage(int studentId, int year, AcademicPeriod period);

    /** Average of every subject of a grade in one period. */
    List<SubjectAverage> findCourseAverages(int gradeId, int year, AcademicPeriod period);

    /** Students whose score in the subject and period is below the threshold. */
    List<StudentScore> findStudentsBelow(int subjectId, int year, AcademicPeriod period, BigDecimal threshold);
}