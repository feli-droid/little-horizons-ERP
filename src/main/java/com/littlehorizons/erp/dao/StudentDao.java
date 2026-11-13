package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.Guardian;
import com.littlehorizons.erp.model.Student;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence operations for students. */
public interface StudentDao {

    Optional<Student> findByDocument(String documentNumber);

    List<Student> findAll();

    /** Searches by document number or by any part of the full name. */
    List<Student> search(String term);

    /**
     * Registers a student, links the guardian and enrolls the student in a grade, all in one transaction.
     * If the guardian has id 0 it is created as well.
     *
     * @return the generated student id
     */
    int register(Student student, Guardian guardian, int gradeId, int academicYear);

    boolean updateContact(int studentId, String address, String phone, String email);

    /** Marks the student and the active enrollment as withdrawn. */
    boolean withdraw(int studentId, LocalDate withdrawalDate);
}