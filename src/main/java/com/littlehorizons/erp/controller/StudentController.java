package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.GradeDao;
import com.littlehorizons.erp.dao.GuardianDao;
import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.DocumentType;
import com.littlehorizons.erp.model.Grade;
import com.littlehorizons.erp.model.Guardian;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.StudentStatus;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Business rules for students and their guardians. */
public class StudentController {

    private static final Pattern NUMERIC_DOCUMENT = Pattern.compile("^[0-9]{6,15}$");
    private static final Pattern PASSPORT_DOCUMENT = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern PERSON_NAME = Pattern.compile("^\\p{L}[\\p{L} '.-]{0,98}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{7,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern BLOOD_TYPE = Pattern.compile("^(A|B|AB|O)[+-]$");

    private static final int MIN_AGE = 3;
    private static final int MAX_AGE = 20;
    private static final int AGE_TOLERANCE_YEARS = 3;
    private static final int BASE_AGE_PREESCHOOL = 5;

    private final StudentDao studentDao;
    private final GuardianDao guardianDao;
    private final GradeDao gradeDao;

    public StudentController(StudentDao studentDao, GuardianDao guardianDao, GradeDao gradeDao) {
        this.studentDao = studentDao;
        this.guardianDao = guardianDao;
        this.gradeDao = gradeDao;
    }

    // ------------------------------------------------------------------ queries

    public List<Grade> getGrades() {
        return gradeDao.findAll();
    }

    public List<Student> listStudents() {
        return studentDao.findAll();
    }

    public List<Student> searchStudents(String term) {
        return studentDao.search(term.trim());
    }

    public Student getStudentByDocument(String documentNumber) throws BusinessException {
        return studentDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un estudiante con el documento " + documentNumber + "."));
    }

    public Optional<Guardian> findGuardianByDocument(String documentNumber) {
        return guardianDao.findByDocument(documentNumber.trim());
    }

    public List<Guardian> getGuardians(Student student) {
        return guardianDao.findByStudentId(student.getId());
    }

    /** Number of students per status (uses streams to group and count). */
    public Map<StudentStatus, Long> countByStatus() {
        return studentDao.findAll().stream()
                .collect(Collectors.groupingBy(Student::getStatus,
                        () -> new EnumMap<>(StudentStatus.class), Collectors.counting()));
    }

    /** Number of active students per current grade. */
    public Map<String, Long> countActiveByGrade() {
        return studentDao.findAll().stream()
                .filter(s -> s.getStatus() == StudentStatus.ACTIVE && s.getCurrentGrade() != null)
                .collect(Collectors.groupingBy(Student::getCurrentGrade, Collectors.counting()));
    }

    // -------------------------------------------------------------- field validations

    public void validateName(String value) throws BusinessException {
        if (!PERSON_NAME.matcher(value.trim()).matches()) {
            throw new BusinessException("Solo se permiten letras, espacios, apóstrofes, puntos y guiones.");
        }
    }

    /** Validates the document format and that no other student already uses it. */
    public void validateStudentDocument(DocumentType type, String number) throws BusinessException {
        validateDocumentFormat(type, number);
        if (studentDao.findByDocument(number.trim()).isPresent()) {
            throw new BusinessException("Ya existe un estudiante con el documento " + number + ".");
        }
    }

    public void validateDocumentFormat(DocumentType type, String number) throws BusinessException {
        Pattern pattern = type == DocumentType.PASSPORT ? PASSPORT_DOCUMENT : NUMERIC_DOCUMENT;
        if (!pattern.matcher(number.trim()).matches()) {
            throw new BusinessException(type == DocumentType.PASSPORT
                    ? "El pasaporte debe tener entre 5 y 20 letras o números."
                    : "El documento debe tener solo números (entre 6 y 15 dígitos).");
        }
    }

    public void validatePhone(String phone) throws BusinessException {
        if (!PHONE.matcher(phone.trim()).matches()) {
            throw new BusinessException("El teléfono debe tener solo números (entre 7 y 15 dígitos).");
        }
    }

    public void validateEmail(String email) throws BusinessException {
        if (!EMAIL.matcher(email.trim()).matches()) {
            throw new BusinessException("Correo electrónico inválido.");
        }
    }

    public void validateBloodType(String bloodType) throws BusinessException {
        if (!BLOOD_TYPE.matcher(bloodType.trim().toUpperCase()).matches()) {
            throw new BusinessException("Tipo de sangre inválido. Ejemplos: O+, A-, AB+.");
        }
    }

    /** Checks the birth date is plausible for a student and coherent with the document type. */
    public void validateBirthDate(DocumentType type, LocalDate birthDate) throws BusinessException {
        LocalDate today = LocalDate.now();
        if (!birthDate.isBefore(today)) {
            throw new BusinessException("La fecha de nacimiento debe ser anterior a hoy.");
        }
        int age = today.getYear() - birthDate.getYear()
                - (birthDate.withYear(today.getYear()).isAfter(today) ? 1 : 0);
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new BusinessException("La edad del estudiante debe estar entre " + MIN_AGE + " y " + MAX_AGE + " años.");
        }
        if (type == DocumentType.RC && age > 7) {
            throw new BusinessException("El Registro Civil solo aplica hasta los 7 años; use Tarjeta de Identidad.");
        }
        if (type == DocumentType.TI && age < 7) {
            throw new BusinessException("La Tarjeta de Identidad aplica desde los 7 años; use Registro Civil.");
        }
        if (type == DocumentType.CC && age < 18) {
            throw new BusinessException("La Cédula de Ciudadanía aplica desde los 18 años.");
        }
    }

    // ------------------------------------------------------------------- operations

    /**
     * Registers a student with their guardian and enrolls them in a grade.
     * Every rule is checked again here so the data is valid even if the view skipped a check.
     */
    public int registerStudent(Student student, Guardian guardian, int gradeId) throws BusinessException {
        Grade grade = gradeDao.findAll().stream()
                .filter(g -> g.getId() == gradeId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("El grado seleccionado no existe."));

        validateName(student.getFirstName());
        validateName(student.getLastName());
        validateStudentDocument(student.getDocumentType(), student.getDocumentNumber());
        validateBirthDate(student.getDocumentType(), student.getBirthDate());
        validateGradeForAge(student, grade);
        if (guardian == null) {
            throw new BusinessException("Todo estudiante debe tener al menos un acudiente.");
        }
        if (guardian.getId() == 0) {
            validateName(guardian.getFirstName());
            validateName(guardian.getLastName());
            validateDocumentFormat(guardian.getDocumentType(), guardian.getDocumentNumber());
            validatePhone(guardian.getPrimaryPhone());
        }
        student.setAdmissionDate(LocalDate.now());
        return studentDao.register(student, guardian, gradeId, LocalDate.now().getYear());
    }

    /** Fails when the student is not active, so the view can warn before asking for any data. */
    public void ensureActive(Student student) throws BusinessException {
        if (student.getStatus() != StudentStatus.ACTIVE) {
            throw new BusinessException("El estudiante está en estado " + student.getStatus().getDbValue()
                    + " y no admite esta operación.");
        }
    }

    public void updateContact(Student student, String address, String phone, String email) throws BusinessException {
        ensureActive(student);
        if (phone != null) {
            validatePhone(phone);
        }
        if (email != null) {
            validateEmail(email);
        }
        if (!studentDao.updateContact(student.getId(), address, phone, email)) {
            throw new BusinessException("No se encontró el estudiante a actualizar.");
        }
    }

    public void withdrawStudent(Student student) throws BusinessException {
        ensureActive(student);
        if (!studentDao.withdraw(student.getId(), LocalDate.now())) {
            throw new BusinessException("No se pudo retirar al estudiante.");
        }
    }

    private void validateGradeForAge(Student student, Grade grade) throws BusinessException {
        int age = student.getAgeAt(LocalDate.of(LocalDate.now().getYear(), 12, 31));
        int expectedAge = BASE_AGE_PREESCHOOL + grade.getLevel();
        if (Math.abs(age - expectedAge) > AGE_TOLERANCE_YEARS) {
            throw new BusinessException("La edad del estudiante (" + age + " años) no corresponde al grado "
                    + grade.getName() + ".");
        }
    }
}