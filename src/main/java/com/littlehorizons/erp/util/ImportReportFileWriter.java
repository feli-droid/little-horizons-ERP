package com.littlehorizons.erp.util;

import com.littlehorizons.erp.model.ImportedAttendance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Writes the attendance import report as a plain text file in the project root folder. */
public final class ImportReportFileWriter {

    public static final String FILE_NAME = "reporte_importacion_asistencias.txt";
    private static final String DOUBLE_LINE = "=".repeat(84);
    private static final String SINGLE_LINE = "-".repeat(84);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ImportReportFileWriter() { }

    /** @return the path of the file written; an existing report is replaced */
    public static Path write(int year, String sourceFile, List<ImportedAttendance> records,
                             Map<Integer, String> studentNames) throws IOException {
        Path file = Path.of(FILE_NAME);
        Files.writeString(file, render(year, sourceFile, records, studentNames), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    private static String render(int year, String sourceFile, List<ImportedAttendance> records,
                                 Map<Integer, String> studentNames) {
        StringBuilder text = new StringBuilder();
        text.append(DOUBLE_LINE).append('\n');
        text.append(" REPORTE DE IMPORTACIÓN DE ASISTENCIAS\n");
        text.append(" Colegio Little Horizons\n");
        text.append(DOUBLE_LINE).append('\n');
        text.append(" Generado          : ").append(LocalDateTime.now().format(TIMESTAMP)).append('\n');
        text.append(" Archivo de origen : ").append(sourceFile).append('\n');
        text.append(" Año               : ").append(year).append('\n');
        text.append(" Registros         : ").append(records.size()).append(" (todos coinciden con la base de datos)\n");
        text.append(SINGLE_LINE).append('\n');
        text.append(String.format(" %-6s %-36s %-11s %-12s %s%n", "ID", "Estudiante", "ID materia", "Fecha", "Estado"));
        text.append(SINGLE_LINE).append('\n');
        records.forEach(r -> text.append(String.format(" %-6d %-36s %-11d %-12s %s%n", r.studentId(),
                studentNames.getOrDefault(r.studentId(), "-"), r.subjectId(), r.date(), r.status().getDbValue())));
        text.append(DOUBLE_LINE).append('\n');
        return text.toString();
    }
}