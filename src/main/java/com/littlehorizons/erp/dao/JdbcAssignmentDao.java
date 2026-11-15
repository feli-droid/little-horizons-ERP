package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.Assignment;
import com.littlehorizons.erp.model.ScheduleSlot;
import com.littlehorizons.erp.model.SchoolDay;
import com.littlehorizons.erp.model.Subject;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link AssignmentDao}. */
public class JdbcAssignmentDao implements AssignmentDao {

    private static final String SUBJECT_SELECT =
            "SELECT m.id_materia, m.codigo_materia, m.nombre_materia, m.id_grado, g.nombre_grado, "
                    + "m.horas_semanales, m.esta_activa "
                    + "FROM materias m JOIN grados g ON g.id_grado = m.id_grado ";

    private static final String ASSIGNMENT_SELECT =
            "SELECT a.id_asignacion, a.id_profesor, a.anio_academico, a.seccion, "
                    + "CONCAT_WS(' ', p.primer_nombre, p.primer_apellido, p.segundo_apellido) AS profesor, "
                    + "m.id_materia, m.codigo_materia, m.nombre_materia, m.id_grado, g.nombre_grado, "
                    + "m.horas_semanales, m.esta_activa "
                    + "FROM asignaciones_profesor a "
                    + "JOIN profesores p ON p.id_profesor = a.id_profesor "
                    + "JOIN materias m ON m.id_materia = a.id_materia "
                    + "JOIN grados g ON g.id_grado = m.id_grado ";

    private static final String SLOT_SELECT =
            "SELECT h.id_horario, h.id_asignacion, h.dia_semana, h.hora_inicio, h.hora_fin, "
                    + "CONCAT(m.nombre_materia, ' - ', g.nombre_grado) AS descripcion "
                    + "FROM horarios_clase h "
                    + "JOIN asignaciones_profesor a ON a.id_asignacion = h.id_asignacion "
                    + "JOIN materias m ON m.id_materia = a.id_materia "
                    + "JOIN grados g ON g.id_grado = m.id_grado ";

    private static final String SLOT_ORDER = "ORDER BY h.dia_semana, h.hora_inicio";

    private static final String CONFLICTS =
            SLOT_SELECT
                    + "JOIN asignaciones_profesor t ON t.id_asignacion = ? "
                    + "JOIN materias tm ON tm.id_materia = t.id_materia "
                    + "WHERE a.anio_academico = t.anio_academico "
                    + "AND (a.id_profesor = t.id_profesor OR (m.id_grado = tm.id_grado AND a.seccion = t.seccion)) "
                    + "AND h.dia_semana = ? AND h.hora_inicio < ? AND h.hora_fin > ? " + SLOT_ORDER;

    @Override
    public List<Subject> findActiveSubjects() {
        List<Subject> subjects = new ArrayList<>();
        String sql = SUBJECT_SELECT + "WHERE m.esta_activa = TRUE ORDER BY g.nivel_numerico, m.nombre_materia";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                subjects.add(mapSubject(rs));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list subjects", e);
            throw new DataAccessException("No se pudo consultar la lista de materias.", e);
        }
        return subjects;
    }

    @Override
    public Optional<Subject> findSubjectByCode(String code) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SUBJECT_SELECT + "WHERE m.codigo_materia = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapSubject(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find subject " + code, e);
            throw new DataAccessException("No se pudo consultar la materia.", e);
        }
    }

    @Override
    public Optional<Assignment> findAssignment(int subjectId, int year, String section) {
        String sql = ASSIGNMENT_SELECT + "WHERE a.id_materia = ? AND a.anio_academico = ? AND a.seccion = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, subjectId);
            statement.setInt(2, year);
            statement.setString(3, section);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapAssignment(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find assignment of subject " + subjectId, e);
            throw new DataAccessException("No se pudo consultar la asignación.", e);
        }
    }

    @Override
    public List<Assignment> findByTeacher(int teacherId, int year) {
        List<Assignment> assignments = new ArrayList<>();
        String sql = ASSIGNMENT_SELECT + "WHERE a.id_profesor = ? AND a.anio_academico = ? "
                + "ORDER BY g.nivel_numerico, m.nombre_materia";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, year);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    assignments.add(mapAssignment(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list assignments of teacher " + teacherId, e);
            throw new DataAccessException("No se pudieron consultar las materias del profesor.", e);
        }
        return assignments;
    }

    @Override
    public int insertAssignment(int teacherId, int subjectId, int year, String section) {
        String sql = "INSERT INTO asignaciones_profesor (id_profesor, id_materia, anio_academico, seccion) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, teacherId);
            statement.setInt(2, subjectId);
            statement.setInt(3, year);
            statement.setString(4, section);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for assignment");
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not assign subject " + subjectId + " to teacher " + teacherId, e);
            throw new DataAccessException("No se pudo asignar la materia al profesor.", e);
        }
    }

    @Override
    public int insertSchedule(int assignmentId, SchoolDay day, LocalTime start, LocalTime end) {
        String sql = "INSERT INTO horarios_clase (id_asignacion, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, assignmentId);
            statement.setString(2, day.getDbValue());
            statement.setTime(3, Time.valueOf(start));
            statement.setTime(4, Time.valueOf(end));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for schedule");
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not insert schedule for assignment " + assignmentId, e);
            throw new DataAccessException("No se pudo registrar el horario.", e);
        }
    }

    @Override
    public List<ScheduleSlot> findSchedulesByAssignment(int assignmentId) {
        return findSlots(SLOT_SELECT + "WHERE h.id_asignacion = ? " + SLOT_ORDER, assignmentId);
    }

    @Override
    public List<ScheduleSlot> findScheduleOfTeacher(int teacherId, int year) {
        return findSlots(SLOT_SELECT + "WHERE a.id_profesor = ? AND a.anio_academico = ? " + SLOT_ORDER, teacherId, year);
    }

    @Override
    public List<ScheduleSlot> findConflicts(int assignmentId, SchoolDay day, LocalTime start, LocalTime end) {
        List<ScheduleSlot> slots = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(CONFLICTS)) {
            statement.setInt(1, assignmentId);
            statement.setString(2, day.getDbValue());
            statement.setTime(3, Time.valueOf(end));
            statement.setTime(4, Time.valueOf(start));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    slots.add(mapSlot(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not check schedule conflicts of assignment " + assignmentId, e);
            throw new DataAccessException("No se pudo verificar los choques de horario.", e);
        }
        return slots;
    }

    private List<ScheduleSlot> findSlots(String sql, int... params) {
        List<ScheduleSlot> slots = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setInt(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    slots.add(mapSlot(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load schedules", e);
            throw new DataAccessException("No se pudo consultar el horario.", e);
        }
        return slots;
    }

    private Subject mapSubject(ResultSet rs) throws SQLException {
        return new Subject(rs.getInt("id_materia"), rs.getString("codigo_materia"), rs.getString("nombre_materia"),
                rs.getInt("id_grado"), rs.getString("nombre_grado"), rs.getInt("horas_semanales"),
                rs.getBoolean("esta_activa"));
    }

    private Assignment mapAssignment(ResultSet rs) throws SQLException {
        return new Assignment(rs.getInt("id_asignacion"), rs.getInt("id_profesor"), rs.getString("profesor"),
                mapSubject(rs), rs.getInt("anio_academico"), rs.getString("seccion"));
    }

    private ScheduleSlot mapSlot(ResultSet rs) throws SQLException {
        return new ScheduleSlot(rs.getInt("id_horario"), rs.getInt("id_asignacion"),
                SchoolDay.fromDb(rs.getString("dia_semana")),
                rs.getTime("hora_inicio").toLocalTime(), rs.getTime("hora_fin").toLocalTime(),
                rs.getString("descripcion"));
    }
}