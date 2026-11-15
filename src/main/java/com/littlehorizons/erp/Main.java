package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.AcademicController;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.dao.AssignmentDao;
import com.littlehorizons.erp.dao.AttendanceDao;
import com.littlehorizons.erp.dao.GradeDao;
import com.littlehorizons.erp.dao.GuardianDao;
import com.littlehorizons.erp.dao.JdbcAcademicDao;
import com.littlehorizons.erp.dao.JdbcAssignmentDao;
import com.littlehorizons.erp.dao.JdbcAttendanceDao;
import com.littlehorizons.erp.dao.JdbcGradeDao;
import com.littlehorizons.erp.dao.JdbcGuardianDao;
import com.littlehorizons.erp.dao.JdbcStudentDao;
import com.littlehorizons.erp.dao.JdbcTeacherDao;
import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.dao.TeacherDao;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.view.AcademicView;
import com.littlehorizons.erp.view.MainMenuView;
import com.littlehorizons.erp.view.StudentView;
import com.littlehorizons.erp.view.TeacherView;

import java.sql.Connection;
import java.sql.SQLException;

/** Application entry point and composition root: wires DAOs, controllers and views. */
public class Main {

    public static void main(String[] args) {
        ErrorLogger.info("Application started");
        if (!isDatabaseAvailable()) {
            System.out.println("[ERROR] No se pudo conectar a la base de datos. Revise config.txt y error_log.txt.");
            return;
        }

        GuardianDao guardianDao = new JdbcGuardianDao();
        StudentDao studentDao = new JdbcStudentDao(guardianDao);
        GradeDao gradeDao = new JdbcGradeDao();
        TeacherDao teacherDao = new JdbcTeacherDao();
        AssignmentDao assignmentDao = new JdbcAssignmentDao();
        AcademicDao academicDao = new JdbcAcademicDao();
        AttendanceDao attendanceDao = new JdbcAttendanceDao();

        StudentView studentView = new StudentView(new StudentController(studentDao, guardianDao, gradeDao));
        AcademicView academicView = new AcademicView(new AcademicController(
                academicDao, attendanceDao, assignmentDao, teacherDao, studentDao, gradeDao));
        TeacherView teacherView = new TeacherView(new TeacherController(teacherDao, assignmentDao));

        new MainMenuView(studentView, academicView, teacherView).run();
        ErrorLogger.info("Application finished");
    }

    private static boolean isDatabaseAvailable() {
        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {
            return connection.isValid(2);
        } catch (SQLException | IllegalStateException e) {
            ErrorLogger.error("Database connection test failed", e);
            return false;
        }
    }
}