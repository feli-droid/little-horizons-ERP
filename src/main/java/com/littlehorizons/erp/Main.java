package com.littlehorizons.erp;

import com.littlehorizons.erp.controller.AcademicController;
import com.littlehorizons.erp.controller.ClubController;
import com.littlehorizons.erp.controller.EventController;
import com.littlehorizons.erp.controller.InvoiceController;
import com.littlehorizons.erp.controller.LoanController;
import com.littlehorizons.erp.controller.PaymentController;
import com.littlehorizons.erp.controller.ReportController;
import com.littlehorizons.erp.controller.ResourceController;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.dao.AssignmentDao;
import com.littlehorizons.erp.dao.AttendanceDao;
import com.littlehorizons.erp.dao.ClubDao;
import com.littlehorizons.erp.dao.EventDao;
import com.littlehorizons.erp.dao.GradeDao;
import com.littlehorizons.erp.dao.GuardianDao;
import com.littlehorizons.erp.dao.InvoiceDao;
import com.littlehorizons.erp.dao.LoanDao;
import com.littlehorizons.erp.dao.PaymentDao;
import com.littlehorizons.erp.dao.JdbcReportDao;
import com.littlehorizons.erp.dao.ReportDao;
import com.littlehorizons.erp.dao.ResourceDao;
import com.littlehorizons.erp.dao.JdbcAcademicDao;
import com.littlehorizons.erp.dao.JdbcAssignmentDao;
import com.littlehorizons.erp.dao.JdbcAttendanceDao;
import com.littlehorizons.erp.dao.JdbcClubDao;
import com.littlehorizons.erp.dao.JdbcEventDao;
import com.littlehorizons.erp.dao.JdbcGradeDao;
import com.littlehorizons.erp.dao.JdbcGuardianDao;
import com.littlehorizons.erp.dao.JdbcInvoiceDao;
import com.littlehorizons.erp.dao.JdbcLoanDao;
import com.littlehorizons.erp.dao.JdbcPaymentDao;
import com.littlehorizons.erp.dao.JdbcResourceDao;
import com.littlehorizons.erp.dao.JdbcStudentDao;
import com.littlehorizons.erp.dao.JdbcTeacherDao;
import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.dao.TeacherDao;
import com.littlehorizons.erp.util.DatabaseConnection;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.view.AcademicView;
import com.littlehorizons.erp.view.ClubView;
import com.littlehorizons.erp.view.EventView;
import com.littlehorizons.erp.view.ExtracurricularView;
import com.littlehorizons.erp.view.FinancialView;
import com.littlehorizons.erp.view.MainMenuView;
import com.littlehorizons.erp.view.ReportView;
import com.littlehorizons.erp.view.ResourceView;
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
        ClubDao clubDao = new JdbcClubDao();
        EventDao eventDao = new JdbcEventDao();
        ResourceDao resourceDao = new JdbcResourceDao();
        LoanDao loanDao = new JdbcLoanDao();
        PaymentDao paymentDao = new JdbcPaymentDao();
        InvoiceDao invoiceDao = new JdbcInvoiceDao();

        StudentView studentView = new StudentView(new StudentController(studentDao, guardianDao, gradeDao));
        AcademicView academicView = new AcademicView(new AcademicController(
                academicDao, attendanceDao, assignmentDao, teacherDao, studentDao, gradeDao));
        TeacherView teacherView = new TeacherView(new TeacherController(teacherDao, assignmentDao));

        ExtracurricularView extracurricularView = new ExtracurricularView(
                new ClubView(new ClubController(clubDao, teacherDao, studentDao)),
                new EventView(new EventController(eventDao, teacherDao, studentDao)));

        InvoiceController invoiceController = new InvoiceController(invoiceDao);
        FinancialView financialView = new FinancialView(
                new PaymentController(paymentDao, invoiceController, studentDao, guardianDao), invoiceController);
        ResourceView resourceView = new ResourceView(new ResourceController(resourceDao),
                new LoanController(loanDao, resourceDao, studentDao, teacherDao));

        ReportDao reportDao = new JdbcReportDao();
        ReportView reportView = new ReportView(new ReportController(reportDao));

        new MainMenuView(studentView, academicView, teacherView, extracurricularView, financialView,
                resourceView, reportView).run();
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