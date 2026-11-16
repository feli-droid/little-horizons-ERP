package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.Club;
import com.littlehorizons.erp.model.ClubMembership;
import com.littlehorizons.erp.model.MembershipStatus;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link ClubDao}. */
public class JdbcClubDao implements ClubDao {

    private static final String CLUB_SELECT =
            "SELECT c.id_club, c.nombre_club, c.descripcion, c.id_profesor_supervisor, "
                    + "CONCAT_WS(' ', p.primer_nombre, p.primer_apellido, p.segundo_apellido) AS supervisor, "
                    + "c.capacidad_maxima, c.horario_reunion, c.esta_activo, "
                    + "(SELECT COUNT(*) FROM membresias_club m WHERE m.id_club = c.id_club "
                    + "AND m.estado_membresia = 'Activo') AS miembros "
                    + "FROM clubes c LEFT JOIN profesores p ON p.id_profesor = c.id_profesor_supervisor ";

    private static final String MEMBERSHIP_SELECT =
            "SELECT m.id_membresia, m.id_club, c.nombre_club, m.id_estudiante, "
                    + "CONCAT_WS(' ', e.primer_nombre, e.segundo_nombre, e.primer_apellido, e.segundo_apellido) AS estudiante, "
                    + "e.numero_documento, m.fecha_ingreso, m.estado_membresia, m.fecha_retiro, m.motivo_retiro "
                    + "FROM membresias_club m JOIN clubes c ON c.id_club = m.id_club "
                    + "JOIN estudiantes e ON e.id_estudiante = m.id_estudiante ";

    @Override
    public List<Club> findAll() {
        List<Club> clubs = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(CLUB_SELECT + "ORDER BY c.nombre_club");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                clubs.add(mapClub(rs));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list clubs", e);
            throw new DataAccessException("No se pudo consultar la lista de clubes.", e);
        }
        return clubs;
    }

    @Override
    public Optional<Club> findById(int clubId) {
        return findOneClub(CLUB_SELECT + "WHERE c.id_club = ?", String.valueOf(clubId), true);
    }

    @Override
    public Optional<Club> findByName(String name) {
        return findOneClub(CLUB_SELECT + "WHERE c.nombre_club = ?", name, false);
    }

    @Override
    public int insert(String name, String description, int supervisorId, int capacity, String schedule) {
        String sql = "INSERT INTO clubes (nombre_club, descripcion, id_profesor_supervisor, capacidad_maxima, "
                + "horario_reunion) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setInt(3, supervisorId);
            statement.setInt(4, capacity);
            statement.setString(5, schedule);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for club");
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not insert club " + name, e);
            throw new DataAccessException("No se pudo registrar el club.", e);
        }
    }

    @Override
    public Optional<ClubMembership> findMembership(int clubId, int studentId) {
        String sql = MEMBERSHIP_SELECT + "WHERE m.id_club = ? AND m.id_estudiante = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clubId);
            statement.setInt(2, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapMembership(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find membership of student " + studentId, e);
            throw new DataAccessException("No se pudo consultar la membresía.", e);
        }
    }

    @Override
    public boolean enroll(int clubId, int studentId, LocalDate joinDate) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                int capacity = lockClubAndGetCapacity(connection, clubId);
                if (countActiveMembers(connection, clubId) >= capacity) {
                    connection.rollback();
                    return false;
                }
                saveMembership(connection, clubId, studentId, joinDate);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not enroll student " + studentId + " in club " + clubId, e);
            throw new DataAccessException("No se pudo inscribir al estudiante en el club.", e);
        }
    }

    @Override
    public boolean withdraw(int clubId, int studentId, LocalDate withdrawDate, String reason) {
        String sql = "UPDATE membresias_club SET estado_membresia = 'Retirado', fecha_retiro = ?, motivo_retiro = ? "
                + "WHERE id_club = ? AND id_estudiante = ? AND estado_membresia = 'Activo'";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, withdrawDate);
            statement.setString(2, reason);
            statement.setInt(3, clubId);
            statement.setInt(4, studentId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            ErrorLogger.error("Could not withdraw student " + studentId + " from club " + clubId, e);
            throw new DataAccessException("No se pudo retirar al estudiante del club.", e);
        }
    }

    @Override
    public List<ClubMembership> findMembers(int clubId) {
        return findMemberships(MEMBERSHIP_SELECT + "WHERE m.id_club = ? AND m.estado_membresia = 'Activo' "
                + "ORDER BY e.primer_apellido, e.segundo_apellido, e.primer_nombre", clubId);
    }

    @Override
    public List<ClubMembership> findByStudent(int studentId) {
        return findMemberships(MEMBERSHIP_SELECT + "WHERE m.id_estudiante = ? ORDER BY m.estado_membresia, c.nombre_club",
                studentId);
    }

    private int lockClubAndGetCapacity(Connection connection, int clubId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT capacidad_maxima FROM clubes WHERE id_club = ? FOR UPDATE")) {
            statement.setInt(1, clubId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new SQLException("Club not found: " + clubId);
            }
        }
    }

    private int countActiveMembers(Connection connection, int clubId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM membresias_club WHERE id_club = ? AND estado_membresia = 'Activo'")) {
            statement.setInt(1, clubId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void saveMembership(Connection connection, int clubId, int studentId, LocalDate joinDate) throws SQLException {
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT id_membresia FROM membresias_club WHERE id_club = ? AND id_estudiante = ?")) {
            check.setInt(1, clubId);
            check.setInt(2, studentId);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE membresias_club SET estado_membresia = 'Activo', fecha_ingreso = ?, "
                                    + "fecha_retiro = NULL, motivo_retiro = NULL WHERE id_membresia = ?")) {
                        update.setObject(1, joinDate);
                        update.setInt(2, rs.getInt(1));
                        update.executeUpdate();
                    }
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO membresias_club (id_estudiante, id_club, fecha_ingreso) VALUES (?, ?, ?)")) {
            insert.setInt(1, studentId);
            insert.setInt(2, clubId);
            insert.setObject(3, joinDate);
            insert.executeUpdate();
        }
    }

    private Optional<Club> findOneClub(String sql, String value, boolean numeric) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (numeric) {
                statement.setInt(1, Integer.parseInt(value));
            } else {
                statement.setString(1, value);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapClub(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find club " + value, e);
            throw new DataAccessException("No se pudo consultar el club.", e);
        }
    }

    private List<ClubMembership> findMemberships(String sql, int id) {
        List<ClubMembership> memberships = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    memberships.add(mapMembership(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list memberships for id " + id, e);
            throw new DataAccessException("No se pudo consultar las membresías.", e);
        }
        return memberships;
    }

    private Club mapClub(ResultSet rs) throws SQLException {
        return new Club(rs.getInt("id_club"), rs.getString("nombre_club"), rs.getString("descripcion"),
                rs.getInt("id_profesor_supervisor"), rs.getString("supervisor"), rs.getInt("capacidad_maxima"),
                rs.getString("horario_reunion"), rs.getBoolean("esta_activo"), rs.getInt("miembros"));
    }

    private ClubMembership mapMembership(ResultSet rs) throws SQLException {
        Date withdrawDate = rs.getDate("fecha_retiro");
        return new ClubMembership(rs.getInt("id_membresia"), rs.getInt("id_club"), rs.getString("nombre_club"),
                rs.getInt("id_estudiante"), rs.getString("estudiante"), rs.getString("numero_documento"),
                rs.getDate("fecha_ingreso").toLocalDate(), MembershipStatus.fromDb(rs.getString("estado_membresia")),
                withdrawDate == null ? null : withdrawDate.toLocalDate(), rs.getString("motivo_retiro"));
    }
}