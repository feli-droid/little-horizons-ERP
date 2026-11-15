package com.littlehorizons.erp.model;

import java.time.LocalDate;
import java.util.StringJoiner;

/** A teacher of the school (immutable). */
public record Teacher(int id, DocumentType documentType, String documentNumber, String firstName,
                      String middleName, String lastName, String secondLastName, String email,
                      String phone, String specialization, LocalDate hireDate, TeacherStatus status) {

    public String fullName() {
        StringJoiner joiner = new StringJoiner(" ");
        for (String part : new String[]{firstName, middleName, lastName, secondLastName}) {
            if (part != null && !part.isBlank()) {
                joiner.add(part);
            }
        }
        return joiner.toString();
    }
}