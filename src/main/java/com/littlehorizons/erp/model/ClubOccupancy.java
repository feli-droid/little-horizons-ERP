package com.littlehorizons.erp.model;

/** Capacity versus active members of a club. */
public record ClubOccupancy(String clubName, String supervisorName, int capacity, int activeMembers) {

    public int freeSpots() {
        return capacity - activeMembers;
    }

    public double occupancyPercent() {
        return capacity == 0 ? 0.0 : activeMembers * 100.0 / capacity;
    }
}