package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.DocumentType;
import com.littlehorizons.erp.model.Gender;
import com.littlehorizons.erp.model.Grade;
import com.littlehorizons.erp.model.Guardian;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.StudentStatus;
import com.littlehorizons.erp.util.InputValidator;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Console menu for the students module. */
public class StudentView {

    /** Validation rule applied to a text typed by the user. */
    @FunctionalInterface
    private interface FieldValidator {
        void validate(String value) throws BusinessException;
    }

    private static final int EXIT_OPTION = 0;

    private final StudentController controller;

    public StudentView(StudentController controller) {
        this.controller = controller;
    }

    public void run() {
        boolean back = false;
        while (!back) {
            printMenu();
            int option = InputValidator.readInt("Seleccione una opción: ", 0, 7);
            try {
                switch (option) {
                    case 1 -> registerStudent();
                    case 2 -> listStudents();
                    case 3 -> searchStudents();
                    case 4 -> updateContact();
                    case 5 -> withdrawStudent();
                    case 6 -> showGuardians();
                    case 7 -> showSummary();
                    default -> back = option == EXIT_OPTION;
                }
            } catch (BusinessException | DataAccessException e) {
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
        System.out.println("║   GESTIÓN DE ESTUDIANTES                     ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("1. Registrar estudiante");
        System.out.println("2. Listar estudiantes");
        System.out.println("3. Buscar estudiante (documento o nombre)");
        System.out.println("4. Actualizar datos de contacto");
        System.out.println("5. Retirar estudiante");
        System.out.println("6. Ver acudientes de un estudiante");
        System.out.println("7. Resumen de estudiantes");
        System.out.println("0. Volver al menú principal");
    }

    // ------------------------------------------------------------------ options

    private void registerStudent() throws BusinessException {
        System.out.println("\n--- Registro de estudiante ---");
        Student student = new Student();

        DocumentType type = readDocumentType("Tipo de documento del estudiante", false);
        student.setDocumentType(type);
        student.setDocumentNumber(readField("Número de documento: ", v -> controller.validateStudentDocument(type, v)));
        student.setFirstName(readField("Primer nombre: ", controller::validateName));
        student.setMiddleName(readOptionalField("Segundo nombre (opcional): ", controller::validateName));
        student.setLastName(readField("Primer apellido: ", controller::validateName));
        student.setSecondLastName(readOptionalField("Segundo apellido (opcional): ", controller::validateName));
        student.setBirthDate(readBirthDate(type));
        student.setGender(readGender());
        student.setAddress(InputValidator.readOptionalText("Dirección (opcional): "));
        student.setPhone(readOptionalField("Teléfono (opcional): ", controller::validatePhone));
        student.setEmail(readOptionalField("Correo electrónico (opcional): ", controller::validateEmail));
        String bloodType = readOptionalField("Tipo de sangre (opcional, ej. O+): ", controller::validateBloodType);
        student.setBloodType(bloodType == null ? null : bloodType.toUpperCase());
        student.setAllergies(InputValidator.readOptionalText("Alergias (opcional): "));
        student.setMedicalConditions(InputValidator.readOptionalText("Condiciones médicas (opcional): "));

        Grade grade = readGrade();
        Guardian guardian = readGuardian();

        System.out.println("\nResumen del registro:");
        System.out.println("  Estudiante: " + student.getFullName() + " (" + type.getDbValue() + " " + student.getDocumentNumber() + ")");
        System.out.println("  Grado:      " + grade.getName());
        System.out.println("  Acudiente:  " + guardian.getFullName() + " - " + guardian.getRelationship());
        if (!InputValidator.confirm("¿Confirma el registro?")) {
            System.out.println("[INFO] Registro cancelado.");
            return;
        }
        int id = controller.registerStudent(student, guardian, grade.getId());
        System.out.println("\n[OK] Estudiante registrado correctamente con ID " + id + " y matriculado en " + grade.getName() + ".");
    }

    private void listStudents() {
        List<Student> students = controller.listStudents();
        printStudentTable(students);
    }

    private void searchStudents() {
        String term = InputValidator.readText("Documento o nombre a buscar: ");
        List<Student> students = controller.searchStudents(term);
        printStudentTable(students);
    }

    private void updateContact() throws BusinessException {
        Student student = findStudentByTypedDocument();
        printStudentDetail(student);
        controller.ensureActive(student);
        System.out.println("\nPresione Enter sin escribir para conservar el valor actual.");
        String address = InputValidator.readOptionalText("Nueva dirección: ");
        String phone = readOptionalField("Nuevo teléfono: ", controller::validatePhone);
        String email = readOptionalField("Nuevo correo electrónico: ", controller::validateEmail);
        controller.updateContact(student,
                address != null ? address : student.getAddress(),
                phone != null ? phone : student.getPhone(),
                email != null ? email : student.getEmail());
        System.out.println("\n[OK] Datos de contacto actualizados.");
    }

    private void withdrawStudent() throws BusinessException {
        Student student = findStudentByTypedDocument();
        printStudentDetail(student);
        controller.ensureActive(student);
        if (!InputValidator.confirm("¿Confirma el retiro de " + student.getFullName() + "?")) {
            System.out.println("[INFO] Operación cancelada.");
            return;
        }
        controller.withdrawStudent(student);
        System.out.println("\n[OK] El estudiante fue retirado.");
    }

    private void showGuardians() throws BusinessException {
        Student student = findStudentByTypedDocument();
        List<Guardian> guardians = controller.getGuardians(student);
        System.out.println("\nAcudientes de " + student.getFullName() + ":");
        if (guardians.isEmpty()) {
            System.out.println("  (sin acudientes registrados)");
            return;
        }
        guardians.forEach(g -> System.out.printf("  - %s | %s %s | %s | Tel: %s | %s%n",
                g.getFullName(), g.getDocumentType().getDbValue(), g.getDocumentNumber(),
                g.getRelationship(), g.getPrimaryPhone(), orDash(g.getEmail())));
    }

    private void showSummary() {
        System.out.println("\n--- Estudiantes por estado ---");
        for (Map.Entry<StudentStatus, Long> entry : controller.countByStatus().entrySet()) {
            System.out.printf("  %-10s %d%n", entry.getKey().getDbValue(), entry.getValue());
        }
        System.out.println("\n--- Estudiantes activos por grado ---");
        controller.countActiveByGrade().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.printf("  %-12s %d%n", e.getKey(), e.getValue()));
    }

    // ------------------------------------------------------------------ helpers

    private Student findStudentByTypedDocument() throws BusinessException {
        String document = InputValidator.readText("Documento del estudiante: ");
        return controller.getStudentByDocument(document);
    }

    private Guardian readGuardian() {
        System.out.println("\n--- Datos del acudiente ---");
        DocumentType type = readDocumentType("Tipo de documento del acudiente", true);
        String number = readField("Número de documento del acudiente: ", v -> controller.validateDocumentFormat(type, v));
        Optional<Guardian> existing = controller.findGuardianByDocument(number);
        if (existing.isPresent()) {
            Guardian guardian = existing.get();
            System.out.println("[INFO] Acudiente ya registrado: " + guardian.getFullName() + " (" + guardian.getRelationship() + ")");
            if (InputValidator.confirm("¿Desea asociar a este acudiente con el estudiante?")) {
                return guardian;
            }
            System.out.println("[INFO] Debe indicar un acudiente distinto.");
            return readGuardian();
        }
        Guardian guardian = new Guardian();
        guardian.setDocumentType(type);
        guardian.setDocumentNumber(number);
        guardian.setFirstName(readField("Primer nombre: ", controller::validateName));
        guardian.setMiddleName(readOptionalField("Segundo nombre (opcional): ", controller::validateName));
        guardian.setLastName(readField("Primer apellido: ", controller::validateName));
        guardian.setSecondLastName(readOptionalField("Segundo apellido (opcional): ", controller::validateName));
        guardian.setRelationship(InputValidator.readText("Parentesco (Madre, Padre, Tutor Legal...): "));
        guardian.setPrimaryPhone(readField("Teléfono principal: ", controller::validatePhone));
        guardian.setEmail(readOptionalField("Correo electrónico (opcional): ", controller::validateEmail));
        guardian.setAddress(InputValidator.readOptionalText("Dirección (opcional): "));
        guardian.setOccupation(InputValidator.readOptionalText("Ocupación (opcional): "));
        return guardian;
    }

    private DocumentType readDocumentType(String title, boolean forAdult) {
        DocumentType[] options = forAdult
                ? new DocumentType[]{DocumentType.CC, DocumentType.CE, DocumentType.PASSPORT}
                : DocumentType.values();
        System.out.println(title + ":");
        for (int i = 0; i < options.length; i++) {
            System.out.printf("  %d. %s%n", i + 1, options[i].getLabel());
        }
        return options[InputValidator.readInt("Opción: ", 1, options.length) - 1];
    }

    private Gender readGender() {
        Gender[] options = Gender.values();
        System.out.println("Género:");
        for (int i = 0; i < options.length; i++) {
            System.out.printf("  %d. %s%n", i + 1, options[i].getLabel());
        }
        return options[InputValidator.readInt("Opción: ", 1, options.length) - 1];
    }

    private Grade readGrade() {
        List<Grade> grades = controller.getGrades();
        System.out.println("\nGrados disponibles:");
        for (int i = 0; i < grades.size(); i++) {
            System.out.printf("  %2d. %s%n", i + 1, grades.get(i).getName());
        }
        return grades.get(InputValidator.readInt("Grado de matrícula: ", 1, grades.size()) - 1);
    }

    private LocalDate readBirthDate(DocumentType type) {
        while (true) {
            LocalDate date = InputValidator.readDate("Fecha de nacimiento");
            try {
                controller.validateBirthDate(type, date);
                return date;
            } catch (BusinessException e) {
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }

    private String readField(String prompt, FieldValidator validator) {
        while (true) {
            String value = InputValidator.readText(prompt);
            try {
                validator.validate(value);
                return value;
            } catch (BusinessException e) {
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }

    private String readOptionalField(String prompt, FieldValidator validator) {
        while (true) {
            String value = InputValidator.readOptionalText(prompt);
            if (value == null) {
                return null;
            }
            try {
                validator.validate(value);
                return value;
            } catch (BusinessException e) {
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }

    private void printStudentTable(List<Student> students) {
        if (students.isEmpty()) {
            System.out.println("\n[INFO] No se encontraron estudiantes.");
            return;
        }
        System.out.println();
        System.out.printf("%-4s %-5s %-12s %-34s %-12s %-10s%n", "ID", "Tipo", "Documento", "Nombre completo", "Grado", "Estado");
        System.out.println("-".repeat(82));
        students.forEach(s -> System.out.printf("%-4d %-5s %-12s %-34s %-12s %-10s%n",
                s.getId(), s.getDocumentType().getDbValue(), s.getDocumentNumber(), s.getFullName(),
                orDash(s.getCurrentGrade()), s.getStatus().getDbValue()));
        System.out.println("\nTotal: " + students.size() + " estudiante(s).");
    }

    private void printStudentDetail(Student s) {
        System.out.println("\n  Nombre:     " + s.getFullName());
        System.out.println("  Documento:  " + s.getDocumentType().getDbValue() + " " + s.getDocumentNumber());
        System.out.println("  Grado:      " + orDash(s.getCurrentGrade()) + "  |  Estado: " + s.getStatus().getDbValue());
        System.out.println("  Dirección:  " + orDash(s.getAddress()));
        System.out.println("  Teléfono:   " + orDash(s.getPhone()));
        System.out.println("  Correo:     " + orDash(s.getEmail()));
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}