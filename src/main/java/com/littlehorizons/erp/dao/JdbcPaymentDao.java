package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.BillingPeriod;
import com.littlehorizons.erp.model.Charge;
import com.littlehorizons.erp.model.ChargeStatus;
import com.littlehorizons.erp.model.DebtorSummary;
import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.InvoiceLine;
import com.littlehorizons.erp.model.Payment;
import com.littlehorizons.erp.model.PaymentBatch;
import com.littlehorizons.erp.model.PaymentConcept;
import com.littlehorizons.erp.model.PaymentLine;
import com.littlehorizons.erp.model.PaymentMethod;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link PaymentDao}. */
public class JdbcPaymentDao implements PaymentDao {

    private static final String CONCEPT_SELECT =
            "SELECT id_concepto, codigo_concepto, nombre_concepto, descripcion, monto_base, es_recurrente, esta_activo "
                    + "FROM conceptos_pago ";

    private static final String CHARGE_SELECT =
            "SELECT c.id_cargo, c.id_estudiante, "
                    + "CONCAT_WS(' ', e.primer_nombre, e.segundo_nombre, e.primer_apellido, e.segundo_apellido) AS estudiante, "
                    + "c.id_concepto, cp.nombre_concepto, c.anio_academico, c.periodo_academico, c.valor_base, c.descuento, "
                    + "c.valor_final, c.motivo_descuento, c.fecha_vencimiento, c.estado_cargo "
                    + "FROM cargos_estudiante c JOIN estudiantes e ON e.id_estudiante = c.id_estudiante "
                    + "JOIN conceptos_pago cp ON cp.id_concepto = c.id_concepto ";

    private static final String PAYMENT_SELECT =
            "SELECT p.id_pago, p.id_estudiante, "
                    + "CONCAT_WS(' ', e.primer_nombre, e.primer_apellido, e.segundo_apellido) AS estudiante, "
                    + "p.id_acudiente, CONCAT_WS(' ', a.primer_nombre, a.primer_apellido, a.segundo_apellido) AS acudiente, "
                    + "cp.nombre_concepto, p.monto_pagado, p.fecha_pago, p.metodo_pago, p.numero_recibo, p.periodo_academico "
                    + "FROM pagos p JOIN estudiantes e ON e.id_estudiante = p.id_estudiante "
                    + "JOIN acudientes a ON a.id_acudiente = p.id_acudiente "
                    + "JOIN conceptos_pago cp ON cp.id_concepto = p.id_concepto ";

    private static final String GUARDIAN_OF_STUDENT =
            "(SELECT %s FROM estudiante_acudiente ea JOIN acudientes a ON a.id_acudiente = ea.id_acudiente "
                    + "WHERE ea.id_estudiante = e.id_estudiante ORDER BY ea.es_principal DESC, a.id_acudiente LIMIT 1)";

    private static final String DEBTORS =
            "SELECT e.id_estudiante, e.numero_documento, "
                    + "CONCAT_WS(' ', e.primer_nombre, e.segundo_nombre, e.primer_apellido, e.segundo_apellido) AS estudiante, "
                    + String.format(GUARDIAN_OF_STUDENT, "CONCAT_WS(' ', a.primer_nombre, a.primer_apellido, a.segundo_apellido)")
                    + " AS acudiente, " + String.format(GUARDIAN_OF_STUDENT, "a.telefono_principal") + " AS telefono, "
                    + "COUNT(*) AS cargos, SUM(c.valor_final) AS deuda, MIN(c.fecha_vencimiento) AS mas_antiguo "
                    + "FROM cargos_estudiante c JOIN estudiantes e ON e.id_estudiante = c.id_estudiante "
                    + "WHERE c.estado_cargo = 'Pendiente' GROUP BY e.id_estudiante "
                    + "HAVING MIN(c.fecha_vencimiento) < ? ORDER BY deuda DESC, e.primer_apellido";

    @Override
    public List<PaymentConcept> findConcepts(boolean onlyActive) {
        List<PaymentConcept> concepts = new ArrayList<>();
        String sql = CONCEPT_SELECT + (onlyActive ? "WHERE esta_activo = TRUE " : "") + "ORDER BY nombre_concepto";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                concepts.add(mapConcept(rs));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list payment concepts", e);
            throw new DataAccessException("No se pudo consultar el catálogo de conceptos.", e);
        }
        return concepts;
    }

    @Override
    public Optional<PaymentConcept> findConceptByCode(String code) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(CONCEPT_SELECT + "WHERE codigo_concepto = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapConcept(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find concept " + code, e);
            throw new DataAccessException("No se pudo consultar el concepto.", e);
        }
    }

    @Override
    public int insertConcept(String code, String name, String description, BigDecimal baseAmount, boolean recurring) {
        String sql = "INSERT INTO conceptos_pago (codigo_concepto, nombre_concepto, descripcion, monto_base, es_recurrente) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setString(3, description);
            statement.setBigDecimal(4, baseAmount);
            statement.setBoolean(5, recurring);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for concept");
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not insert concept " + code, e);
            throw new DataAccessException("No se pudo registrar el concepto de pago.", e);
        }
    }

    @Override
    public boolean updateConceptAmount(int conceptId, BigDecimal newAmount) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE conceptos_pago SET monto_base = ? WHERE id_concepto = ?")) {
            statement.setBigDecimal(1, newAmount);
            statement.setInt(2, conceptId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            ErrorLogger.error("Could not update amount of concept " + conceptId, e);
            throw new DataAccessException("No se pudo actualizar el valor del concepto.", e);
        }
    }

    @Override
    public int insertCharge(int studentId, int conceptId, int year, BillingPeriod period, BigDecimal baseValue,
                            BigDecimal discount, String discountReason, LocalDate dueDate) {
        String sql = "INSERT INTO cargos_estudiante (id_estudiante, id_concepto, anio_academico, periodo_academico, "
                + "valor_base, descuento, motivo_descuento, fecha_vencimiento) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, studentId);
            statement.setInt(2, conceptId);
            statement.setInt(3, year);
            statement.setString(4, period.getDbValue());
            statement.setBigDecimal(5, baseValue);
            statement.setBigDecimal(6, discount);
            statement.setString(7, discountReason);
            statement.setObject(8, dueDate);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for charge");
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not insert charge for student " + studentId, e);
            throw new DataAccessException("No se pudo generar el cargo.", e);
        }
    }

    @Override
    public boolean chargeExists(int studentId, int conceptId, LocalDate dueDate) {
        String sql = "SELECT COUNT(*) FROM cargos_estudiante WHERE id_estudiante = ? AND id_concepto = ? "
                + "AND fecha_vencimiento = ? AND estado_cargo <> 'Anulado'";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, conceptId);
            statement.setObject(3, dueDate);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not check existing charge for student " + studentId, e);
            throw new DataAccessException("No se pudo verificar el cargo.", e);
        }
    }

    @Override
    public List<Charge> findPendingCharges(int studentId) {
        List<Charge> charges = new ArrayList<>();
        String sql = CHARGE_SELECT + "WHERE c.id_estudiante = ? AND c.estado_cargo = 'Pendiente' "
                + "ORDER BY c.fecha_vencimiento, cp.nombre_concepto";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    charges.add(mapCharge(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list pending charges of student " + studentId, e);
            throw new DataAccessException("No se pudo consultar los cargos pendientes.", e);
        }
        return charges;
    }

    @Override
    public int nextReceiptSequence(int year) {
        String prefix = "REC-" + year + "-";
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(numero_recibo, ?) AS UNSIGNED)), 0) + 1 "
                + "FROM pagos WHERE numero_recibo LIKE ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, prefix.length() + 1);
            statement.setString(2, prefix + "%");
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not compute next receipt number", e);
            throw new DataAccessException("No se pudo calcular el número de recibo.", e);
        }
    }

    @Override
    public int registerPayments(PaymentBatch batch, Invoice invoice) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<Integer> paymentIds = new ArrayList<>();
                for (PaymentLine line : batch.lines()) {
                    if (line.chargeId() != null && !markChargePaid(connection, line.chargeId())) {
                        connection.rollback();
                        return 0;
                    }
                    paymentIds.add(insertPayment(connection, batch, line));
                }
                int invoiceId = insertInvoice(connection, invoice);
                for (int i = 0; i < paymentIds.size(); i++) {
                    insertInvoiceLine(connection, invoiceId, paymentIds.get(i), invoice.lines().get(i));
                }
                connection.commit();
                return invoiceId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not register payments for invoice " + invoice.number(), e);
            throw new DataAccessException("No se pudo registrar el pago. No se guardó ningún cambio.", e);
        }
    }

    @Override
    public List<Payment> findPaymentsByStudent(int studentId) {
        return queryPayments(PAYMENT_SELECT + "WHERE p.id_estudiante = ? ORDER BY p.fecha_pago DESC, p.id_pago DESC",
                studentId);
    }

    @Override
    public List<Payment> findPaymentsByGuardian(int guardianId) {
        List<Payment> payments = new ArrayList<>();
        String sql = PAYMENT_SELECT + "WHERE p.id_acudiente = ? OR p.id_estudiante IN "
                + "(SELECT id_estudiante FROM estudiante_acudiente WHERE id_acudiente = ?) "
                + "ORDER BY p.fecha_pago DESC, p.id_pago DESC";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, guardianId);
            statement.setInt(2, guardianId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    payments.add(mapPayment(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list payments of guardian " + guardianId, e);
            throw new DataAccessException("No se pudo consultar el historial de pagos.", e);
        }
        return payments;
    }

    @Override
    public List<DebtorSummary> findDebtors(LocalDate dueBefore, LocalDate today) {
        List<DebtorSummary> debtors = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(DEBTORS)) {
            statement.setObject(1, dueBefore);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    LocalDate oldest = rs.getDate("mas_antiguo").toLocalDate();
                    debtors.add(new DebtorSummary(rs.getInt("id_estudiante"), rs.getString("numero_documento"),
                            rs.getString("estudiante"), rs.getString("acudiente"), rs.getString("telefono"),
                            rs.getLong("cargos"), rs.getBigDecimal("deuda"), oldest,
                            ChronoUnit.DAYS.between(oldest, today)));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not build debtors report", e);
            throw new DataAccessException("No se pudo generar el reporte de morosos.", e);
        }
        return debtors;
    }

    // ------------------------------------------------------------ transaction steps

    private boolean markChargePaid(Connection connection, int chargeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE cargos_estudiante SET estado_cargo = 'Pagado' WHERE id_cargo = ? AND estado_cargo = 'Pendiente'")) {
            statement.setInt(1, chargeId);
            return statement.executeUpdate() == 1;
        }
    }

    private int insertPayment(Connection connection, PaymentBatch batch, PaymentLine line) throws SQLException {
        String sql = "INSERT INTO pagos (id_estudiante, id_acudiente, id_concepto, id_cargo, monto_pagado, fecha_pago, "
                + "metodo_pago, numero_recibo, anio_academico, periodo_academico, notas) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, line.studentId());
            statement.setInt(2, batch.guardianId());
            statement.setInt(3, line.conceptId());
            if (line.chargeId() == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, line.chargeId());
            }
            statement.setBigDecimal(5, line.amount());
            statement.setObject(6, batch.date());
            statement.setString(7, batch.method().getDbValue());
            statement.setString(8, line.receiptNumber());
            if (line.year() == null) {
                statement.setNull(9, Types.INTEGER);
            } else {
                statement.setInt(9, line.year());
            }
            statement.setString(10, line.period());
            statement.setString(11, line.notes());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for payment");
            }
        }
    }

    private int insertInvoice(Connection connection, Invoice invoice) throws SQLException {
        String sql = "INSERT INTO facturas (numero_factura, id_acudiente, fecha_emision, subtotal, iva, total, cufe, "
                + "contenido_qr, resolucion_numero) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, invoice.number());
            statement.setInt(2, invoice.guardianId());
            statement.setObject(3, invoice.issueDate());
            statement.setBigDecimal(4, invoice.subtotal());
            statement.setBigDecimal(5, invoice.vat());
            statement.setBigDecimal(6, invoice.total());
            statement.setString(7, invoice.cufe());
            statement.setString(8, invoice.qrContent());
            statement.setString(9, invoice.resolutionNumber());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for invoice");
            }
        }
    }

    private void insertInvoiceLine(Connection connection, int invoiceId, int paymentId, InvoiceLine line)
            throws SQLException {
        String sql = "INSERT INTO detalle_factura (id_factura, id_pago, descripcion, cantidad, valor_unitario, subtotal) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, invoiceId);
            statement.setInt(2, paymentId);
            statement.setString(3, line.description());
            statement.setInt(4, line.quantity());
            statement.setBigDecimal(5, line.unitValue());
            statement.setBigDecimal(6, line.subtotal());
            statement.executeUpdate();
        }
    }

    // ----------------------------------------------------------------- mapping

    private List<Payment> queryPayments(String sql, int id) {
        List<Payment> payments = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    payments.add(mapPayment(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list payments for id " + id, e);
            throw new DataAccessException("No se pudo consultar el historial de pagos.", e);
        }
        return payments;
    }

    private PaymentConcept mapConcept(ResultSet rs) throws SQLException {
        return new PaymentConcept(rs.getInt("id_concepto"), rs.getString("codigo_concepto"),
                rs.getString("nombre_concepto"), rs.getString("descripcion"), rs.getBigDecimal("monto_base"),
                rs.getBoolean("es_recurrente"), rs.getBoolean("esta_activo"));
    }

    private Charge mapCharge(ResultSet rs) throws SQLException {
        return new Charge(rs.getInt("id_cargo"), rs.getInt("id_estudiante"), rs.getString("estudiante"),
                rs.getInt("id_concepto"), rs.getString("nombre_concepto"), rs.getInt("anio_academico"),
                BillingPeriod.fromDb(rs.getString("periodo_academico")), rs.getBigDecimal("valor_base"),
                rs.getBigDecimal("descuento"), rs.getBigDecimal("valor_final"), rs.getString("motivo_descuento"),
                rs.getDate("fecha_vencimiento").toLocalDate(), ChargeStatus.fromDb(rs.getString("estado_cargo")));
    }

    private Payment mapPayment(ResultSet rs) throws SQLException {
        return new Payment(rs.getInt("id_pago"), rs.getInt("id_estudiante"), rs.getString("estudiante"),
                rs.getInt("id_acudiente"), rs.getString("acudiente"), rs.getString("nombre_concepto"),
                rs.getBigDecimal("monto_pagado"), rs.getDate("fecha_pago").toLocalDate(),
                PaymentMethod.fromDb(rs.getString("metodo_pago")), rs.getString("numero_recibo"),
                rs.getString("periodo_academico"));
    }
}