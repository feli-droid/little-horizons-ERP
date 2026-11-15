package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.Assignment;
import com.littlehorizons.erp.model.SchoolDay;
import com.littlehorizons.erp.model.ScheduleSlot;
import com.littlehorizons.erp.model.Subject;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Persistence operations for subjects, teaching assignments and class schedules. */
public interface AssignmentDao {

    List<Subject> findActiveSubjects();

    Optional<Subject> findSubjectByCode(String code);

    /** Finds who teaches a subject in a given year and section, if anyone. */
    Optional<Assignment> findAssignment(int subjectId, int year, String section);

    List<Assignment> findByTeacher(int teacherId, int year);

    /** @return the generated assignment id */
    int insertAssignment(int teacherId, int subjectId, int year, String section);

    int insertSchedule(int assignmentId, SchoolDay day, LocalTime start, LocalTime end);

    List<ScheduleSlot> findSchedulesByAssignment(int assignmentId);

    List<ScheduleSlot> findScheduleOfTeacher(int teacherId, int year);

    /**
     * Finds existing slots that overlap the given one, either because the same teacher is busy
     * or because the same grade and section already has a class at that time.
     */
    List<ScheduleSlot> findConflicts(int assignmentId, SchoolDay day, LocalTime start, LocalTime end);
}