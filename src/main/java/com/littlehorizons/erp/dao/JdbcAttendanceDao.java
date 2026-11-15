package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.AbsenceSummary;
import com.littlehorizons.erp.model.AttendanceEntry;
import com.littlehorizons.erp.model.AttendanceStatus;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JDBC implementation of {@link AttendanceDao}. */
public class JdbcAttendanceDao implements AttendanceDao {

    private static final String UPSERT =
            "INSERT INTO asistencias (id_estudiante, id_materia, fecha_asistencia, estado_asistencia, registrado_por) "
                    + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE estado_asistencia = VALUES(estado_asistencia), "
                    + "registrado_por = VALUES(registrado_por)";

    private static final String ABSENCES =
            "SELECT e.id_estudiante, e.numero_documento, "
                    + "CONCAT_WS(' ', e.primer_nombre, e.segundo_nombre, e.primer_apellido, e.segundo_apellido) AS nombre, "
                    + "COUNT(*) AS ausencias FROM asistencias a JOIN estudiantes e ON e.id_estudiante = a.id_estudiante "
                    + "WHERE a.estado_asistencia = 'Ausente' AND a.fecha_asistencia BETWEEN ? AND ? "
                    + "GROUP BY e.id_estudiante HAVING COUNT(*) > ? ORDER BY ausencias DESC, e.primer_apellido";

    private static final String BY_STUDENT =
            "SELECT a.fecha_asistencia, m.nombre_materia, a.estado_asistencia FROM asistencias a "
                    + "JOIN materias m ON m.id_materia = a.id_materia "
                    + "WHERE a.id_estudiante = ? AND a.fecha_asistencia BETWEEN ? AND ? "
                    + "ORDER BY a.fecha_asistencia, m.nombre_materia";

    @Override
    public Map<Integer, AttendanceStatus> findByCourseAndDate(int subjectId, LocalDate date) {
        Map<Integer, AttendanceStatus> statuses = new HashMap<>();
        String sql = "SELECT id_estudiante, estado_asistencia FROM asistencias WHERE id_materia = ? AND fecha_asistencia = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, subjectId);
            statement.setObject(2, date);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    statuses.put(rs.getInt("id_estudiante"), AttendanceStatus.fromDb(rs.getString("estado_asistencia")));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load attendance of subject " + subjectId, e);
            throw new DataAccessException("No se pudo consultar la asistencia.", e);
        }
        return statuses;
    }

    @Override
    public void saveAll(int subjectId, LocalDate date, int teacherId, Map<Integer, AttendanceStatus> statuses) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (Map.Entry<Integer, AttendanceStatus> entry : statuses.entrySet()) {
                    statement.setInt(1, entry.getKey());
                    statement.setInt(2, subjectId);
                    statement.setObject(3, date);
                    statement.setString(4, entry.getValue().getDbValue());
                    statement.setInt(5, teacherId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not save attendance of subject " + subjectId + " on " + date, e);
            throw new DataAccessException("No se pudo guardar la asistencia. No se guardó ningún cambio.", e);
        }
    }

    @Override
    public List<AbsenceSummary> findAbsenceReport(LocalDate from, LocalDate to, int maxAbsences) {
        List<AbsenceSummary> report = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(ABSENCES)) {
            statement.setObject(1, from);
            statement.setObject(2, to);
            statement.setInt(3, maxAbsences);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    report.add(new AbsenceSummary(rs.getInt("id_estudiante"), rs.getString("numero_documento"),
                            rs.getString("nombre"), rs.getLong("ausencias")));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not build absence report", e);
            throw new DataAccessException("No se pudo generar el reporte de inasistencias.", e);
        }
        return report;
    }

    @Override
    public List<AttendanceEntry> findByStudent(int studentId, LocalDate from, LocalDate to) {
        List<AttendanceEntry> entries = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(BY_STUDENT)) {
            statement.setInt(1, studentId);
            statement.setObject(2, from);
            statement.setObject(3, to);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new AttendanceEntry(rs.getDate("fecha_asistencia").toLocalDate(),
                            rs.getString("nombre_materia"), AttendanceStatus.fromDb(rs.getString("estado_asistencia"))));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load attendance of student " + studentId, e);
            throw new DataAccessException("No se pudo consultar la asistencia del estudiante.", e);
        }
        return entries;
    }
}