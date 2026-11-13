package com.littlehorizons.erp.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Writes error, warning and info messages to error_log.txt. */
public final class ErrorLogger {

    private static final Path LOG_FILE = Path.of("error_log.txt");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ErrorLogger() { }

    /** Technical failures (database, files, unexpected exceptions). */
    public static void error(String message, Throwable cause) {
        write("ERROR", message + (cause != null ? " | " + cause : ""));
    }

    /** Input or business rules rejected by the system (not a system failure). */
    public static void warn(String message) {
        write("WARN", message);
    }

    /** Normal application events. */
    public static void info(String message) {
        write("INFO", message);
    }

    private static synchronized void write(String level, String message) {
        String line = String.format("[%s] [%s] %s%n", LocalDateTime.now().format(FORMAT), level, message);
        try {
            Files.writeString(LOG_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("No se pudo escribir en el log: " + e.getMessage());
        }
    }
}