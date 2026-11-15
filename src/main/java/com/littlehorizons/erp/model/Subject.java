package com.littlehorizons.erp.model;

/** A subject taught in a specific grade. */
public record Subject(int id, String code, String name, int gradeId, String gradeName,
                      int weeklyHours, boolean active) {
}