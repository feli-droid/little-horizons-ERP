package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.AcademicController;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.exception.DataAccessException;
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
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.InputValidator;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Console menu for the academic module: grades, averages and attendance. */
public class AcademicView {

    private static final int EXIT_OPTION = 0;
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final AcademicController controller;

    public AcademicView(AcademicController controller) {
        this.controller = controller;
    }

    public void run() {
        boolean back = false;
        while (!back) {
            printMenu();
            int option = InputValidator.readInt("Seleccione una opción: ", 0, 8);
            try {
                switch (option) {
                    case 1 -> registerScores();
                    case 2 -> showHistory();
                    case 3 -> showStudentAverages();
                    case 4 -> showCourseAverages();
                    case 5 -> showStudentsNeedingSupport();
                    case 6 -> registerAttendance();
                    case 7 -> showAbsenceReport();
                    case 8 -> showStudentAttendance();
                    default -> back = option == EXIT_OPTION;
                }
            } catch (BusinessException e) {
                ErrorLogger.warn("Business rule rejected: " + e.getMessage());
                System.out.println("\n[ERROR] " + e.getMessage());
            } catch (DataAccessException e) {
                System.out.println("\n[ERROR] " + e.getMessage());
            }
            if (!back) {
                InputValidator.waitForEnter();
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   GESTIÓN ACADÉMICA                          ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("1. Registrar notas de un curso");
        System.out.println("2. Historial académico de un estudiante");
        System.out.println("3. Promedios de un estudiante");
        System.out.println("4. Promedios de un curso");
        System.out.println("5. Estudiantes que necesitan apoyo");
        System.out.println("6. Registrar asistencia");
        System.out.println("7. Reporte de inasistencias de la semana");
        System.out.println("8. Asistencia de un estudiante");
        System.out.println("0. Volver al menú principal");
    }

    // ------------------------------------------------------------------- grades

    private void registerScores() throws BusinessException {
        Assignment assignment = chooseAssignment(findActiveTeacher());
        AcademicPeriod period = choosePeriod();
        List<EnrolledStudent> students = controller.getEnrolledStudents(assignment);

        System.out.println("\n" + assignment.subject().name() + " - " + assignment.subject().gradeName()
                + " | Periodo " + period.getDbValue() + " | " + students.size() + " estudiantes");
        System.out.println("Escriba la nota (0.0 a 5.0) o presione Enter para omitir al estudiante.\n");
        int created = 0;
        int updated = 0;
        for (EnrolledStudent student : students) {
            String current = controller.findScore(assignment, student, period)
                    .map(r -> r.score().toPlainString()).orElse("-");
            String text = ConsoleForm.readOptionalField(
                    String.format("%-30s nota actual: %-4s nueva: ", student.fullName(), current), controller::parseScore);
            if (text == null) {
                continue;
            }
            BigDecimal score = controller.parseScore(text);
            String observations = score.compareTo(AcademicController.PASSING_SCORE) < 0
                    ? InputValidator.readOptionalText("    Observación (opcional): ") : null;
            if (controller.saveScore(assignment, student, period, score, observations)) {
                created++;
            } else {
                updated++;
            }
        }
        System.out.println("\n[OK] Notas nuevas: " + created + " | Notas actualizadas: " + updated);
    }

    private void showHistory() throws BusinessException {
        Student student = findStudent();
        List<AcademicRecord> records = controller.getHistory(student);
        System.out.println("\nHistorial académico de " + student.getFullName() + ":");
        if (records.isEmpty()) {
            System.out.println("  (sin notas registradas)");
            return;
        }
        System.out.printf("%-6s %-8s %-22s %-12s %-5s %s%n", "Año", "Periodo", "Materia", "Grado", "Nota", "Observaciones");
        System.out.println("-".repeat(80));
        records.forEach(r -> System.out.printf("%-6d %-8s %-22s %-12s %-5s %s%n", r.year(), r.period().getDbValue(),
                r.subjectName(), r.gradeName(), r.score().toPlainString(), ConsoleForm.orDash(r.observations())));
        System.out.println("\nTotal de notas: " + records.size());
    }

    private void showStudentAverages() throws BusinessException {
        Student student = findStudent();
        int year = InputValidator.readInt("Año académico: ", MIN_YEAR, MAX_YEAR);
        List<SubjectAverage> averages = controller.getSubjectAverages(student, year);
        if (averages.isEmpty()) {
            throw new BusinessException("El estudiante no tiene notas registradas en " + year + ".");
        }
        System.out.println("\nPromedio por materia de " + student.getFullName() + " en " + year + " (periodos I a IV):");
        averages.forEach(a -> System.out.printf("  %-24s %s  (%d notas)%n", a.subjectName(),
                AcademicController.round(a.average()), a.gradeCount()));
        System.out.println("\nPromedio general por periodo:");
        controller.getPeriodAverages(student, year)
                .forEach((period, avg) -> System.out.printf("  Periodo %-6s %s%n", period.getDbValue(), avg));
    }

    private void showCourseAverages() throws BusinessException {
        Grade grade = ConsoleForm.choose("Grado", controller.getGrades(), Grade::getName);
        int year = InputValidator.readInt("Año académico: ", MIN_YEAR, MAX_YEAR);
        AcademicPeriod period = choosePeriod();
        List<SubjectAverage> averages = controller.getCourseAverages(grade, year, period);
        if (averages.isEmpty()) {
            throw new BusinessException("No hay notas de " + grade.getName() + " en el periodo " + period.getDbValue() + " de " + year + ".");
        }
        System.out.println("\nPromedio del curso " + grade.getName() + " - periodo " + period.getDbValue() + " de " + year + ":");
        averages.forEach(a -> System.out.printf("  %-24s %s  (%d notas)%n", a.subjectName(),
                AcademicController.round(a.average()), a.gradeCount()));
        controller.overallAverage(averages)
                .ifPresent(avg -> System.out.println("\nPromedio general del curso: " + avg));
    }

    private void showStudentsNeedingSupport() throws BusinessException {
        Assignment assignment = chooseAssignment(findActiveTeacher());
        AcademicPeriod period = choosePeriod();
        List<StudentScore> students = controller.getStudentsNeedingSupport(assignment, period);
        System.out.println("\n" + assignment.subject().name() + " - " + assignment.subject().gradeName()
                + " | Periodo " + period.getDbValue() + " | Notas inferiores a " + AcademicController.PASSING_SCORE);
        if (students.isEmpty()) {
            System.out.println("  Ningún estudiante necesita apoyo en este periodo.");
            return;
        }
        students.forEach(s -> System.out.printf("  %-12s %-32s %s%n", s.student().documentNumber(),
                s.student().fullName(), s.score().toPlainString()));
        System.out.println("\nEstudiantes que necesitan apoyo: " + students.size());
    }

    // --------------------------------------------------------------- attendance

    private void registerAttendance() throws BusinessException {
        Assignment assignment = chooseAssignment(findActiveTeacher());
        LocalDate date = readAttendanceDate();
        List<EnrolledStudent> students = controller.getEnrolledStudents(assignment);
        Map<Integer, AttendanceStatus> saved = controller.getAttendance(assignment, date);

        System.out.println("\n" + assignment.subject().name() + " - " + assignment.subject().gradeName() + " | " + date);
        System.out.println("Estados: P = Presente, A = Ausente, T = Tardanza, E = Excusa médica. Enter = valor por defecto.\n");
        Map<Integer, AttendanceStatus> statuses = new HashMap<>();
        for (EnrolledStudent student : students) {
            AttendanceStatus defaultStatus = saved.getOrDefault(student.studentId(), AttendanceStatus.PRESENT);
            statuses.put(student.studentId(), readStatus(student.fullName(), defaultStatus));
        }
        controller.saveAttendance(assignment, date, statuses);
        System.out.println("\n[OK] Asistencia guardada para " + statuses.size() + " estudiantes:");
        statuses.values().stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .forEach((status, count) -> System.out.println("  " + status.getDbValue() + ": " + count));
    }

    private void showAbsenceReport() {
        LocalDate date = InputValidator.readDate("Cualquier fecha de la semana a consultar");
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        List<AbsenceSummary> report = controller.weeklyAbsenceReport(date);
        System.out.println("\nSemana del " + monday + " al " + monday.plusDays(4)
                + " | Más de " + AcademicController.WEEKLY_ABSENCE_LIMIT + " ausencias:");
        if (report.isEmpty()) {
            System.out.println("  Ningún estudiante supera el límite de ausencias.");
            return;
        }
        report.forEach(a -> System.out.printf("  %-12s %-32s %d ausencias%n", a.documentNumber(), a.fullName(), a.absences()));
        System.out.println("\nEstudiantes con inasistencia preocupante: " + report.size());
    }

    private void showStudentAttendance() throws BusinessException {
        Student student = findStudent();
        LocalDate from = InputValidator.readDate("Fecha inicial");
        LocalDate to = InputValidator.readDate("Fecha final");
        List<AttendanceEntry> entries = controller.getStudentAttendance(student, from, to);
        System.out.println("\nAsistencia de " + student.getFullName() + " entre " + from + " y " + to + ":");
        if (entries.isEmpty()) {
            System.out.println("  (sin registros en ese rango)");
            return;
        }
        entries.forEach(e -> System.out.printf("  %s  %-24s %s%n", e.date(), e.subjectName(), e.status().getDbValue()));
        System.out.println("\nResumen:");
        controller.countByStatus(entries).forEach((status, count) -> System.out.println("  " + status.getDbValue() + ": " + count));
    }

    // ------------------------------------------------------------------ helpers

    private Teacher findActiveTeacher() throws BusinessException {
        return controller.getActiveTeacher(InputValidator.readText("Documento del profesor: "));
    }

    private Student findStudent() throws BusinessException {
        return controller.getStudentByDocument(InputValidator.readText("Documento del estudiante: "));
    }

    private Assignment chooseAssignment(Teacher teacher) throws BusinessException {
        System.out.println("Profesor: " + teacher.fullName());
        return ConsoleForm.choose("Materia", controller.getAssignments(teacher),
                a -> a.subject().code() + " - " + a.subject().name() + " (" + a.subject().gradeName() + ", secc. " + a.section() + ")");
    }

    private AcademicPeriod choosePeriod() {
        return ConsoleForm.choose("Periodo", Arrays.asList(AcademicPeriod.values()), AcademicPeriod::getDbValue);
    }

    private LocalDate readAttendanceDate() {
        while (true) {
            LocalDate date = InputValidator.readDate("Fecha de la clase");
            try {
                controller.validateAttendanceDate(date);
                return date;
            } catch (BusinessException e) {
                ErrorLogger.warn("Validation failed: " + e.getMessage());
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }

    private AttendanceStatus readStatus(String studentName, AttendanceStatus defaultStatus) {
        while (true) {
            String code = InputValidator.readOptionalText(
                    String.format("%-30s [%s] (P/A/T/E): ", studentName, defaultStatus.getDbValue()));
            if (code == null) {
                return defaultStatus;
            }
            AttendanceStatus status = AttendanceStatus.fromCode(code);
            if (status != null) {
                return status;
            }
            ErrorLogger.warn("Invalid input at attendance status: use P, A, T or E.");
            System.out.println("[ERROR] Use P, A, T o E.");
        }
    }
}