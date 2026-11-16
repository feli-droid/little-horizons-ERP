package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.Resource;
import com.littlehorizons.erp.model.ResourceCategory;
import com.littlehorizons.erp.model.ResourceStatus;
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

/** JDBC implementation of {@link ResourceDao}. */
public class JdbcResourceDao implements ResourceDao {

    private static final String BASE_SELECT =
            "SELECT r.id_recurso, r.codigo_recurso, r.nombre_recurso, r.id_categoria, c.nombre_categoria, "
                    + "r.descripcion, r.fecha_adquisicion, r.ubicacion_fisica, r.estado_recurso "
                    + "FROM recursos r JOIN categorias_recurso c ON c.id_categoria = r.id_categoria ";

    @Override
    public List<Resource> findAll() {
        return query(BASE_SELECT + "ORDER BY r.codigo_recurso");
    }

    @Override
    public Optional<Resource> findByCode(String code) {
        List<Resource> found = query(BASE_SELECT + "WHERE r.codigo_recurso = ?", code);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public List<Resource> search(String term) {
        String pattern = "%" + term + "%";
        return query(BASE_SELECT + "WHERE r.codigo_recurso LIKE ? OR r.nombre_recurso LIKE ? ORDER BY r.codigo_recurso",
                pattern, pattern);
    }

    @Override
    public List<ResourceCategory> findCategories() {
        List<ResourceCategory> categories = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id_categoria, nombre_categoria FROM categorias_recurso ORDER BY id_categoria");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                categories.add(new ResourceCategory(rs.getInt("id_categoria"), rs.getString("nombre_categoria")));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list resource categories", e);
            throw new DataAccessException("No se pudo consultar las categorías de recursos.", e);
        }
        return categories;
    }

    @Override
    public int insert(String code, String name, int categoryId, String description, LocalDate acquisitionDate,
                      String location) {
        String sql = "INSERT INTO recursos (codigo_recurso, nombre_recurso, id_categoria, descripcion, "
                + "fecha_adquisicion, ubicacion_fisica) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setInt(3, categoryId);
            statement.setString(4, description);
            statement.setObject(5, acquisitionDate);
            statement.setString(6, location);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for resource");
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not insert resource " + code, e);
            throw new DataAccessException("No se pudo registrar el recurso.", e);
        }
    }

    @Override
    public boolean updateStatus(int resourceId, ResourceStatus status) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE recursos SET estado_recurso = ? WHERE id_recurso = ?")) {
            statement.setString(1, status.getDbValue());
            statement.setInt(2, resourceId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            ErrorLogger.error("Could not update status of resource " + resourceId, e);
            throw new DataAccessException("No se pudo actualizar el estado del recurso.", e);
        }
    }

    private List<Resource> query(String sql, String... params) {
        List<Resource> resources = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    resources.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not query resources", e);
            throw new DataAccessException("No se pudo consultar el inventario.", e);
        }
        return resources;
    }

    private Resource mapRow(ResultSet rs) throws SQLException {
        Date acquisition = rs.getDate("fecha_adquisicion");
        return new Resource(rs.getInt("id_recurso"), rs.getString("codigo_recurso"), rs.getString("nombre_recurso"),
                rs.getInt("id_categoria"), rs.getString("nombre_categoria"), rs.getString("descripcion"),
                acquisition == null ? null : acquisition.toLocalDate(), rs.getString("ubicacion_fisica"),
                ResourceStatus.fromDb(rs.getString("estado_recurso")));
    }
}