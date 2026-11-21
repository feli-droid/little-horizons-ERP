package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.AttendanceDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.AttendanceImportResult;
import com.littlehorizons.erp.model.AttendanceStatus;
import com.littlehorizons.erp.model.ImportedAttendance;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.ImportReportFileWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Business rules of the bulk attendance import: reads the CSV and saves each valid line on its own. */
public class AttendanceImportController {

    private static final String IMPORT_FILE_NAME = "asistencias_importar.csv";
    private static final String EXPECTED_HEADER = "id_estudiante;id_materia;fecha;estado";
    private static final String FIELD_SEPARATOR = ";";
    private static final int EXPECTED_FIELDS = 4;
    private static final char BYTE_ORDER_MARK = '﻿';

    private final AttendanceDao attendanceDao;

    public AttendanceImportController(AttendanceDao attendanceDao) {
        this.attendanceDao = attendanceDao;
    }

    /** Imports the default file located in the project root folder. */
    public AttendanceImportResult importFromFile() throws BusinessException {
        return importFromFile(Path.of(IMPORT_FILE_NAME));
    }

    /**
     * Imports the given file line by line. A bad line is only counted as failed; it never stops the process.
     *
     * @throws BusinessException if the file does not exist, cannot be read or has a wrong header
     */
    public AttendanceImportResult importFromFile(Path file) throws BusinessException {
        int imported = 0;
        int failed = 0;
        for (String line : readDataLines(file)) {
            Optional<ImportedAttendance> record = parseLine(line);
            if (record.isPresent() && save(record.get())) {
                imported++;
            } else {
                failed++;
            }
        }
        ErrorLogger.info("Attendance import finished: " + imported + " saved, " + failed + " rejected");
        return new AttendanceImportResult(imported, failed);
    }

    /**
     * Writes the import report of a year, but only when the file and the database agree: every valid line of the
     * file that belongs to that year must already be stored with the same status (that is, it was imported).
     *
     * @return the path of the report written
     * @throws BusinessException if the file has no records of the year or they are not in the database yet
     */
    public Path generateImportReport(int year) throws BusinessException {
        Path source = Path.of(IMPORT_FILE_NAME);
        // Same key twice in the file: the last line wins, exactly as it does when it is imported.
        Map<String, ImportedAttendance> recordsOfYear = new LinkedHashMap<>();
        for (String line : readDataLines(source)) {
            parseLine(line).filter(r -> r.date().getYear() == year)
                    .ifPresent(r -> recordsOfYear.put(r.studentId() + "/" + r.subjectId() + "/" + r.date(), r));
        }
        if (recordsOfYear.isEmpty()) {
            throw new BusinessException("No se generó el reporte de importación: el archivo '" + IMPORT_FILE_NAME
                    + "' no tiene registros válidos del año " + year + ".");
        }
        if (!allStoredInDatabase(recordsOfYear.values())) {
            throw new BusinessException("No se generó el reporte de importación: la asistencia de " + year
                    + " en la base de datos no coincide con '" + IMPORT_FILE_NAME
                    + "'. Importe primero el archivo con la opción 8 del menú principal.");
        }
        List<ImportedAttendance> records = new ArrayList<>(recordsOfYear.values());
        Map<Integer, String> studentNames = attendanceDao.findStudentNames(
                records.stream().map(ImportedAttendance::studentId).collect(Collectors.toSet()));
        try {
            return ImportReportFileWriter.write(year, IMPORT_FILE_NAME, records, studentNames);
        } catch (IOException e) {
            ErrorLogger.error("Could not write the import report", e);
            throw new BusinessException("No se pudo escribir el reporte de importación.");
        }
    }

    private boolean allStoredInDatabase(Iterable<ImportedAttendance> records) {
        Map<String, Map<Integer, AttendanceStatus>> storedByCourseAndDate = new HashMap<>();
        for (ImportedAttendance record : records) {
            Map<Integer, AttendanceStatus> stored = storedByCourseAndDate.computeIfAbsent(
                    record.subjectId() + "/" + record.date(),
                    key -> attendanceDao.findByCourseAndDate(record.subjectId(), record.date()));
            if (stored.get(record.studentId()) != record.status()) {
                return false;
            }
        }
        return true;
    }

    /** Reads the data lines of the file (without the header and without blank lines). */
    private List<String> readDataLines(Path file) throws BusinessException {
        if (!Files.isRegularFile(file)) {
            throw new BusinessException("Error: El archivo '" + IMPORT_FILE_NAME
                    + "' no se encuentra en la carpeta raíz del proyecto.");
        }
        List<String> lines = new ArrayList<>();
        // InputStreamReader replaces unreadable characters instead of throwing, so a badly encoded line
        // is simply rejected by the state validation and the rest of the file is still processed.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            if (!hasValidHeader(reader.readLine())) {
                throw new BusinessException("Error: El archivo no tiene el formato correcto. "
                        + "La cabecera debe ser: " + EXPECTED_HEADER);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            ErrorLogger.error("Could not read " + IMPORT_FILE_NAME, e);
            throw new BusinessException("Error: No se pudo leer el archivo '" + IMPORT_FILE_NAME + "'.");
        }
        return lines;
    }

    /** The header must match exactly; only the invisible mark that some editors add at the start is ignored. */
    private boolean hasValidHeader(String firstLine) {
        if (firstLine == null) {
            return false;
        }
        String header = firstLine.isEmpty() || firstLine.charAt(0) != BYTE_ORDER_MARK
                ? firstLine : firstLine.substring(1);
        return EXPECTED_HEADER.equals(header);
    }

    /** Validates one data line in the order of the statement; empty when it must be rejected. */
    private Optional<ImportedAttendance> parseLine(String line) {
        String[] fields = line.split(FIELD_SEPARATOR, -1);
        if (fields.length != EXPECTED_FIELDS) {
            return Optional.empty();
        }
        try {
            int studentId = Integer.parseInt(fields[0]);
            int subjectId = Integer.parseInt(fields[1]);
            if (!attendanceDao.studentExists(studentId) || !attendanceDao.subjectExists(subjectId)) {
                return Optional.empty();
            }
            LocalDate date = LocalDate.parse(fields[2]);
            AttendanceStatus status = AttendanceStatus.fromDb(fields[3]);
            return Optional.of(new ImportedAttendance(studentId, subjectId, date, status));
        } catch (IllegalArgumentException | DateTimeParseException | DataAccessException e) {
            return Optional.empty();
        }
    }

    /** Saves one valid record; false when the database refuses it. */
    private boolean save(ImportedAttendance record) {
        try {
            attendanceDao.insertImported(record.studentId(), record.subjectId(), record.date(), record.status());
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }
}