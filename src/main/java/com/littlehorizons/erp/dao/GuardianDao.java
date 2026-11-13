package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.Guardian;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Persistence operations for guardians. */
public interface GuardianDao {

    Optional<Guardian> findByDocument(String documentNumber);

    List<Guardian> findByStudentId(int studentId);

    /** Inserts a guardian using an existing connection so it can join a transaction. */
    int insert(Connection connection, Guardian guardian) throws SQLException;

    /** Links a guardian to a student using an existing connection. */
    void linkToStudent(Connection connection, int studentId, int guardianId, boolean primary) throws SQLException;
}