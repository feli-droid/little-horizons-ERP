package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.Club;
import com.littlehorizons.erp.model.ClubMembership;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence operations for clubs and memberships. */
public interface ClubDao {

    List<Club> findAll();

    Optional<Club> findById(int clubId);

    Optional<Club> findByName(String name);

    /** @return the generated club id */
    int insert(String name, String description, int supervisorId, int capacity, String schedule);

    Optional<ClubMembership> findMembership(int clubId, int studentId);

    /**
     * Enrolls the student if the club still has seats, checking and inserting in one transaction.
     * A student who left the club is reactivated.
     *
     * @return false when the club is full
     */
    boolean enroll(int clubId, int studentId, LocalDate joinDate);

    /** @return true if an active membership was closed */
    boolean withdraw(int clubId, int studentId, LocalDate withdrawDate, String reason);

    List<ClubMembership> findMembers(int clubId);

    List<ClubMembership> findByStudent(int studentId);
}