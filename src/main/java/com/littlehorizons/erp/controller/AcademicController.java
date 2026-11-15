package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.dao.AssignmentDao;
import com.littlehorizons.erp.dao.AttendanceDao;
import com.littlehorizons.erp.dao.GradeDao;
import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.dao.TeacherDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.AbsenceSummary;
import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.AcademicRecord;
import com.littlehorizons.erp.model.Assignment;
import com.littlehorizons.erp.model.AttendanceEntry;
import com.littlehorizons.erp.model.AttendanceStatus;
import com.littlehorizons.erp.model.EnrolledStudent;
import com.littlehorizons.erp.model.Grade;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.StudentScore;
import com.littlehorizons.erp.model.SubjectAverage;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.model.TeacherStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Business rules for grades, averages and attendance. */
public class AcademicController {

    /** Scores below this value mean the student needs extra support. */
    public static final BigDecimal PASSING_SCORE = new BigDecimal("3.0");
    /** A student with MORE absences than this in one week appears in the absence report. */
    public static final int WEEKLY_ABSENCE_LIMIT = 3;

    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("5.0");

    private final AcademicDao academicDao;
    private final AttendanceDao attendanceDao;
    private final AssignmentDao assignmentDao;
    private final TeacherDao teacherDao;
    private final StudentDao studentDao;
    private final GradeDao gradeDao;

    public AcademicController(AcademicDao academicDao, AttendanceDao attendanceDao, AssignmentDao assignmentDao,
                              TeacherDao teacherDao, StudentDao studentDao, GradeDao gradeDao) {
        this.academicDao = academicDao;
        this.attendanceDao = attendanceDao;
        this.assignmentDao = assignmentDao;
        this.teacherDao = teacherDao;
        this.studentDao = studentDao;
        this.gradeDao = gradeDao;
    }

    // ------------------------------------------------------------------ lookups

    public Teacher getActiveTeacher(String documentNumber) throws BusinessException {
        Teacher teacher = teacherDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un profesor con el documento " + documentNumber + "."));
        if (teacher.status() != TeacherStatus.ACTIVE) {
            throw new BusinessException("El profesor está en estado " + teacher.status().getDbValue() + " y no puede registrar información.");
        }
        return teacher;
    }

    public List<Assignment> getAssignments(Teacher teacher) throws BusinessException {
        List<Assignment> assignments = assignmentDao.findByTeacher(teacher.id(), TeacherController.currentYear());
        if (assignments.isEmpty()) {
            throw new BusinessException("El profesor no tiene materias asignadas en " + TeacherController.currentYear() + ".");
        }
        return assignments;
    }

    public Student getStudentByDocument(String documentNumber) throws BusinessException {
        return studentDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un estudiante con el documento " + documentNumber + "."));
    }

    public List<Grade> getGrades() {
        return gradeDao.findAll();
    }

    public List<EnrolledStudent> getEnrolledStudents(Assignment assignment) throws BusinessException {
        List<EnrolledStudent> students = academicDao.findEnrolledStudents(assignment.subject().id(), assignment.year());
        if (students.isEmpty()) {
            throw new BusinessException("No hay estudiantes inscritos en " + assignment.subject().name() + ".");
        }
        return students;
    }

    // ------------------------------------------------------------------ grades

    /** Parses a score typed by the user: between 0.0 and 5.0 with at most one decimal. */
    public BigDecimal parseScore(String text) throws BusinessException {
        BigDecimal value;
        try {
            value = new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new BusinessException("La nota debe ser un número entre 0.0 y 5.0.");
        }
        if (value.compareTo(MIN_SCORE) < 0 || value.compareTo(MAX_SCORE) > 0) {
            throw new BusinessException("La nota debe estar entre 0.0 y 5.0.");
        }
        if (value.stripTrailingZeros().scale() > 1) {
            throw new BusinessException("La nota admite un solo decimal (ej. 4.5).");
        }
        return value.setScale(1, RoundingMode.UNNECESSARY);
    }

    public Optional<AcademicRecord> findScore(Assignment assignment, EnrolledStudent student, AcademicPeriod period) {
        return academicDao.findScore(student.studentId(), assignment.subject().id(), assignment.year(), period);
    }

    /** Saves a score, creating it or replacing the existing one. @return true if it was created. */
    public boolean saveScore(Assignment assignment, EnrolledStudent student, AcademicPeriod period,
                             BigDecimal score, String observations) {
        Optional<AcademicRecord> existing = findScore(assignment, student, period);
        LocalDate today = LocalDate.now();
        if (existing.isPresent()) {
            academicDao.updateScore(existing.get().id(), assignment.teacherId(), score, observations, today);
            return false;
        }
        academicDao.insertScore(student.studentId(), assignment.subject().id(), assignment.teacherId(),
                assignment.year(), period, score, observations, today);
        return true;
    }

    public List<AcademicRecord> getHistory(Student student) {
        return academicDao.findHistory(student.getId());
    }

    public List<SubjectAverage> getSubjectAverages(Student student, int year) {
        return academicDao.findStudentSubjectAverages(student.getId(), year);
    }

    /** Average of the student in each period (only the periods that already have grades). */
    public Map<AcademicPeriod, BigDecimal> getPeriodAverages(Student student, int year) {
        Map<AcademicPeriod, BigDecimal> averages = new EnumMap<>(AcademicPeriod.class);
        for (AcademicPeriod period : AcademicPeriod.values()) {
            academicDao.findStudentPeriodAverage(student.getId(), year, period)
                    .ifPresent(avg -> averages.put(period, round(avg)));
        }
        return averages;
    }

    public List<SubjectAverage> getCourseAverages(Grade grade, int year, AcademicPeriod period) {
        return academicDao.findCourseAverages(grade.getId(), year, period);
    }

    /** General average of a course: every grade weighs the same, whatever the subject. */
    public Optional<BigDecimal> overallAverage(List<SubjectAverage> averages) {
        long total = averages.stream().mapToLong(SubjectAverage::gradeCount).sum();
        if (total == 0) {
            return Optional.empty();
        }
        BigDecimal weighted = averages.stream()
                .map(a -> a.average().multiply(BigDecimal.valueOf(a.gradeCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(round(weighted.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)));
    }

    public List<StudentScore> getStudentsNeedingSupport(Assignment assignment, AcademicPeriod period) {
        return academicDao.findStudentsBelow(assignment.subject().id(), assignment.year(), period, PASSING_SCORE);
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------- attendance

    public void validateAttendanceDate(LocalDate date) throws BusinessException {
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("No se puede registrar asistencia de una fecha futura.");
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new BusinessException("Solo hay clases de lunes a viernes.");
        }
    }

    public Map<Integer, AttendanceStatus> getAttendance(Assignment assignment, LocalDate date) {
        return attendanceDao.findByCourseAndDate(assignment.subject().id(), date);
    }

    public void saveAttendance(Assignment assignment, LocalDate date, Map<Integer, AttendanceStatus> statuses)
            throws BusinessException {
        validateAttendanceDate(date);
        attendanceDao.saveAll(assignment.subject().id(), date, assignment.teacherId(), statuses);
    }

    /** Students with more than {@link #WEEKLY_ABSENCE_LIMIT} absences in the Monday-Friday week of the date. */
    public List<AbsenceSummary> weeklyAbsenceReport(LocalDate anyDayOfWeek) {
        LocalDate monday = anyDayOfWeek.with(DayOfWeek.MONDAY);
        return attendanceDao.findAbsenceReport(monday, monday.plusDays(4), WEEKLY_ABSENCE_LIMIT);
    }

    public List<AttendanceEntry> getStudentAttendance(Student student, LocalDate from, LocalDate to)
            throws BusinessException {
        if (to.isBefore(from)) {
            throw new BusinessException("La fecha final no puede ser anterior a la inicial.");
        }
        return attendanceDao.findByStudent(student.getId(), from, to);
    }

    /** Counts the entries of each attendance status (uses streams to group and count). */
    public Map<AttendanceStatus, Long> countByStatus(List<AttendanceEntry> entries) {
        return entries.stream().collect(Collectors.groupingBy(AttendanceEntry::status,
                () -> new EnumMap<>(AttendanceStatus.class), Collectors.counting()));
    }
}