package com.littlehorizons.erp.view;

import com.littlehorizons.erp.util.InputValidator;

/** Main menu of the application: groups the options by module. */
public class MainMenuView {

    private static final int ATTENDANCE_IMPORT_OPTION = 8;
    private static final int EXIT_OPTION = 9;

    private final StudentView studentView;
    private final AcademicView academicView;
    private final TeacherView teacherView;
    private final ExtracurricularView extracurricularView;
    private final FinancialView financialView;
    private final ResourceView resourceView;
    private final ReportView reportView;
    private final AttendanceImportView attendanceImportView;

    public MainMenuView(StudentView studentView, AcademicView academicView, TeacherView teacherView,
                        ExtracurricularView extracurricularView, FinancialView financialView,
                        ResourceView resourceView, ReportView reportView,
                        AttendanceImportView attendanceImportView) {
        this.studentView = studentView;
        this.academicView = academicView;
        this.teacherView = teacherView;
        this.extracurricularView = extracurricularView;
        this.financialView = financialView;
        this.resourceView = resourceView;
        this.reportView = reportView;
        this.attendanceImportView = attendanceImportView;
    }

    public void run() {
        boolean exit = false;
        while (!exit) {
            printMenu();
            int option = InputValidator.readInt("Seleccione una opción: ", 1, EXIT_OPTION);
            switch (option) {
                case 1 -> studentView.run();
                case 2 -> academicView.run();
                case 3 -> teacherView.run();
                case 4 -> extracurricularView.run();
                case 5 -> financialView.run();
                case 6 -> resourceView.run();
                case 7 -> reportView.run();
                case ATTENDANCE_IMPORT_OPTION -> attendanceImportView.run();
                case EXIT_OPTION -> exit = true;
            }
        }
        System.out.println("\nGracias por usar el ERP del Colegio Little Horizons. ¡Hasta pronto!");
    }

    private void printMenu() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   ERP Colegio Little Horizons                ║");
        System.out.println("║   Sistema de Gestión Educativa               ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("1. Gestión de Estudiantes");
        System.out.println("2. Gestión Académica");
        System.out.println("3. Gestión de Profesores");
        System.out.println("4. Actividades Extracurriculares");
        System.out.println("5. Gestión Financiera");
        System.out.println("6. Gestión de Recursos");
        System.out.println("7. Reportes y Estadísticas");
        System.out.println("8. TEST - Importar Asistencias desde CSV");
        System.out.println("9. Salir");
        System.out.println();
    }
}