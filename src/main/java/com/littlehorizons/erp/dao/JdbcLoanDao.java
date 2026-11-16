package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.BorrowerType;
import com.littlehorizons.erp.model.Loan;
import com.littlehorizons.erp.model.LoanStatus;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link LoanDao}; students and teachers have separate loan tables. */
public class JdbcLoanDao implements LoanDao {

    private static final String STUDENT_NAME = "CONCAT_WS(' ', b.primer_nombre, b.segundo_nombre, b.primer_apellido, b.segundo_apellido)";

    /** Both loan tables as one result set, so reports do not repeat the query. */
    private static final String ALL_LOANS =
            "SELECT 'Estudiante' AS tipo, p.id_prestamo, p.id_recurso, r.codigo_recurso, r.nombre_recurso, "
                    + "p.id_estudiante AS id_prestatario, " + STUDENT_NAME + " AS prestatario, b.numero_documento, "
                    + "p.fecha_prestamo, p.fecha_devolucion_esperada, p.fecha_devolucion_real, p.estado_prestamo, "
                    + "p.observaciones FROM prestamos_estudiante p JOIN recursos r ON r.id_recurso = p.id_recurso "
                    + "JOIN estudiantes b ON b.id_estudiante = p.id_estudiante "
                    + "UNION ALL "
                    + "SELECT 'Profesor' AS tipo, p.id_prestamo, p.id_recurso, r.codigo_recurso, r.nombre_recurso, "
                    + "p.id_profesor AS id_prestatario, " + STUDENT_NAME + " AS prestatario, b.numero_documento, "
                    + "p.fecha_prestamo, p.fecha_devolucion_esperada, p.fecha_devolucion_real, p.estado_prestamo, "
                    + "p.observaciones FROM prestamos_profesor p JOIN recursos r ON r.id_recurso = p.id_recurso "
                    + "JOIN profesores b ON b.id_profesor = p.id_profesor";

    private static final String LOANS = "SELECT * FROM (" + ALL_LOANS + ") loans ";

    @Override
    public boolean lend(BorrowerType type, int borrowerId, int resourceId, LocalDate loanDate, LocalDate dueDate,
                        String observations) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!"Disponible".equals(lockResourceStatus(connection, resourceId))) {
                    connection.rollback();
                    return false;
                }
                insertLoan(connection, type, borrowerId, resourceId, loanDate, dueDate, observations);
                setResourceStatus(connection, resourceId, "Prestado");
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not lend resource " + resourceId + " to " + type + " " + borrowerId, e);
            throw new DataAccessException("No se pudo registrar el préstamo. No se guardó ningún cambio.", e);
        }
    }

    @Override
    public boolean registerReturn(BorrowerType type, int loanId, LocalDate returnDate, String observations) {
        String table = table(type);
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                int resourceId = lockOpenLoan(connection, table, loanId);
                if (resourceId == 0) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE " + table
                        + " SET estado_prestamo = 'Devuelto', fecha_devolucion_real = ?, "
                        + "observaciones = COALESCE(?, observaciones) WHERE id_prestamo = ?")) {
                    update.setObject(1, returnDate);
                    update.setString(2, observations);
                    update.setInt(3, loanId);
                    update.executeUpdate();
                }
                setResourceStatus(connection, resourceId, "Disponible");
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not register return of loan " + loanId, e);
            throw new DataAccessException("No se pudo registrar la devolución. No se guardó ningún cambio.", e);
        }
    }

    @Override
    public int markOverdue(LocalDate today) {
        int changed = 0;
        for (BorrowerType type : BorrowerType.values()) {
            String sql = "UPDATE " + table(type) + " SET estado_prestamo = 'Vencido' "
                    + "WHERE estado_prestamo = 'Activo' AND fecha_devolucion_esperada < ?";
            try (Connection connection = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, today);
                changed += statement.executeUpdate();
            } catch (SQLException e) {
                ErrorLogger.error("Could not mark overdue loans of " + type, e);
                throw new DataAccessException("No se pudo actualizar los préstamos vencidos.", e);
            }
        }
        return changed;
    }

    @Override
    public Optional<Loan> findOpenByResource(int resourceId) {
        List<Loan> loans = query(LOANS + "WHERE id_recurso = ? AND estado_prestamo <> 'Devuelto' "
                + "ORDER BY fecha_prestamo DESC", resourceId);
        return loans.isEmpty() ? Optional.empty() : Optional.of(loans.get(0));
    }

    @Override
    public List<Loan> findOpen() {
        return query(LOANS + "WHERE estado_prestamo <> 'Devuelto' ORDER BY fecha_devolucion_esperada, codigo_recurso");
    }

    @Override
    public List<Loan> findOverdue(LocalDate today) {
        return query(LOANS + "WHERE fecha_devolucion_real IS NULL AND fecha_devolucion_esperada < ? "
                + "ORDER BY fecha_devolucion_esperada", today);
    }

    @Override
    public List<Loan> findByBorrower(BorrowerType type, int borrowerId) {
        return query(LOANS + "WHERE tipo = ? AND id_prestatario = ? ORDER BY fecha_prestamo DESC",
                type.getLabel(), borrowerId);
    }

    @Override
    public int countOpen(BorrowerType type, int borrowerId) {
        return count("SELECT COUNT(*) FROM " + table(type) + " WHERE " + borrowerColumn(type)
                + " = ? AND estado_prestamo <> 'Devuelto'", borrowerId, null);
    }

    @Override
    public int countOverdue(BorrowerType type, int borrowerId, LocalDate today) {
        return count("SELECT COUNT(*) FROM " + table(type) + " WHERE " + borrowerColumn(type)
                + " = ? AND fecha_devolucion_real IS NULL AND fecha_devolucion_esperada < ?", borrowerId, today);
    }

    private int count(String sql, int borrowerId, LocalDate date) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, borrowerId);
            if (date != null) {
                statement.setObject(2, date);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not count loans of borrower " + borrowerId, e);
            throw new DataAccessException("No se pudo consultar los préstamos de la persona.", e);
        }
    }

    private String lockResourceStatus(Connection connection, int resourceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT estado_recurso FROM recursos WHERE id_recurso = ? FOR UPDATE")) {
            statement.setInt(1, resourceId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                throw new SQLException("Resource not found: " + resourceId);
            }
        }
    }

    private int lockOpenLoan(Connection connection, String table, int loanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id_recurso FROM " + table
                + " WHERE id_prestamo = ? AND estado_prestamo <> 'Devuelto' FOR UPDATE")) {
            statement.setInt(1, loanId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void insertLoan(Connection connection, BorrowerType type, int borrowerId, int resourceId,
                            LocalDate loanDate, LocalDate dueDate, String observations) throws SQLException {
        String sql = "INSERT INTO " + table(type) + " (id_recurso, " + borrowerColumn(type)
                + ", fecha_prestamo, fecha_devolucion_esperada, observaciones) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, resourceId);
            statement.setInt(2, borrowerId);
            statement.setObject(3, loanDate);
            statement.setObject(4, dueDate);
            statement.setString(5, observations);
            statement.executeUpdate();
        }
    }

    private void setResourceStatus(Connection connection, int resourceId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE recursos SET estado_recurso = ? WHERE id_recurso = ?")) {
            statement.setString(1, status);
            statement.setInt(2, resourceId);
            statement.executeUpdate();
        }
    }

    private String table(BorrowerType type) {
        return type == BorrowerType.STUDENT ? "prestamos_estudiante" : "prestamos_profesor";
    }

    private String borrowerColumn(BorrowerType type) {
        return type == BorrowerType.STUDENT ? "id_estudiante" : "id_profesor";
    }

    private List<Loan> query(String sql, Object... params) {
        List<Loan> loans = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    loans.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not query loans", e);
            throw new DataAccessException("No se pudo consultar los préstamos.", e);
        }
        return loans;
    }

    private Loan mapRow(ResultSet rs) throws SQLException {
        Date returnDate = rs.getDate("fecha_devolucion_real");
        BorrowerType type = "Estudiante".equals(rs.getString("tipo")) ? BorrowerType.STUDENT : BorrowerType.TEACHER;
        return new Loan(rs.getInt("id_prestamo"), type, rs.getInt("id_recurso"), rs.getString("codigo_recurso"),
                rs.getString("nombre_recurso"), rs.getInt("id_prestatario"), rs.getString("prestatario"),
                rs.getString("numero_documento"), rs.getDate("fecha_prestamo").toLocalDate(),
                rs.getDate("fecha_devolucion_esperada").toLocalDate(),
                returnDate == null ? null : returnDate.toLocalDate(),
                LoanStatus.fromDb(rs.getString("estado_prestamo")), rs.getString("observaciones"));
    }
}