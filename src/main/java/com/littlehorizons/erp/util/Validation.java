package com.littlehorizons.erp.util;

import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.DocumentType;

import java.util.regex.Pattern;

/** Reusable format validations shared by the controllers. */
public final class Validation {

    private static final Pattern NUMERIC_DOCUMENT = Pattern.compile("^[0-9]{6,15}$");
    private static final Pattern PASSPORT_DOCUMENT = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern PERSON_NAME = Pattern.compile("^\\p{L}[\\p{L} '.-]{0,98}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{7,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private Validation() { }

    public static void name(String value) throws BusinessException {
        if (!PERSON_NAME.matcher(value.trim()).matches()) {
            throw new BusinessException("Solo se permiten letras, espacios, apóstrofes, puntos y guiones.");
        }
    }

    public static void documentFormat(DocumentType type, String number) throws BusinessException {
        Pattern pattern = type == DocumentType.PASSPORT ? PASSPORT_DOCUMENT : NUMERIC_DOCUMENT;
        if (!pattern.matcher(number.trim()).matches()) {
            throw new BusinessException(type == DocumentType.PASSPORT
                    ? "El pasaporte debe tener entre 5 y 20 letras o números."
                    : "El documento debe tener solo números (entre 6 y 15 dígitos).");
        }
    }

    public static void phone(String phone) throws BusinessException {
        if (!PHONE.matcher(phone.trim()).matches()) {
            throw new BusinessException("El teléfono debe tener solo números (entre 7 y 15 dígitos).");
        }
    }

    public static void email(String email) throws BusinessException {
        if (!EMAIL.matcher(email.trim()).matches()) {
            throw new BusinessException("Correo electrónico inválido.");
        }
    }
}