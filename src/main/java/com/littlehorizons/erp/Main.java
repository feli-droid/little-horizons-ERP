package com.littlehorizons.erp;

import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        String sql = "SELECT COUNT(*) FROM estudiantes";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                System.out.println("Conexión exitosa. Estudiantes: " + rs.getInt(1));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Connection test failed", e);
            System.out.println("[ERROR] No se pudo conectar a la base de datos.");
        }
    }
}