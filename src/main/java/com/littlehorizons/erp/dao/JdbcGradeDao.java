package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.Grade;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** JDBC implementation of {@link GradeDao}. */
public class JdbcGradeDao implements GradeDao {

    private static final String SELECT_ALL =
            "SELECT id_grado, nombre_grado, nivel_numerico FROM grados ORDER BY nivel_numerico";

    @Override
    public List<Grade> findAll() {
        List<Grade> grades = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                grades.add(new Grade(rs.getInt("id_grado"), rs.getString("nombre_grado"), rs.getInt("nivel_numerico")));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load grades", e);
            throw new DataAccessException("No se pudieron consultar los grados.", e);
        }
        return grades;
    }
}