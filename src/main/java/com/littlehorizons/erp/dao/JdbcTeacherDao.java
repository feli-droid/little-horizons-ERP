package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.DocumentType;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.model.TeacherStatus;
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

/** JDBC implementation of {@link TeacherDao}. */
public class JdbcTeacherDao implements TeacherDao {

    private static final String BASE_SELECT =
            "SELECT id_profesor, tipo_documento, numero_documento, primer_nombre, segundo_nombre, "
                    + "primer_apellido, segundo_apellido, correo_electronico, telefono, especializacion, "
                    + "fecha_contratacion, estado FROM profesores ";

    private static final String ORDER = "ORDER BY primer_apellido, segundo_apellido, primer_nombre";

    private static final String INSERT =
            "INSERT INTO profesores (tipo_documento, numero_documento, primer_nombre, segundo_nombre, "
                    + "primer_apellido, segundo_apellido, correo_electronico, telefono, especializacion, "
                    + "fecha_contratacion, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public Optional<Teacher> findByDocument(String documentNumber) {
        return findOne(BASE_SELECT + "WHERE numero_documento = ?", documentNumber);
    }

    @Override
    public Optional<Teacher> findByEmail(String email) {
        return findOne(BASE_SELECT + "WHERE correo_electronico = ?", email);
    }

    @Override
    public List<Teacher> findAll() {
        List<Teacher> teachers = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(BASE_SELECT + ORDER);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                teachers.add(mapRow(rs));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list teachers", e);
            throw new DataAccessException("No se pudo consultar la lista de profesores.", e);
        }
        return teachers;
    }

    @Override
    public List<Teacher> search(String term) {
        List<Teacher> teachers = new ArrayList<>();
        String pattern = "%" + term + "%";
        String sql = BASE_SELECT + "WHERE numero_documento LIKE ? "
                + "OR CONCAT_WS(' ', primer_nombre, segundo_nombre, primer_apellido, segundo_apellido) LIKE ? " + ORDER;
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    teachers.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not search teachers with term '" + term + "'", e);
            throw new DataAccessException("No se pudo realizar la búsqueda de profesores.", e);
        }
        return teachers;
    }

    @Override
    public int insert(Teacher teacher) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, teacher.documentType().getDbValue());
            statement.setString(2, teacher.documentNumber());
            statement.setString(3, teacher.firstName());
            statement.setString(4, teacher.middleName());
            statement.setString(5, teacher.lastName());
            statement.setString(6, teacher.secondLastName());
            statement.setString(7, teacher.email());
            statement.setString(8, teacher.phone());
            statement.setString(9, teacher.specialization());
            statement.setObject(10, teacher.hireDate());
            statement.setString(11, teacher.status().getDbValue());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for teacher");
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not insert teacher " + teacher.documentNumber(), e);
            throw new DataAccessException("No se pudo registrar el profesor.", e);
        }
    }

    @Override
    public boolean updateContact(int teacherId, String email, String phone) {
        return update("UPDATE profesores SET correo_electronico = ?, telefono = ? WHERE id_profesor = ?",
                teacherId, email, phone);
    }

    @Override
    public boolean updateStatus(int teacherId, TeacherStatus status) {
        return update("UPDATE profesores SET estado = ? WHERE id_profesor = ?", teacherId, status.getDbValue());
    }

    private boolean update(String sql, int teacherId, String... values) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String value : values) {
                statement.setString(index++, value);
            }
            statement.setInt(index, teacherId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            ErrorLogger.error("Could not update teacher " + teacherId, e);
            throw new DataAccessException("No se pudo actualizar el profesor.", e);
        }
    }

    private Optional<Teacher> findOne(String sql, String value) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find teacher with value " + value, e);
            throw new DataAccessException("No se pudo consultar el profesor.", e);
        }
    }

    private Teacher mapRow(ResultSet rs) throws SQLException {
        return new Teacher(rs.getInt("id_profesor"),
                DocumentType.fromDb(rs.getString("tipo_documento")),
                rs.getString("numero_documento"),
                rs.getString("primer_nombre"), rs.getString("segundo_nombre"),
                rs.getString("primer_apellido"), rs.getString("segundo_apellido"),
                rs.getString("correo_electronico"), rs.getString("telefono"),
                rs.getString("especializacion"),
                rs.getDate("fecha_contratacion").toLocalDate(),
                TeacherStatus.fromDb(rs.getString("estado")));
    }
}