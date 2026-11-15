package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.Assignment;
import com.littlehorizons.erp.model.DocumentType;
import com.littlehorizons.erp.model.ScheduleSlot;
import com.littlehorizons.erp.model.SchoolDay;
import com.littlehorizons.erp.model.Subject;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.model.TeacherStatus;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.InputValidator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Console menu for the teachers module. */
public class TeacherView {

    private static final int EXIT_OPTION = 0;

    private final TeacherController controller;

    public TeacherView(TeacherController controller) {
        this.controller = controller;
    }

    public void run() {
        boolean back = false;
        while (!back) {
            printMenu();
            int option = InputValidator.readInt("Seleccione una opción: ", 0, 9);
            try {
                switch (option) {
                    case 1 -> registerTeacher();
                    case 2 -> printTeacherTable(controller.listTeachers());
                    case 3 -> printTeacherTable(controller.searchTeachers(InputValidator.readText("Documento o nombre a buscar: ")));
                    case 4 -> changeStatus();
                    case 5 -> updateContact();
                    case 6 -> assignSubject();
                    case 7 -> showWorkload();
                    case 8 -> addSchedule();
                    case 9 -> showWeeklySchedule();
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
        System.out.println("║   GESTIÓN DE PROFESORES                      ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("1. Registrar profesor");
        System.out.println("2. Listar profesores");
        System.out.println("3. Buscar profesor (documento o nombre)");
        System.out.println("4. Cambiar estado de un profesor");
        System.out.println("5. Actualizar datos de contacto");
        System.out.println("6. Asignar materia a un profesor");
        System.out.println("7. Ver carga académica de un profesor");
        System.out.println("8. Agregar horario de clase");
        System.out.println("9. Ver horario semanal de un profesor");
        System.out.println("0. Volver al menú principal");
    }

    // ------------------------------------------------------------------ options

    private void registerTeacher() throws BusinessException {
        System.out.println("\n--- Registro de profesor ---");
        DocumentType type = ConsoleForm.choose("Tipo de documento",
                List.of(DocumentType.CC, DocumentType.CE, DocumentType.PASSPORT), DocumentType::getLabel);
        String number = ConsoleForm.readField("Número de documento: ", v -> controller.validateTeacherDocument(type, v));
        String firstName = ConsoleForm.readField("Primer nombre: ", controller::validateName);
        String middleName = ConsoleForm.readOptionalField("Segundo nombre (opcional): ", controller::validateName);
        String lastName = ConsoleForm.readField("Primer apellido: ", controller::validateName);
        String secondLastName = ConsoleForm.readOptionalField("Segundo apellido (opcional): ", controller::validateName);
        String email = ConsoleForm.readField("Correo electrónico: ", controller::validateNewEmail);
        String phone = ConsoleForm.readOptionalField("Teléfono (opcional): ", controller::validatePhone);
        String specialization = InputValidator.readOptionalText("Especialización (opcional): ");
        LocalDate hireDate = readHireDate();

        Teacher teacher = new Teacher(0, type, number, firstName, middleName, lastName, secondLastName,
                email, phone, specialization, hireDate, TeacherStatus.ACTIVE);
        System.out.println("\nResumen: " + teacher.fullName() + " (" + type.getDbValue() + " " + number + ") - "
                + ConsoleForm.orDash(specialization));
        if (!InputValidator.confirm("¿Confirma el registro?")) {
            System.out.println("[INFO] Registro cancelado.");
            return;
        }
        int id = controller.registerTeacher(teacher);
        System.out.println("\n[OK] Profesor registrado correctamente con ID " + id + ".");
    }

    private void changeStatus() throws BusinessException {
        Teacher teacher = findTeacher();
        System.out.println("Estado actual: " + teacher.status().getDbValue());
        TeacherStatus status = ConsoleForm.choose("Nuevo estado", Arrays.asList(TeacherStatus.values()), TeacherStatus::getDbValue);
        if (!InputValidator.confirm("¿Cambiar a " + status.getDbValue() + "?")) {
            System.out.println("[INFO] Operación cancelada.");
            return;
        }
        controller.changeStatus(teacher, status);
        System.out.println("\n[OK] Estado actualizado.");
    }

    private void updateContact() throws BusinessException {
        Teacher teacher = findTeacher();
        System.out.println("Correo actual: " + teacher.email() + " | Teléfono actual: " + ConsoleForm.orDash(teacher.phone()));
        System.out.println("Presione Enter sin escribir para conservar el valor actual.");
        String email = ConsoleForm.readOptionalField("Nuevo correo: ", v -> controller.validateEmailForUpdate(teacher, v));
        String phone = ConsoleForm.readOptionalField("Nuevo teléfono: ", controller::validatePhone);
        controller.updateContact(teacher, email, phone);
        System.out.println("\n[OK] Datos de contacto actualizados.");
    }

    private void assignSubject() throws BusinessException {
        Teacher teacher = findTeacher();
        System.out.println("Profesor: " + teacher.fullName() + " (" + ConsoleForm.orDash(teacher.specialization()) + ")");
        String code = InputValidator.readText("Código de la materia (escriba LISTAR para ver el catálogo): ");
        while (code.equalsIgnoreCase("LISTAR")) {
            printSubjects(controller.listSubjects());
            code = InputValidator.readText("Código de la materia: ");
        }
        String section = ConsoleForm.readField("Sección (ej. A): ", v -> { });
        Assignment assignment = controller.assignSubject(teacher, code, section);
        System.out.println("\n[OK] " + assignment.subject().name() + " (" + assignment.subject().gradeName()
                + ", sección " + assignment.section() + ") asignada a " + teacher.fullName() + ".");
        System.out.println("     Recuerde registrar su horario (opción 8): la materia requiere "
                + assignment.subject().weeklyHours() + " horas semanales.");
    }

    private void showWorkload() throws BusinessException {
        Teacher teacher = findTeacher();
        List<Assignment> assignments = controller.getAssignments(teacher);
        System.out.println("\nCarga académica de " + teacher.fullName() + " en " + TeacherController.currentYear() + ":");
        if (assignments.isEmpty()) {
            System.out.println("  (sin materias asignadas)");
            return;
        }
        System.out.printf("%-8s %-22s %-12s %-5s %-14s%n", "Código", "Materia", "Grado", "Secc.", "Horas prog./sem.");
        System.out.println("-".repeat(70));
        long totalMinutes = 0;
        for (Assignment a : assignments) {
            long minutes = controller.getScheduleOf(a).stream().mapToLong(ScheduleSlot::minutes).sum();
            totalMinutes += minutes;
            System.out.printf("%-8s %-22s %-12s %-5s %d/%d h%n", a.subject().code(), a.subject().name(),
                    a.subject().gradeName(), a.section(), minutes / 60, a.subject().weeklyHours());
        }
        System.out.println("\nTotal de materias: " + assignments.size() + " | Horas programadas: "
                + TeacherController.formatHours(totalMinutes));
    }

    private void addSchedule() throws BusinessException {
        Teacher teacher = findTeacher();
        List<Assignment> assignments = controller.getAssignments(teacher);
        Assignment assignment = ConsoleForm.choose("Materia", assignments,
                a -> a.subject().code() + " - " + a.subject().name() + " (" + a.subject().gradeName() + ", secc. " + a.section() + ")");
        List<ScheduleSlot> current = controller.getScheduleOf(assignment);
        System.out.println("Horario actual: " + (current.isEmpty() ? "(ninguno)" : ""));
        current.forEach(s -> System.out.println("  " + s.day().getLabel() + " " + s.start() + " - " + s.end()));
        System.out.println("La materia requiere " + assignment.subject().weeklyHours() + " horas semanales.");
        SchoolDay day = ConsoleForm.choose("Día", Arrays.asList(SchoolDay.values()), SchoolDay::getLabel);
        LocalTime start = ConsoleForm.readTime("Hora de inicio");
        LocalTime end = ConsoleForm.readTime("Hora de fin");
        controller.addSchedule(assignment, day, start, end);
        System.out.println("\n[OK] Horario registrado: " + day.getLabel() + " " + start + " - " + end + ".");
    }

    private void showWeeklySchedule() throws BusinessException {
        Teacher teacher = findTeacher();
        List<ScheduleSlot> slots = controller.getSchedule(teacher);
        System.out.println("\nHorario semanal de " + teacher.fullName() + ":");
        if (slots.isEmpty()) {
            System.out.println("  (sin horarios registrados)");
            return;
        }
        Map<SchoolDay, List<ScheduleSlot>> byDay = slots.stream()
                .collect(Collectors.groupingBy(ScheduleSlot::day, () -> new EnumMap<>(SchoolDay.class), Collectors.toList()));
        byDay.forEach((day, daySlots) -> {
            System.out.println("\n  " + day.getLabel() + ":");
            daySlots.forEach(s -> System.out.printf("    %s - %s  %s%n", s.start(), s.end(), s.description()));
        });
    }

    // ------------------------------------------------------------------ helpers

    private Teacher findTeacher() throws BusinessException {
        return controller.getByDocument(InputValidator.readText("Documento del profesor: "));
    }

    private LocalDate readHireDate() {
        while (true) {
            LocalDate date = InputValidator.readDate("Fecha de contratación");
            try {
                controller.validateHireDate(date);
                return date;
            } catch (BusinessException e) {
                ErrorLogger.warn("Validation failed: " + e.getMessage());
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }

    private void printTeacherTable(List<Teacher> teachers) {
        if (teachers.isEmpty()) {
            System.out.println("\n[INFO] No se encontraron profesores.");
            return;
        }
        System.out.println();
        System.out.printf("%-4s %-12s %-32s %-22s %-9s%n", "ID", "Documento", "Nombre completo", "Especialización", "Estado");
        System.out.println("-".repeat(84));
        teachers.forEach(t -> System.out.printf("%-4d %-12s %-32s %-22s %-9s%n", t.id(), t.documentNumber(),
                t.fullName(), ConsoleForm.orDash(t.specialization()), t.status().getDbValue()));
        System.out.println("\nTotal: " + teachers.size() + " profesor(es).");
    }

    private void printSubjects(List<Subject> subjects) {
        System.out.println();
        System.out.printf("%-8s %-22s %-12s %s%n", "Código", "Materia", "Grado", "Horas/sem.");
        System.out.println("-".repeat(52));
        subjects.forEach(s -> System.out.printf("%-8s %-22s %-12s %d%n", s.code(), s.name(), s.gradeName(), s.weeklyHours()));
        System.out.println();
    }
}