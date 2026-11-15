package com.littlehorizons.erp.view;

import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.InputValidator;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Function;

/** Console helpers shared by the views: validated fields, numbered choices and times. */
public final class ConsoleForm {

    /** Validation rule applied to a text typed by the user. */
    @FunctionalInterface
    public interface Validator {
        void validate(String value) throws BusinessException;
    }

    private ConsoleForm() { }

    /** Reads a required text and asks again until the validator accepts it. */
    public static String readField(String prompt, Validator validator) {
        while (true) {
            String value = InputValidator.readText(prompt);
            try {
                validator.validate(value);
                return value;
            } catch (BusinessException e) {
                reject(e.getMessage());
            }
        }
    }

    /** Reads an optional text (Enter = null) and asks again while the validator rejects it. */
    public static String readOptionalField(String prompt, Validator validator) {
        while (true) {
            String value = InputValidator.readOptionalText(prompt);
            if (value == null) {
                return null;
            }
            try {
                validator.validate(value);
                return value;
            } catch (BusinessException e) {
                reject(e.getMessage());
            }
        }
    }

    /** Shows a numbered list and returns the option the user picks. */
    public static <T> T choose(String title, List<T> options, Function<T, String> label) {
        System.out.println(title + ":");
        for (int i = 0; i < options.size(); i++) {
            System.out.printf("  %2d. %s%n", i + 1, label.apply(options.get(i)));
        }
        return options.get(InputValidator.readInt("Opción: ", 1, options.size()) - 1);
    }

    public static LocalTime readTime(String prompt) {
        while (true) {
            String text = InputValidator.readText(prompt + " (HH:mm): ");
            try {
                return LocalTime.parse(text);
            } catch (DateTimeParseException e) {
                reject("Hora inválida. Use el formato HH:mm (ej. 07:30).");
            }
        }
    }

    public static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static void reject(String message) {
        ErrorLogger.warn("Validation failed: " + message);
        System.out.println("[ERROR] " + message);
    }
}