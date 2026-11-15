package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.AcademicRecord;
import com.littlehorizons.erp.model.EnrolledStudent;
import com.littlehorizons.erp.model.StudentScore;
import com.littlehorizons.erp.model.SubjectAverage;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link AcademicDao}. */
public class JdbcAcademicDao implements AcademicDao {

    private static final String FULL_NAME = "CONCAT_WS(' ', e.primer_nombre, e.segundo_nombre, "
            + "e.primer_apellido, e.segundo_apellido)";

    private static final String RECORD_SELECT =
            "SELECT r.id_registro, r.id_estudiante, r.id_materia, m.nombre_materia, g.nombre_grado, "
                    + "r.id_profesor, r.anio_academico, r.periodo_academico, r.valor_nota, r.observaciones, "
                    + "r.fecha_registro "
                    + "FROM registros_academicos r "
                    + "JOIN materias m ON m.id_materia = r.id_materia "
                    + "JOIN grados g ON g.id_grado = m.id_grado ";

    private static final String ENROLLED =
            "SELECT e.id_estudiante, e.numero_documento, " + FULL_NAME + " AS nombre FROM estudiantes e "
                    + "WHERE e.estado = 'Activo' AND EXISTS (SELECT 1 FROM inscripciones_materia i "
                    + "WHERE i.id_estudiante = e.id_estudiante AND i.id_materia = ? AND i.anio_academico = ? "
                    + "AND i.estado IN ('Activa', 'Completada')) "
                    + "ORDER BY e.primer_apellido, e.segundo_apellido, e.primer_nombre";

    private static final String BELOW =
            "SELECT e.id_estudiante, e.numero_documento, " + FULL_NAME + " AS nombre, r.valor_nota "
                    + "FROM registros_academicos r JOIN estudiantes e ON e.id_estudiante = r.id_estudiante "
                    + "WHERE r.id_materia = ? AND r.anio_academico = ? AND r.periodo_academico = ? "
                    + "AND r.valor_nota < ? ORDER BY r.valor_nota, e.primer_apellido";

    @Override
    public List<EnrolledStudent> findEnrolledStudents(int subjectId, int year) {
        List<EnrolledStudent> students = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(ENROLLED)) {
            statement.setInt(1, subjectId);
            statement.setInt(2, year);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    students.add(new EnrolledStudent(rs.getInt("id_estudiante"),
                            rs.getString("numero_documento"), rs.getString("nombre")));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list students enrolled in subject " + subjectId, e);
            throw new DataAccessException("No se pudo consultar los estudiantes de la materia.", e);
        }
        return students;
    }

    @Override
    public Optional<AcademicRecord> findScore(int studentId, int subjectId, int year, AcademicPeriod period) {
        String sql = RECORD_SELECT + "WHERE r.id_estudiante = ? AND r.id_materia = ? "
                + "AND r.anio_academico = ? AND r.periodo_academico = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, subjectId);
            statement.setInt(3, year);
            statement.setString(4, period.getDbValue());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRecord(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find score of student " + studentId, e);
            throw new DataAccessException("No se pudo consultar la nota.", e);
        }
    }

    @Override
    public void insertScore(int studentId, int subjectId, int teacherId, int year, AcademicPeriod period,
                            BigDecimal score, String observations, LocalDate recordDate) {
        String sql = "INSERT INTO registros_academicos (id_estudiante, id_materia, id_profesor, anio_academico, "
                + "periodo_academico, valor_nota, observaciones, fecha_registro) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, subjectId);
            statement.setInt(3, teacherId);
            statement.setInt(4, year);
            statement.setString(5, period.getDbValue());
            statement.setBigDecimal(6, score);
            statement.setString(7, observations);
            statement.setObject(8, recordDate);
            statement.executeUpdate();
        } catch (SQLException e) {
            ErrorLogger.error("Could not insert score of student " + studentId, e);
            throw new DataAccessException("No se pudo guardar la nota.", e);
        }
    }

    @Override
    public void updateScore(int recordId, int teacherId, BigDecimal score, String observations, LocalDate recordDate) {
        String sql = "UPDATE registros_academicos SET valor_nota = ?, observaciones = ?, id_profesor = ?, "
                + "fecha_registro = ? WHERE id_registro = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, score);
            statement.setString(2, observations);
            statement.setInt(3, teacherId);
            statement.setObject(4, recordDate);
            statement.setInt(5, recordId);
            statement.executeUpdate();
        } catch (SQLException e) {
            ErrorLogger.error("Could not update score record " + recordId, e);
            throw new DataAccessException("No se pudo actualizar la nota.", e);
        }
    }

    @Override
    public List<AcademicRecord> findHistory(int studentId) {
        List<AcademicRecord> records = new ArrayList<>();
        String sql = RECORD_SELECT + "WHERE r.id_estudiante = ? "
                + "ORDER BY r.anio_academico, m.nombre_materia, r.periodo_academico";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRecord(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not load history of student " + studentId, e);
            throw new DataAccessException("No se pudo consultar el historial académico.", e);
        }
        return records;
    }

    @Override
    public List<SubjectAverage> findStudentSubjectAverages(int studentId, int year) {
        String sql = "SELECT m.nombre_materia, AVG(r.valor_nota) AS promedio, COUNT(*) AS cantidad "
                + "FROM registros_academicos r JOIN materias m ON m.id_materia = r.id_materia "
                + "WHERE r.id_estudiante = ? AND r.anio_academico = ? AND r.periodo_academico <> 'Final' "
                + "GROUP BY m.id_materia, m.nombre_materia ORDER BY m.nombre_materia";
        return findAverages(sql, studentId, year);
    }

    @Override
    public Optional<BigDecimal> findStudentPeriodAverage(int studentId, int year, AcademicPeriod period) {
        String sql = "SELECT AVG(valor_nota) FROM registros_academicos "
                + "WHERE id_estudiante = ? AND anio_academico = ? AND periodo_academico = ?";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, year);
            statement.setString(3, period.getDbValue());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getBigDecimal(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not compute period average of student " + studentId, e);
            throw new DataAccessException("No se pudo calcular el promedio del estudiante.", e);
        }
    }

    @Override
    public List<SubjectAverage> findCourseAverages(int gradeId, int year, AcademicPeriod period) {
        String sql = "SELECT m.nombre_materia, AVG(r.valor_nota) AS promedio, COUNT(*) AS cantidad "
                + "FROM registros_academicos r JOIN materias m ON m.id_materia = r.id_materia "
                + "WHERE m.id_grado = ? AND r.anio_academico = ? AND r.periodo_academico = ? "
                + "GROUP BY m.id_materia, m.nombre_materia ORDER BY m.nombre_materia";
        List<SubjectAverage> averages = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, gradeId);
            statement.setInt(2, year);
            statement.setString(3, period.getDbValue());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    averages.add(mapAverage(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not compute course averages of grade " + gradeId, e);
            throw new DataAccessException("No se pudo calcular el promedio del curso.", e);
        }
        return averages;
    }

    @Override
    public List<StudentScore> findStudentsBelow(int subjectId, int year, AcademicPeriod period, BigDecimal threshold) {
        List<StudentScore> students = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(BELOW)) {
            statement.setInt(1, subjectId);
            statement.setInt(2, year);
            statement.setString(3, period.getDbValue());
            statement.setBigDecimal(4, threshold);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    students.add(new StudentScore(new EnrolledStudent(rs.getInt("id_estudiante"),
                            rs.getString("numero_documento"), rs.getString("nombre")), rs.getBigDecimal("valor_nota")));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list low scores of subject " + subjectId, e);
            throw new DataAccessException("No se pudo consultar los estudiantes con bajo rendimiento.", e);
        }
        return students;
    }

    private List<SubjectAverage> findAverages(String sql, int first, int second) {
        List<SubjectAverage> averages = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, first);
            statement.setInt(2, second);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    averages.add(mapAverage(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not compute averages", e);
            throw new DataAccessException("No se pudo calcular los promedios.", e);
        }
        return averages;
    }

    private SubjectAverage mapAverage(ResultSet rs) throws SQLException {
        return new SubjectAverage(rs.getString("nombre_materia"), rs.getBigDecimal("promedio"), rs.getLong("cantidad"));
    }

    private AcademicRecord mapRecord(ResultSet rs) throws SQLException {
        return new AcademicRecord(rs.getInt("id_registro"), rs.getInt("id_estudiante"), rs.getInt("id_materia"),
                rs.getString("nombre_materia"), rs.getString("nombre_grado"), rs.getInt("id_profesor"),
                rs.getInt("anio_academico"), AcademicPeriod.fromDb(rs.getString("periodo_academico")),
                rs.getBigDecimal("valor_nota"), rs.getString("observaciones"),
                rs.getDate("fecha_registro").toLocalDate());
    }
}