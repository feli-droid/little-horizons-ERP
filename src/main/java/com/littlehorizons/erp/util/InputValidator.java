package com.littlehorizons.erp.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;
import java.util.regex.Pattern;

/** Reads and validates console input, retrying until the value is valid. */
public final class InputValidator {

    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private InputValidator() { }

    public static String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = SCANNER.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("[ERROR] Este campo no puede estar vacío.");
        }
    }

    public static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(SCANNER.nextLine().trim());
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.printf("[ERROR] Ingrese un número entre %d y %d.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("[ERROR] Ingrese un número entero válido.");
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
                System.out.printf("[ERROR] El valor debe estar entre %s y %s.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("[ERROR] Ingrese un número válido.");
            }
        }
    }

    public static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt + " (AAAA-MM-DD): ");
            try {
                return LocalDate.parse(SCANNER.nextLine().trim());
            } catch (DateTimeParseException e) {
                System.out.println("[ERROR] Fecha inválida. Use el formato AAAA-MM-DD.");
            }
        }
    }

    public static String readEmail(String prompt) {
        while (true) {
            String value = readText(prompt);
            if (EMAIL.matcher(value).matches()) {
                return value;
            }
            System.out.println("[ERROR] Correo electrónico inválido.");
        }
    }

    public static boolean confirm(String prompt) {
        while (true) {
            System.out.print(prompt + " (S/N): ");
            String value = SCANNER.nextLine().trim().toUpperCase();
            if (value.equals("S")) return true;
            if (value.equals("N")) return false;
            System.out.println("[ERROR] Responda S o N.");
        }
    }
}