package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.AttendanceStat;
import com.littlehorizons.erp.model.ClubOccupancy;
import com.littlehorizons.erp.model.MonthlyIncome;
import com.littlehorizons.erp.model.StudentRanking;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC implementation of {@link ReportDao}. */
public class JdbcReportDao implements ReportDao {

    private static final String SELECT_AVERAGES =
            "SELECT e.id_estudiante, e.numero_documento, "
                    + "CONCAT_WS(' ', e.primer_nombre, e.segundo_nombre, e.primer_apellido, e.segundo_apellido) AS nombre, "
                    + "g.nombre_grado, ROUND(AVG(r.valor_nota), 2) AS promedio, COUNT(*) AS notas "
                    + "FROM registros_academicos r "
                    + "JOIN estudiantes e ON e.id_estudiante = r.id_estudiante "
                    + "JOIN matriculas m ON m.id_estudiante = e.id_estudiante AND m.anio_academico = r.anio_academico "
                    + "AND m.estado_matricula = 'Activo' "
                    + "JOIN grados g ON g.id_grado = m.id_grado "
                    + "WHERE r.anio_academico = ? AND e.estado = 'Activo' "
                    + "GROUP BY e.id_estudiante, e.numero_documento, nombre, g.nombre_grado, g.nivel_numerico "
                    + "ORDER BY promedio DESC, nombre";

    private static final String SELECT_ATTENDANCE =
            "SELECT g.nombre_grado, COUNT(*) AS total, "
                    + "SUM(CASE WHEN a.estado_asistencia IN ('Presente', 'Tardanza') THEN 1 ELSE 0 END) AS asistio "
                    + "FROM asistencias a "
                    + "JOIN materias ma ON ma.id_materia = a.id_materia "
                    + "JOIN grados g ON g.id_grado = ma.id_grado "
                    + "GROUP BY g.id_grado, g.nombre_grado, g.nivel_numerico ORDER BY g.nivel_numerico";

    private static final String SELECT_CLUBS =
            "SELECT c.nombre_club, CONCAT_WS(' ', p.primer_nombre, p.primer_apellido) AS supervisor, "
                    + "c.capacidad_maxima, COUNT(mc.id_membresia) AS miembros "
                    + "FROM clubes c "
                    + "LEFT JOIN profesores p ON p.id_profesor = c.id_profesor_supervisor "
                    + "LEFT JOIN membresias_club mc ON mc.id_club = c.id_club AND mc.estado_membresia = 'Activo' "
                    + "WHERE c.esta_activo = 1 "
                    + "GROUP BY c.id_club, c.nombre_club, supervisor, c.capacidad_maxima ORDER BY c.nombre_club";

    private static final String SELECT_INCOME =
            "SELECT DATE_FORMAT(fecha_pago, '%Y-%m') AS mes, COUNT(*) AS pagos, SUM(monto_pagado) AS total "
                    + "FROM pagos WHERE YEAR(fecha_pago) = ? GROUP BY mes ORDER BY mes";

    private static final String SELECT_RESOURCES =
            "SELECT estado_recurso, COUNT(*) FROM recursos GROUP BY estado_recurso ORDER BY estado_recurso";

    private static final String SELECT_COUNTERS =
            "SELECT 'Estudiantes activos', COUNT(*) FROM estudiantes WHERE estado = 'Activo' "
                    + "UNION ALL SELECT 'Acudientes', COUNT(*) FROM acudientes "
                    + "UNION ALL SELECT 'Profesores activos', COUNT(*) FROM profesores WHERE estado = 'Activo' "
                    + "UNION ALL SELECT 'Materias activas', COUNT(*) FROM materias WHERE esta_activa = 1 "
                    + "UNION ALL SELECT 'Clubes activos', COUNT(*) FROM clubes WHERE esta_activo = 1 "
                    + "UNION ALL SELECT 'Eventos', COUNT(*) FROM eventos "
                    + "UNION ALL SELECT 'Recursos', COUNT(*) FROM recursos "
                    + "UNION ALL SELECT 'Préstamos vencidos', "
                    + "(SELECT COUNT(*) FROM prestamos_estudiante WHERE estado_prestamo = 'Vencido') "
                    + "+ (SELECT COUNT(*) FROM prestamos_profesor WHERE estado_prestamo = 'Vencido') "
                    + "UNION ALL SELECT 'Facturas emitidas', COUNT(*) FROM facturas";

    @Override
    public List<StudentRanking> findStudentAverages(int year) {
        List<StudentRanking> result = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_AVERAGES)) {
            statement.setInt(1, year);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new StudentRanking(rs.getInt("id_estudiante"), rs.getString("numero_documento"),
                            rs.getString("nombre"), rs.getString("nombre_grado"), rs.getBigDecimal("promedio"),
                            rs.getInt("notas")));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load student averages", e);
            throw new DataAccessException("No se pudieron consultar los promedios.", e);
        }
        return result;
    }

    @Override
    public List<AttendanceStat> findAttendanceByGrade() {
        List<AttendanceStat> result = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ATTENDANCE);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.add(new AttendanceStat(rs.getString("nombre_grado"), rs.getLong("total"), rs.getLong("asistio")));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load attendance statistics", e);
            throw new DataAccessException("No se pudo consultar la asistencia.", e);
        }
        return result;
    }

    @Override
    public List<ClubOccupancy> findClubOccupancy() {
        List<ClubOccupancy> result = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CLUBS);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String supervisor = rs.getString("supervisor");
                result.add(new ClubOccupancy(rs.getString("nombre_club"), supervisor == null ? "-" : supervisor,
                        rs.getInt("capacidad_maxima"), rs.getInt("miembros")));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load club occupancy", e);
            throw new DataAccessException("No se pudo consultar la ocupación de los clubes.", e);
        }
        return result;
    }

    @Override
    public List<MonthlyIncome> findMonthlyIncome(int year) {
        List<MonthlyIncome> result = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_INCOME)) {
            statement.setInt(1, year);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new MonthlyIncome(rs.getString("mes"), rs.getInt("pagos"), rs.getBigDecimal("total")));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load monthly income", e);
            throw new DataAccessException("No se pudieron consultar los ingresos.", e);
        }
        return result;
    }

    @Override
    public Map<String, Long> countResourcesByStatus() {
        return readCounters(SELECT_RESOURCES, "No se pudo consultar el inventario.");
    }

    @Override
    public Map<String, Long> findGeneralCounters() {
        return readCounters(SELECT_COUNTERS, "No se pudieron consultar los totales generales.");
    }

    private Map<String, Long> readCounters(String sql, String userMessage) {
        Map<String, Long> counters = new LinkedHashMap<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                counters.put(rs.getString(1), rs.getLong(2));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Counter query failed: " + userMessage, e);
            throw new DataAccessException(userMessage, e);
        }
        return counters;
    }
}