package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.EventDao;
import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.dao.TeacherDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.EventParticipant;
import com.littlehorizons.erp.model.EventPosition;
import com.littlehorizons.erp.model.EventType;
import com.littlehorizons.erp.model.ParticipationRole;
import com.littlehorizons.erp.model.SchoolEvent;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.StudentParticipation;
import com.littlehorizons.erp.model.StudentStatus;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.model.TeacherStatus;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Business rules for events, participants and competition positions. */
public class EventController {

    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 150;
    private static final LocalDate EARLIEST_EVENT_DATE = LocalDate.of(2000, 1, 1);

    private final EventDao eventDao;
    private final TeacherDao teacherDao;
    private final StudentDao studentDao;

    public EventController(EventDao eventDao, TeacherDao teacherDao, StudentDao studentDao) {
        this.eventDao = eventDao;
        this.teacherDao = teacherDao;
        this.studentDao = studentDao;
    }

    public List<SchoolEvent> listEvents() {
        return eventDao.findAll();
    }

    public Student getStudentByDocument(String documentNumber) throws BusinessException {
        return studentDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un estudiante con el documento " + documentNumber + "."));
    }

    public Teacher getOrganizer(String documentNumber) throws BusinessException {
        Teacher teacher = teacherDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un profesor con el documento " + documentNumber + "."));
        if (teacher.status() != TeacherStatus.ACTIVE) {
            throw new BusinessException("El profesor debe estar en estado Activo para organizar un evento.");
        }
        return teacher;
    }

    public void validateEventName(String name) throws BusinessException {
        int length = name.trim().length();
        if (length < MIN_NAME_LENGTH || length > MAX_NAME_LENGTH) {
            throw new BusinessException("El nombre debe tener entre " + MIN_NAME_LENGTH + " y " + MAX_NAME_LENGTH + " caracteres.");
        }
        if (name.trim().chars().noneMatch(Character::isLetter)) {
            throw new BusinessException("El nombre debe contener letras; no puede ser solo números o símbolos.");
        }
    }

    public void validateEventDate(LocalDate date) throws BusinessException {
        if (date.isBefore(EARLIEST_EVENT_DATE)) {
            throw new BusinessException("La fecha del evento no es válida.");
        }
    }

    public int registerEvent(String name, String description, EventType type, LocalDate date, String place,
                             boolean competition, Teacher organizer) throws BusinessException {
        validateEventName(name);
        validateEventDate(date);
        return eventDao.insert(name.trim(), description, type, date, place, competition, organizer.id());
    }

    /** Registers who took part in an event; only events that already happened (or happen today) accept participants. */
    public void addParticipant(SchoolEvent event, Student student, ParticipationRole role) throws BusinessException {
        if (!event.active()) {
            throw new BusinessException("El evento " + event.name() + " no está activo.");
        }
        if (event.date().isAfter(LocalDate.now())) {
            throw new BusinessException("El evento será el " + event.date() + ": solo se registran participantes de eventos ya realizados.");
        }
        if (student.getStatus() != StudentStatus.ACTIVE) {
            throw new BusinessException("Solo los estudiantes en estado Activo pueden participar en eventos.");
        }
        if (role == ParticipationRole.COMPETITOR && !event.competition()) {
            throw new BusinessException("El evento no tiene competencia: no se puede registrar a un competidor.");
        }
        if (eventDao.findParticipant(event.id(), student.getId()).isPresent()) {
            throw new BusinessException(student.getFullName() + " ya está registrado en " + event.name() + ".");
        }
        eventDao.addParticipant(event.id(), student.getId(), role);
    }

    /** Sets the position of a competitor; each podium place (1st, 2nd, 3rd) goes to only one participant. */
    public void setPosition(SchoolEvent event, EventParticipant participant, EventPosition position)
            throws BusinessException {
        if (!event.competition()) {
            throw new BusinessException("El evento no tiene competencia: no admite posiciones.");
        }
        if (participant.role() != ParticipationRole.COMPETITOR) {
            throw new BusinessException("Solo los participantes con rol Competidor pueden recibir una posición.");
        }
        if (participant.position() == position) {
            throw new BusinessException(participant.studentName() + " ya tiene la posición " + position.getDbValue() + ".");
        }
        if (position.isPodium() && eventDao.isPositionTaken(event.id(), position)) {
            throw new BusinessException("La posición " + position.getDbValue() + " ya fue asignada a otro competidor.");
        }
        eventDao.setPosition(participant.id(), position);
    }

    public List<EventParticipant> getParticipants(SchoolEvent event) {
        return eventDao.findParticipants(event.id());
    }

    public List<StudentParticipation> getParticipations(Student student) {
        return eventDao.findByStudent(student.getId());
    }

    /** Counts the participations of a student by kind of event (uses streams to group and count). */
    public Map<EventType, Long> countByType(List<StudentParticipation> participations) {
        return participations.stream().collect(Collectors.groupingBy(StudentParticipation::type,
                () -> new EnumMap<>(EventType.class), Collectors.counting()));
    }
}