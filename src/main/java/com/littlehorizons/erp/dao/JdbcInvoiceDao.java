package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.InstitutionSettings;
import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.InvoiceLine;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link InvoiceDao}. */
public class JdbcInvoiceDao implements InvoiceDao {

    private static final String INVOICE_SELECT =
            "SELECT f.id_factura, f.numero_factura, f.id_acudiente, "
                    + "CONCAT_WS(' ', a.primer_nombre, a.segundo_nombre, a.primer_apellido, a.segundo_apellido) AS cliente, "
                    + "a.numero_documento, a.direccion, f.fecha_emision, f.subtotal, f.iva, f.total, f.cufe, "
                    + "f.contenido_qr, f.resolucion_numero, f.nombre_archivo, f.ruta_archivo "
                    + "FROM facturas f JOIN acudientes a ON a.id_acudiente = f.id_acudiente ";

    @Override
    public InstitutionSettings findSettings() {
        String sql = "SELECT razon_social, nit, direccion, telefono, correo_electronico, prefijo_factura, "
                + "resolucion_numero, resolucion_fecha, rango_desde, rango_hasta, porcentaje_iva "
                + "FROM configuracion_institucion WHERE id_configuracion = 1";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return new InstitutionSettings(rs.getString("razon_social"), rs.getString("nit"),
                        rs.getString("direccion"), rs.getString("telefono"), rs.getString("correo_electronico"),
                        rs.getString("prefijo_factura"), rs.getString("resolucion_numero"),
                        rs.getDate("resolucion_fecha").toLocalDate(), rs.getInt("rango_desde"),
                        rs.getInt("rango_hasta"), rs.getBigDecimal("porcentaje_iva"));
            }
            throw new SQLException("configuracion_institucion has no row with id 1");
        } catch (SQLException e) {
            ErrorLogger.error("Could not load institution settings", e);
            throw new DataAccessException("No se pudo consultar la configuración del colegio.", e);
        }
    }

    @Override
    public int nextSequence(String prefix) {
        String like = prefix + "-";
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(numero_factura, ?) AS UNSIGNED)), 0) + 1 "
                + "FROM facturas WHERE numero_factura LIKE ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, like.length() + 1);
            statement.setString(2, like + "%");
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not compute next invoice number", e);
            throw new DataAccessException("No se pudo calcular el número de factura.", e);
        }
    }

    @Override
    public Optional<Invoice> findByNumber(String number) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(INVOICE_SELECT + "WHERE f.numero_factura = ?")) {
            statement.setString(1, number);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int invoiceId = rs.getInt("id_factura");
                return Optional.of(mapInvoice(rs, findLines(connection, invoiceId)));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find invoice " + number, e);
            throw new DataAccessException("No se pudo consultar la factura.", e);
        }
    }

    @Override
    public List<Invoice> findLatest(int limit) {
        List<Invoice> invoices = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(INVOICE_SELECT + "ORDER BY f.id_factura DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    invoices.add(mapInvoice(rs, List.of()));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list latest invoices", e);
            throw new DataAccessException("No se pudo consultar las facturas.", e);
        }
        return invoices;
    }

    @Override
    public void updateFileInfo(int invoiceId, String fileName, String filePath) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE facturas SET nombre_archivo = ?, ruta_archivo = ? WHERE id_factura = ?")) {
            statement.setString(1, fileName);
            statement.setString(2, filePath);
            statement.setInt(3, invoiceId);
            statement.executeUpdate();
        } catch (SQLException e) {
            ErrorLogger.error("Could not save file info of invoice " + invoiceId, e);
            throw new DataAccessException("No se pudo guardar la ruta de la factura.", e);
        }
    }

    private List<InvoiceLine> findLines(Connection connection, int invoiceId) throws SQLException {
        List<InvoiceLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT descripcion, cantidad, valor_unitario, subtotal FROM detalle_factura "
                        + "WHERE id_factura = ? ORDER BY id_detalle")) {
            statement.setInt(1, invoiceId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lines.add(new InvoiceLine(rs.getString("descripcion"), rs.getInt("cantidad"),
                            rs.getBigDecimal("valor_unitario"), rs.getBigDecimal("subtotal")));
                }
            }
        }
        return lines;
    }

    private Invoice mapInvoice(ResultSet rs, List<InvoiceLine> lines) throws SQLException {
        return new Invoice(rs.getInt("id_factura"), rs.getString("numero_factura"), rs.getInt("id_acudiente"),
                rs.getString("cliente"), rs.getString("numero_documento"), rs.getString("direccion"),
                rs.getDate("fecha_emision").toLocalDate(), rs.getBigDecimal("subtotal"), rs.getBigDecimal("iva"),
                rs.getBigDecimal("total"), rs.getString("cufe"), rs.getString("contenido_qr"),
                rs.getString("resolucion_numero"), rs.getString("nombre_archivo"), rs.getString("ruta_archivo"), lines);
    }
}