package com.littlehorizons.erp.model;

/** An extracurricular club with its capacity and current number of active members. */
public record Club(int id, String name, String description, int supervisorId, String supervisorName,
                   int capacity, String schedule, boolean active, int activeMembers) {

    public int availableSeats() {
        return capacity - activeMembers;
    }
}