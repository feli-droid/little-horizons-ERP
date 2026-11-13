package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.DocumentType;
import com.littlehorizons.erp.model.Gender;
import com.littlehorizons.erp.model.Guardian;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.StudentStatus;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link StudentDao}. */
public class JdbcStudentDao implements StudentDao {

    private static final String BASE_SELECT =
            "SELECT e.id_estudiante, e.tipo_documento, e.numero_documento, e.primer_nombre, e.segundo_nombre, "
                    + "e.primer_apellido, e.segundo_apellido, e.fecha_nacimiento, e.genero, e.direccion, e.telefono, "
                    + "e.correo_electronico, e.tipo_sangre, e.alergias, e.condiciones_medicas, e.fecha_ingreso, "
                    + "e.estado, g.nombre_grado "
                    + "FROM estudiantes e "
                    + "LEFT JOIN matriculas m ON m.id_estudiante = e.id_estudiante "
                    + "AND m.anio_academico = (SELECT MAX(m2.anio_academico) FROM matriculas m2 "
                    + "WHERE m2.id_estudiante = e.id_estudiante) "
                    + "LEFT JOIN grados g ON g.id_grado = m.id_grado ";

    private static final String SELECT_BY_DOCUMENT = BASE_SELECT + "WHERE e.numero_documento = ?";

    private static final String SELECT_ALL = BASE_SELECT
            + "ORDER BY e.primer_apellido, e.segundo_apellido, e.primer_nombre";

    private static final String SEARCH = BASE_SELECT
            + "WHERE e.numero_documento LIKE ? "
            + "OR CONCAT_WS(' ', e.primer_nombre, e.segundo_nombre, e.primer_apellido, e.segundo_apellido) LIKE ? "
            + "ORDER BY e.primer_apellido, e.segundo_apellido, e.primer_nombre";

    private static final String INSERT_STUDENT =
            "INSERT INTO estudiantes (tipo_documento, numero_documento, primer_nombre, segundo_nombre, "
                    + "primer_apellido, segundo_apellido, fecha_nacimiento, genero, direccion, telefono, "
                    + "correo_electronico, tipo_sangre, alergias, condiciones_medicas, fecha_ingreso) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_ENROLLMENT =
            "INSERT INTO matriculas (id_estudiante, id_grado, anio_academico, estado_matricula, fecha_matricula) "
                    + "VALUES (?, ?, ?, 'Activo', ?)";

    private static final String UPDATE_CONTACT =
            "UPDATE estudiantes SET direccion = ?, telefono = ?, correo_electronico = ? WHERE id_estudiante = ?";

    private static final String WITHDRAW_STUDENT =
            "UPDATE estudiantes SET estado = 'Retirado' WHERE id_estudiante = ? AND estado = 'Activo'";

    private static final String WITHDRAW_ENROLLMENT =
            "UPDATE matriculas SET estado_matricula = 'Retirado', fecha_retiro = ? "
                    + "WHERE id_estudiante = ? AND estado_matricula = 'Activo'";

    private final GuardianDao guardianDao;

    public JdbcStudentDao(GuardianDao guardianDao) {
        this.guardianDao = guardianDao;
    }

    @Override
    public Optional<Student> findByDocument(String documentNumber) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_DOCUMENT)) {
            statement.setString(1, documentNumber);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not find student by document " + documentNumber, e);
            throw new DataAccessException("No se pudo consultar el estudiante.", e);
        }
    }

    @Override
    public List<Student> findAll() {
        List<Student> students = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                students.add(mapRow(rs));
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not list students", e);
            throw new DataAccessException("No se pudo consultar la lista de estudiantes.", e);
        }
        return students;
    }

    @Override
    public List<Student> search(String term) {
        List<Student> students = new ArrayList<>();
        String pattern = "%" + term + "%";
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    students.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not search students with term '" + term + "'", e);
            throw new DataAccessException("No se pudo realizar la búsqueda de estudiantes.", e);
        }
        return students;
    }

    @Override
    public int register(Student student, Guardian guardian, int gradeId, int academicYear) {
        boolean guardianIsNew = guardian.getId() == 0;
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (guardianIsNew) {
                    guardian.setId(guardianDao.insert(connection, guardian));
                }
                int studentId = insertStudent(connection, student);
                guardianDao.linkToStudent(connection, studentId, guardian.getId(), true);
                insertEnrollment(connection, studentId, gradeId, academicYear, student.getAdmissionDate());
                connection.commit();
                student.setId(studentId);
                return studentId;
            } catch (SQLException e) {
                connection.rollback();
                if (guardianIsNew) {
                    guardian.setId(0);
                }
                throw e;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not register student " + student.getDocumentNumber(), e);
            throw new DataAccessException("No se pudo registrar el estudiante. No se guardó ningún dato.", e);
        }
    }

    @Override
    public boolean updateContact(int studentId, String address, String phone, String email) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_CONTACT)) {
            statement.setString(1, address);
            statement.setString(2, phone);
            statement.setString(3, email);
            statement.setInt(4, studentId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            ErrorLogger.error("Could not update contact data of student " + studentId, e);
            throw new DataAccessException("No se pudieron actualizar los datos del estudiante.", e);
        }
    }

    @Override
    public boolean withdraw(int studentId, LocalDate withdrawalDate) {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement studentStatement = connection.prepareStatement(WITHDRAW_STUDENT);
                 PreparedStatement enrollmentStatement = connection.prepareStatement(WITHDRAW_ENROLLMENT)) {
                studentStatement.setInt(1, studentId);
                if (studentStatement.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
                enrollmentStatement.setObject(1, withdrawalDate);
                enrollmentStatement.setInt(2, studentId);
                enrollmentStatement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            ErrorLogger.error("Could not withdraw student " + studentId, e);
            throw new DataAccessException("No se pudo retirar al estudiante. No se guardó ningún cambio.", e);
        }
    }

    private int insertStudent(Connection connection, Student student) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_STUDENT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, student.getDocumentType().getDbValue());
            statement.setString(2, student.getDocumentNumber());
            statement.setString(3, student.getFirstName());
            statement.setString(4, student.getMiddleName());
            statement.setString(5, student.getLastName());
            statement.setString(6, student.getSecondLastName());
            statement.setObject(7, student.getBirthDate());
            statement.setString(8, student.getGender().getDbValue());
            statement.setString(9, student.getAddress());
            statement.setString(10, student.getPhone());
            statement.setString(11, student.getEmail());
            statement.setString(12, student.getBloodType());
            statement.setString(13, student.getAllergies());
            statement.setString(14, student.getMedicalConditions());
            statement.setObject(15, student.getAdmissionDate());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("No generated key returned for student");
            }
        }
    }

    private void insertEnrollment(Connection connection, int studentId, int gradeId, int academicYear,
                                  LocalDate enrollmentDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ENROLLMENT)) {
            statement.setInt(1, studentId);
            statement.setInt(2, gradeId);
            statement.setInt(3, academicYear);
            statement.setObject(4, enrollmentDate);
            statement.executeUpdate();
        }
    }

    private Student mapRow(ResultSet rs) throws SQLException {
        Student student = new Student();
        student.setId(rs.getInt("id_estudiante"));
        student.setDocumentType(DocumentType.fromDb(rs.getString("tipo_documento")));
        student.setDocumentNumber(rs.getString("numero_documento"));
        student.setFirstName(rs.getString("primer_nombre"));
        student.setMiddleName(rs.getString("segundo_nombre"));
        student.setLastName(rs.getString("primer_apellido"));
        student.setSecondLastName(rs.getString("segundo_apellido"));
        student.setBirthDate(rs.getDate("fecha_nacimiento").toLocalDate());
        student.setGender(Gender.fromDb(rs.getString("genero")));
        student.setAddress(rs.getString("direccion"));
        student.setPhone(rs.getString("telefono"));
        student.setEmail(rs.getString("correo_electronico"));
        student.setBloodType(rs.getString("tipo_sangre"));
        student.setAllergies(rs.getString("alergias"));
        student.setMedicalConditions(rs.getString("condiciones_medicas"));
        student.setAdmissionDate(rs.getDate("fecha_ingreso").toLocalDate());
        student.setStatus(StudentStatus.fromDb(rs.getString("estado")));
        student.setCurrentGrade(rs.getString("nombre_grado"));
        return student;
    }
}