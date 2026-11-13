package com.littlehorizons.erp.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;
import java.util.regex.Pattern;

/** Reads and validates console input, retrying until the value is valid. Every rejected input is logged. */
public final class InputValidator {

    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private InputValidator() { }

    /** Reads a required, non-empty text. */
    public static String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = SCANNER.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            reject(prompt, "Este campo no puede estar vacío.");
        }
    }

    /** Reads an optional text; returns null when the user just presses Enter. */
    public static String readOptionalText(String prompt) {
        System.out.print(prompt);
        String value = SCANNER.nextLine().trim();
        return value.isEmpty() ? null : value;
    }

    public static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(SCANNER.nextLine().trim());
                if (value >= min && value <= max) {
                    return value;
                }
                reject(prompt, String.format("Ingrese un número entre %d y %d.", min, max));
            } catch (NumberFormatException e) {
                reject(prompt, "Ingrese un número entero válido.");
            }
        }
    }

    public static BigDecimal readDecimal(String prompt, BigDecimal min, BigDecimal max) {
        while (true) {
            System.out.print(prompt);
            try {
                BigDecimal value = new BigDecimal(SCANNER.nextLine().trim().replace(',', '.'));
                if (value.compareTo(min) >= 0 && value.compareTo(max) <= 0) {
                    return value;
                }
                reject(prompt, String.format("El valor debe estar entre %s y %s.", min, max));
            } catch (NumberFormatException e) {
                reject(prompt, "Ingrese un número válido.");
            }
        }
    }

    public static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt + " (AAAA-MM-DD): ");
            try {
                return LocalDate.parse(SCANNER.nextLine().trim());
            } catch (DateTimeParseException e) {
                reject(prompt, "Fecha inválida. Use el formato AAAA-MM-DD.");
            }
        }
    }

    public static String readEmail(String prompt) {
        while (true) {
            String value = readText(prompt);
            if (EMAIL.matcher(value).matches()) {
                return value;
            }
            reject(prompt, "Correo electrónico inválido.");
        }
    }

    public static boolean confirm(String prompt) {
        while (true) {
            System.out.print(prompt + " (S/N): ");
            String value = SCANNER.nextLine().trim().toUpperCase();
            if (value.equals("S")) {
                return true;
            }
            if (value.equals("N")) {
                return false;
            }
            reject(prompt, "Responda S o N.");
        }
    }

    /** Pauses until the user presses Enter. */
    public static void waitForEnter() {
        System.out.print("\nPresione Enter para continuar...");
        SCANNER.nextLine();
    }

    /** Shows the error to the user and records it in the log. */
    private static void reject(String prompt, String reason) {
        ErrorLogger.warn("Invalid input at '" + prompt.trim() + "': " + reason);
        System.out.println("[ERROR] " + reason);
    }
}