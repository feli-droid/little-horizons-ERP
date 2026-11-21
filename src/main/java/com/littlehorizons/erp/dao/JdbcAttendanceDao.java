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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JDBC implementation of {@link AttendanceDao}. */
public class JdbcAttendanceDao implements AttendanceDao {

    private static final String UPSERT =
            "INSERT INTO asistencias (id_estudiante, id_materia, fecha_asistencia, estado_asistencia, registrado_por) "
                    + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE estado_asistencia = VALUES(estado_asistencia), "
                    + "registrado_por = VALUES(registrado_por)";

    private static final String INSERT_IMPORTED =
            "INSERT INTO asistencias (id_estudiante, id_materia, fecha_asistencia, estado_asistencia) "
                    + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE estado_asistencia = VALUES(estado_asistencia)";

    private static final String STUDENT_EXISTS = "SELECT 1 FROM estudiantes WHERE id_estudiante = ?";

    private static final String SUBJECT_EXISTS = "SELECT 1 FROM materias WHERE id_materia = ?";

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

    @Override
    public Map<Integer, String> findStudentNames(Collection<Integer> studentIds) {
        Map<Integer, String> names = new HashMap<>();
        if (studentIds.isEmpty()) {
            return names;
        }
        String sql = "SELECT id_estudiante, CONCAT_WS(' ', primer_nombre, segundo_nombre, primer_apellido, "
                + "segundo_apellido) AS nombre FROM estudiantes WHERE id_estudiante IN ("
                + String.join(", ", Collections.nCopies(studentIds.size(), "?")) + ")";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int position = 1;
            for (int studentId : studentIds) {
                statement.setInt(position++, studentId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    names.put(rs.getInt("id_estudiante"), rs.getString("nombre"));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load the names of the students", e);
            throw new DataAccessException("No se pudieron consultar los nombres de los estudiantes.", e);
        }
        return names;
    }

    @Override
    public boolean studentExists(int studentId) {
        return exists(STUDENT_EXISTS, studentId);
    }

    @Override
    public boolean subjectExists(int subjectId) {
        return exists(SUBJECT_EXISTS, subjectId);
    }

    @Override
    public void insertImported(int studentId, int subjectId, LocalDate date, AttendanceStatus status) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_IMPORTED)) {
            statement.setInt(1, studentId);
            statement.setInt(2, subjectId);
            statement.setObject(3, date);
            statement.setString(4, status.getDbValue());
            statement.executeUpdate();
        } catch (SQLException e) {
            ErrorLogger.error("Could not import attendance of student " + studentId + " in subject " + subjectId, e);
            throw new DataAccessException("No se pudo guardar la asistencia importada.", e);
        }
    }

    private boolean exists(String sql, int id) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not check the existence of id " + id, e);
            throw new DataAccessException("No se pudo consultar la base de datos.", e);
        }
    }
}