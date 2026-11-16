package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.ClubDao;
import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.dao.TeacherDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.Club;
import com.littlehorizons.erp.model.ClubMembership;
import com.littlehorizons.erp.model.MembershipStatus;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.StudentStatus;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.model.TeacherStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Business rules for clubs and club memberships. */
public class ClubController {

    public static final int MAX_CAPACITY = 200;

    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 100;

    private final ClubDao clubDao;
    private final TeacherDao teacherDao;
    private final StudentDao studentDao;

    public ClubController(ClubDao clubDao, TeacherDao teacherDao, StudentDao studentDao) {
        this.clubDao = clubDao;
        this.teacherDao = teacherDao;
        this.studentDao = studentDao;
    }

    public List<Club> listClubs() {
        return clubDao.findAll();
    }

    public Student getStudentByDocument(String documentNumber) throws BusinessException {
        return studentDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un estudiante con el documento " + documentNumber + "."));
    }

    public Teacher getSupervisor(String documentNumber) throws BusinessException {
        Teacher teacher = teacherDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un profesor con el documento " + documentNumber + "."));
        if (teacher.status() != TeacherStatus.ACTIVE) {
            throw new BusinessException("El profesor debe estar en estado Activo para encargarse de un club.");
        }
        return teacher;
    }

    public void validateClubName(String name) throws BusinessException {
        String clean = name.trim();
        if (clean.length() < MIN_NAME_LENGTH || clean.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("El nombre debe tener entre " + MIN_NAME_LENGTH + " y " + MAX_NAME_LENGTH + " caracteres.");
        }
        if (clubDao.findByName(clean).isPresent()) {
            throw new BusinessException("Ya existe un club llamado " + clean + ".");
        }
    }

    public int registerClub(String name, String description, Teacher supervisor, int capacity, String schedule)
            throws BusinessException {
        validateClubName(name);
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new BusinessException("El cupo debe estar entre 1 y " + MAX_CAPACITY + ".");
        }
        return clubDao.insert(name.trim(), description, supervisor.id(), capacity, schedule);
    }

    /** Enrolls the student if the rules allow it; the capacity is checked inside the database transaction. */
    public void enrollStudent(Club club, Student student) throws BusinessException {
        if (!club.active()) {
            throw new BusinessException("El club " + club.name() + " no está activo.");
        }
        if (student.getStatus() != StudentStatus.ACTIVE) {
            throw new BusinessException("Solo los estudiantes en estado Activo pueden inscribirse en un club.");
        }
        Optional<ClubMembership> current = clubDao.findMembership(club.id(), student.getId());
        if (current.isPresent() && current.get().status() == MembershipStatus.ACTIVE) {
            throw new BusinessException(student.getFullName() + " ya es miembro activo de " + club.name() + ".");
        }
        if (!clubDao.enroll(club.id(), student.getId(), LocalDate.now())) {
            throw new BusinessException("El club " + club.name() + " alcanzó su cupo máximo de " + club.capacity() + " estudiantes.");
        }
    }

    public void withdrawStudent(Club club, Student student, String reason) throws BusinessException {
        Optional<ClubMembership> current = clubDao.findMembership(club.id(), student.getId());
        if (current.isEmpty() || current.get().status() != MembershipStatus.ACTIVE) {
            throw new BusinessException(student.getFullName() + " no es miembro activo de " + club.name() + ".");
        }
        if (!clubDao.withdraw(club.id(), student.getId(), LocalDate.now(), reason)) {
            throw new BusinessException("No se pudo retirar al estudiante del club.");
        }
    }

    public List<ClubMembership> getMembers(Club club) {
        return clubDao.findMembers(club.id());
    }

    public List<ClubMembership> getMemberships(Student student) {
        return clubDao.findByStudent(student.getId());
    }
}