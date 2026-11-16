package com.littlehorizons.erp.model;

import java.time.LocalDate;

/** Membership of a student in a club. */
public record ClubMembership(int id, int clubId, String clubName, int studentId, String studentName,
                             String studentDocument, LocalDate joinDate, MembershipStatus status,
                             LocalDate withdrawDate, String withdrawReason) {
}