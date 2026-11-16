package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.EventParticipant;
import com.littlehorizons.erp.model.EventPosition;
import com.littlehorizons.erp.model.EventType;
import com.littlehorizons.erp.model.ParticipationRole;
import com.littlehorizons.erp.model.SchoolEvent;
import com.littlehorizons.erp.model.StudentParticipation;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link EventDao}. */
public class JdbcEventDao implements EventDao {

    private static final String EVENT_SELECT =
            "SELECT ev.id_evento, ev.nombre_evento, ev.descripcion, ev.tipo_evento, ev.fecha_evento, ev.lugar, "
                    + "ev.tiene_competencia, ev.id_organizador, "
                    + "CONCAT_WS(' ', p.primer_nombre, p.primer_apellido, p.segundo_apellido) AS organizador, "
                    + "ev.esta_activo, (SELECT COUNT(*) FROM participantes_evento pe "
                    + "WHERE pe.id_evento = ev.id_evento) AS participantes "
                    + "FROM eventos ev LEFT JOIN profesores p ON p.id_profesor = ev.id_organizador ";

    private static final String PARTICIPANT_SELECT =
            "SELECT pe.id_participacion, pe.id_evento, pe.id_estudiante, "
                    + "CONCAT_WS(' ', e.primer_nombre, e.segundo_nombre, e.primer_apellido, e.segundo_apellido) AS estudiante, "
                    + "e.numero_documento, pe.rol_participacion, pe.posicion_obtenida "
                    + "FROM participantes_evento pe JOIN estudiantes e ON e.id_estudiante = pe.id_estudiante ";

    @Override
    public List<SchoolEvent> findAll() {
        List<SchoolEvent> events = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(EVENT_SELECT + "ORDER BY ev.fecha_evento");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                events.add(mapEvent(rs));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list events", e);
            throw new DataAccessException("No se pudo consultar la lista de eventos.", e);
        }
        return events;
    }

    @Override
    public Optional<SchoolEvent> findById(int eventId) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(EVENT_SELECT + "WHERE ev.id_evento = ?")) {
            statement.setInt(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapEvent(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find event " + eventId, e);
            throw new DataAccessException("No se pudo consultar el evento.", e);
        }
    }

    @Override
    public int insert(String name, String description, EventType type, LocalDate date, String place,
                      boolean competition, int organizerId) {
        String sql = "INSERT INTO eventos (nombre_evento, descripcion, tipo_evento, fecha_evento, lugar, "
                + "tiene_competencia, id_organizador) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setString(3, type.getDbValue());
            statement.setObject(4, date);
            statement.setString(5, place);
            statement.setBoolean(6, competition);
            statement.setInt(7, organizerId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for event");
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not insert event " + name, e);
            throw new DataAccessException("No se pudo registrar el evento.", e);
        }
    }

    @Override
    public List<EventParticipant> findParticipants(int eventId) {
        List<EventParticipant> participants = new ArrayList<>();
        String sql = PARTICIPANT_SELECT + "WHERE pe.id_evento = ? "
                + "ORDER BY pe.posicion_obtenida IS NULL, pe.posicion_obtenida, e.primer_apellido, e.primer_nombre";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    participants.add(mapParticipant(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list participants of event " + eventId, e);
            throw new DataAccessException("No se pudo consultar los participantes del evento.", e);
        }
        return participants;
    }

    @Override
    public Optional<EventParticipant> findParticipant(int eventId, int studentId) {
        String sql = PARTICIPANT_SELECT + "WHERE pe.id_evento = ? AND pe.id_estudiante = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, eventId);
            statement.setInt(2, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapParticipant(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find participant " + studentId + " of event " + eventId, e);
            throw new DataAccessException("No se pudo consultar la participación.", e);
        }
    }

    @Override
    public void addParticipant(int eventId, int studentId, ParticipationRole role) {
        String sql = "INSERT INTO participantes_evento (id_evento, id_estudiante, rol_participacion) VALUES (?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, eventId);
            statement.setInt(2, studentId);
            statement.setString(3, role.getDbValue());
            statement.executeUpdate();
        } catch (SQLException e) {
            ErrorLogger.error("Could not add participant " + studentId + " to event " + eventId, e);
            throw new DataAccessException("No se pudo registrar al participante.", e);
        }
    }

    @Override
    public void setPosition(int participationId, EventPosition position) {
        String sql = "UPDATE participantes_evento SET posicion_obtenida = ? WHERE id_participacion = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, position.getDbValue());
            statement.setInt(2, participationId);
            statement.executeUpdate();
        } catch (SQLException e) {
            ErrorLogger.error("Could not set position of participation " + participationId, e);
            throw new DataAccessException("No se pudo registrar la posición.", e);
        }
    }

    @Override
    public boolean isPositionTaken(int eventId, EventPosition position) {
        String sql = "SELECT COUNT(*) FROM participantes_evento WHERE id_evento = ? AND posicion_obtenida = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, eventId);
            statement.setString(2, position.getDbValue());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not check position of event " + eventId, e);
            throw new DataAccessException("No se pudo verificar la posición.", e);
        }
    }

    @Override
    public List<StudentParticipation> findByStudent(int studentId) {
        List<StudentParticipation> participations = new ArrayList<>();
        String sql = "SELECT ev.nombre_evento, ev.tipo_evento, ev.fecha_evento, pe.rol_participacion, "
                + "pe.posicion_obtenida FROM participantes_evento pe "
                + "JOIN eventos ev ON ev.id_evento = pe.id_evento WHERE pe.id_estudiante = ? ORDER BY ev.fecha_evento";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    participations.add(new StudentParticipation(rs.getString("nombre_evento"),
                            EventType.fromDb(rs.getString("tipo_evento")), rs.getDate("fecha_evento").toLocalDate(),
                            ParticipationRole.fromDb(rs.getString("rol_participacion")),
                            EventPosition.fromDb(rs.getString("posicion_obtenida"))));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list participations of student " + studentId, e);
            throw new DataAccessException("No se pudo consultar la participación del estudiante.", e);
        }
        return participations;
    }

    private SchoolEvent mapEvent(ResultSet rs) throws SQLException {
        return new SchoolEvent(rs.getInt("id_evento"), rs.getString("nombre_evento"), rs.getString("descripcion"),
                EventType.fromDb(rs.getString("tipo_evento")), rs.getDate("fecha_evento").toLocalDate(),
                rs.getString("lugar"), rs.getBoolean("tiene_competencia"), rs.getInt("id_organizador"),
                rs.getString("organizador"), rs.getBoolean("esta_activo"), rs.getInt("participantes"));
    }

    private EventParticipant mapParticipant(ResultSet rs) throws SQLException {
        return new EventParticipant(rs.getInt("id_participacion"), rs.getInt("id_evento"), rs.getInt("id_estudiante"),
                rs.getString("estudiante"), rs.getString("numero_documento"),
                ParticipationRole.fromDb(rs.getString("rol_participacion")),
                EventPosition.fromDb(rs.getString("posicion_obtenida")));
    }
}