package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.AssignmentDao;
import com.littlehorizons.erp.dao.TeacherDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.Assignment;
import com.littlehorizons.erp.model.DocumentType;
import com.littlehorizons.erp.model.ScheduleSlot;
import com.littlehorizons.erp.model.SchoolDay;
import com.littlehorizons.erp.model.Subject;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.model.TeacherStatus;
import com.littlehorizons.erp.util.Validation;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Business rules for teachers, teaching assignments and class schedules. */
public class TeacherController {

    private static final Pattern SECTION = Pattern.compile("^[A-Za-z]{1,3}$");
    private static final LocalTime SCHOOL_START = LocalTime.of(6, 0);
    private static final LocalTime SCHOOL_END = LocalTime.of(17, 0);
    private static final LocalDate EARLIEST_HIRE_DATE = LocalDate.of(1970, 1, 1);

    private final TeacherDao teacherDao;
    private final AssignmentDao assignmentDao;

    public TeacherController(TeacherDao teacherDao, AssignmentDao assignmentDao) {
        this.teacherDao = teacherDao;
        this.assignmentDao = assignmentDao;
    }

    public static int currentYear() {
        return LocalDate.now().getYear();
    }

    // ------------------------------------------------------------------ queries

    public List<Teacher> listTeachers() {
        return teacherDao.findAll();
    }

    public List<Teacher> searchTeachers(String term) {
        return teacherDao.search(term.trim());
    }

    public Teacher getByDocument(String documentNumber) throws BusinessException {
        return teacherDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un profesor con el documento " + documentNumber + "."));
    }

    public List<Subject> listSubjects() {
        return assignmentDao.findActiveSubjects();
    }

    public List<Assignment> getAssignments(Teacher teacher) {
        return assignmentDao.findByTeacher(teacher.id(), currentYear());
    }

    public List<ScheduleSlot> getSchedule(Teacher teacher) {
        return assignmentDao.findScheduleOfTeacher(teacher.id(), currentYear());
    }

    public List<ScheduleSlot> getScheduleOf(Assignment assignment) {
        return assignmentDao.findSchedulesByAssignment(assignment.id());
    }

    // ------------------------------------------------------------- field validations

    public void validateName(String value) throws BusinessException {
        Validation.name(value);
    }

    public void validatePhone(String value) throws BusinessException {
        Validation.phone(value);
    }

    public void validateTeacherDocument(DocumentType type, String number) throws BusinessException {
        Validation.documentFormat(type, number);
        if (teacherDao.findByDocument(number.trim()).isPresent()) {
            throw new BusinessException("Ya existe un profesor con el documento " + number + ".");
        }
    }

    /** Validates the e-mail format and that no other teacher uses it. */
    public void validateNewEmail(String email) throws BusinessException {
        Validation.email(email);
        if (teacherDao.findByEmail(email.trim()).isPresent()) {
            throw new BusinessException("Ya existe un profesor con el correo " + email + ".");
        }
    }

    /** Same as {@link #validateNewEmail} but lets the teacher keep their own e-mail. */
    public void validateEmailForUpdate(Teacher teacher, String email) throws BusinessException {
        Validation.email(email);
        Optional<Teacher> owner = teacherDao.findByEmail(email.trim());
        if (owner.isPresent() && owner.get().id() != teacher.id()) {
            throw new BusinessException("Ya existe otro profesor con el correo " + email + ".");
        }
    }

    public void validateHireDate(LocalDate hireDate) throws BusinessException {
        if (hireDate.isAfter(LocalDate.now())) {
            throw new BusinessException("La fecha de contratación no puede ser futura.");
        }
        if (hireDate.isBefore(EARLIEST_HIRE_DATE)) {
            throw new BusinessException("La fecha de contratación no es válida.");
        }
    }

    // ------------------------------------------------------------------- operations

    /** Registers a teacher; the rules are checked again here in case the view skipped one. */
    public int registerTeacher(Teacher teacher) throws BusinessException {
        validateName(teacher.firstName());
        validateName(teacher.lastName());
        validateTeacherDocument(teacher.documentType(), teacher.documentNumber());
        validateNewEmail(teacher.email());
        validateHireDate(teacher.hireDate());
        return teacherDao.insert(teacher);
    }

    public void changeStatus(Teacher teacher, TeacherStatus newStatus) throws BusinessException {
        if (teacher.status() == newStatus) {
            throw new BusinessException("El profesor ya está en estado " + newStatus.getDbValue() + ".");
        }
        if (!teacherDao.updateStatus(teacher.id(), newStatus)) {
            throw new BusinessException("No se pudo cambiar el estado del profesor.");
        }
    }

    /** Updates e-mail and phone; a null value keeps the current one. */
    public void updateContact(Teacher teacher, String email, String phone) throws BusinessException {
        String newEmail = email != null ? email.trim() : teacher.email();
        String newPhone = phone != null ? phone.trim() : teacher.phone();
        if (email != null) {
            validateEmailForUpdate(teacher, newEmail);
        }
        if (phone != null) {
            Validation.phone(newPhone);
        }
        if (!teacherDao.updateContact(teacher.id(), newEmail, newPhone)) {
            throw new BusinessException("No se encontró el profesor a actualizar.");
        }
    }

    public Assignment assignSubject(Teacher teacher, String subjectCode, String section) throws BusinessException {
        if (teacher.status() != TeacherStatus.ACTIVE) {
            throw new BusinessException("Solo se pueden asignar materias a profesores en estado Activo.");
        }
        if (!SECTION.matcher(section.trim()).matches()) {
            throw new BusinessException("La sección debe tener de 1 a 3 letras (ej. A, B).");
        }
        String cleanSection = section.trim().toUpperCase();
        Subject subject = assignmentDao.findSubjectByCode(subjectCode.trim().toUpperCase())
                .filter(Subject::active)
                .orElseThrow(() -> new BusinessException("No existe una materia activa con el código " + subjectCode + "."));
        int year = currentYear();
        Optional<Assignment> existing = assignmentDao.findAssignment(subject.id(), year, cleanSection);
        if (existing.isPresent()) {
            throw new BusinessException("La materia " + subject.name() + " (" + subject.gradeName() + ", sección "
                    + cleanSection + ") ya está asignada a " + existing.get().teacherName() + ".");
        }
        int id = assignmentDao.insertAssignment(teacher.id(), subject.id(), year, cleanSection);
        return new Assignment(id, teacher.id(), teacher.fullName(), subject, year, cleanSection);
    }

    /**
     * Adds a weekly class slot. It must be inside school hours, must not exceed the weekly hours of the
     * subject and must not overlap another class of the same teacher or of the same grade and section.
     */
    public void addSchedule(Assignment assignment, SchoolDay day, LocalTime start, LocalTime end)
            throws BusinessException {
        if (!end.isAfter(start)) {
            throw new BusinessException("La hora de fin debe ser posterior a la hora de inicio.");
        }
        if (start.isBefore(SCHOOL_START) || end.isAfter(SCHOOL_END)) {
            throw new BusinessException("El horario escolar va de " + SCHOOL_START + " a " + SCHOOL_END + ".");
        }
        long scheduledMinutes = getScheduleOf(assignment).stream().mapToLong(ScheduleSlot::minutes).sum();
        long newMinutes = Duration.between(start, end).toMinutes();
        long limitMinutes = assignment.subject().weeklyHours() * 60L;
        if (scheduledMinutes + newMinutes > limitMinutes) {
            throw new BusinessException("Se superarían las " + assignment.subject().weeklyHours()
                    + " horas semanales de la materia (ya hay " + formatHours(scheduledMinutes) + " programadas).");
        }
        List<ScheduleSlot> conflicts = assignmentDao.findConflicts(assignment.id(), day, start, end);
        if (!conflicts.isEmpty()) {
            ScheduleSlot first = conflicts.get(0);
            throw new BusinessException("Choque de horario con " + first.description() + " el "
                    + first.day().getLabel() + " de " + first.start() + " a " + first.end() + ".");
        }
        assignmentDao.insertSchedule(assignment.id(), day, start, end);
    }

    public static String formatHours(long minutes) {
        return String.format("%d h %02d min", minutes / 60, minutes % 60);
    }
}