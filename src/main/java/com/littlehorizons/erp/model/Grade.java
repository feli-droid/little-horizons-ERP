package com.littlehorizons.erp.model;

/** Academic grade (Preescolar = level 0, Primero = 1, ..., Once = 11). */
public class Grade {

    private final int id;
    private final String name;
    private final int level;

    public Grade(int id, String name, int level) {
        this.id = id;
        this.name = name;
        this.level = level;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }
}