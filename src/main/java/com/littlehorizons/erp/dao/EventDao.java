package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.EventParticipant;
import com.littlehorizons.erp.model.EventPosition;
import com.littlehorizons.erp.model.EventType;
import com.littlehorizons.erp.model.ParticipationRole;
import com.littlehorizons.erp.model.SchoolEvent;
import com.littlehorizons.erp.model.StudentParticipation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence operations for events and their participants. */
public interface EventDao {

    List<SchoolEvent> findAll();

    Optional<SchoolEvent> findById(int eventId);

    /** @return the generated event id */
    int insert(String name, String description, EventType type, LocalDate date, String place,
               boolean competition, int organizerId);

    List<EventParticipant> findParticipants(int eventId);

    Optional<EventParticipant> findParticipant(int eventId, int studentId);

    void addParticipant(int eventId, int studentId, ParticipationRole role);

    void setPosition(int participationId, EventPosition position);

    /** True when another participant of the event already has the given position. */
    boolean isPositionTaken(int eventId, EventPosition position);

    List<StudentParticipation> findByStudent(int studentId);
}