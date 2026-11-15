package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.model.TeacherStatus;

import java.util.List;
import java.util.Optional;

/** Persistence operations for teachers. */
public interface TeacherDao {

    Optional<Teacher> findByDocument(String documentNumber);

    Optional<Teacher> findByEmail(String email);

    List<Teacher> findAll();

    /** Searches by document number or by any part of the full name. */
    List<Teacher> search(String term);

    /** @return the generated teacher id */
    int insert(Teacher teacher);

    boolean updateContact(int teacherId, String email, String phone);

    boolean updateStatus(int teacherId, TeacherStatus status);
}