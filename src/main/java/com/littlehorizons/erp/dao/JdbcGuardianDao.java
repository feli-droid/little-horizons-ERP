package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.DocumentType;
import com.littlehorizons.erp.model.Guardian;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link GuardianDao}. */
public class JdbcGuardianDao implements GuardianDao {

    private static final String COLUMNS =
            "a.id_acudiente, a.tipo_documento, a.numero_documento, a.primer_nombre, a.segundo_nombre, "
                    + "a.primer_apellido, a.segundo_apellido, a.parentesco, a.telefono_principal, "
                    + "a.correo_electronico, a.direccion, a.ocupacion";

    private static final String SELECT_BY_DOCUMENT =
            "SELECT " + COLUMNS + " FROM acudientes a WHERE a.numero_documento = ?";

    private static final String SELECT_BY_STUDENT =
            "SELECT " + COLUMNS + " FROM acudientes a "
                    + "JOIN estudiante_acudiente ea ON ea.id_acudiente = a.id_acudiente "
                    + "WHERE ea.id_estudiante = ? ORDER BY ea.es_principal DESC, a.primer_apellido";

    private static final String INSERT =
            "INSERT INTO acudientes (tipo_documento, numero_documento, primer_nombre, segundo_nombre, "
                    + "primer_apellido, segundo_apellido, parentesco, telefono_principal, correo_electronico, "
                    + "direccion, ocupacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String LINK =
            "INSERT INTO estudiante_acudiente (id_estudiante, id_acudiente, es_principal) VALUES (?, ?, ?)";

    @Override
    public Optional<Guardian> findByDocument(String documentNumber) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_DOCUMENT)) {
            statement.setString(1, documentNumber);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find guardian by document " + documentNumber, e);
            throw new DataAccessException("No se pudo consultar el acudiente.", e);
        }
    }

    @Override
    public List<Guardian> findByStudentId(int studentId) {
        List<Guardian> guardians = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_STUDENT)) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    guardians.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list guardians of student " + studentId, e);
            throw new DataAccessException("No se pudieron consultar los acudientes del estudiante.", e);
        }
        return guardians;
    }

    @Override
    public int insert(Connection connection, Guardian guardian) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, guardian.getDocumentType().getDbValue());
            statement.setString(2, guardian.getDocumentNumber());
            statement.setString(3, guardian.getFirstName());
            statement.setString(4, guardian.getMiddleName());
            statement.setString(5, guardian.getLastName());
            statement.setString(6, guardian.getSecondLastName());
            statement.setString(7, guardian.getRelationship());
            statement.setString(8, guardian.getPrimaryPhone());
            statement.setString(9, guardian.getEmail());
            statement.setString(10, guardian.getAddress());
            statement.setString(11, guardian.getOccupation());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for guardian");
            }
        }
    }

    @Override
    public void linkToStudent(Connection connection, int studentId, int guardianId, boolean primary)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LINK)) {
            statement.setInt(1, studentId);
            statement.setInt(2, guardianId);
            statement.setBoolean(3, primary);
            statement.executeUpdate();
        }
    }

    private Guardian mapRow(ResultSet rs) throws SQLException {
        Guardian guardian = new Guardian();
        guardian.setId(rs.getInt("id_acudiente"));
        guardian.setDocumentType(DocumentType.fromDb(rs.getString("tipo_documento")));
        guardian.setDocumentNumber(rs.getString("numero_documento"));
        guardian.setFirstName(rs.getString("primer_nombre"));
        guardian.setMiddleName(rs.getString("segundo_nombre"));
        guardian.setLastName(rs.getString("primer_apellido"));
        guardian.setSecondLastName(rs.getString("segundo_apellido"));
        guardian.setRelationship(rs.getString("parentesco"));
        guardian.setPrimaryPhone(rs.getString("telefono_principal"));
        guardian.setEmail(rs.getString("correo_electronico"));
        guardian.setAddress(rs.getString("direccion"));
        guardian.setOccupation(rs.getString("ocupacion"));
        return guardian;
    }
}