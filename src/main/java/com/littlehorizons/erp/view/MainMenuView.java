package com.littlehorizons.erp.view;

import com.littlehorizons.erp.util.InputValidator;

/** Main menu of the application: groups the options by module. */
public class MainMenuView {

    private static final int EXIT_OPTION = 8;

    private final StudentView studentView;
    private final AcademicView academicView;
    private final TeacherView teacherView;

    public MainMenuView(StudentView studentView, AcademicView academicView, TeacherView teacherView) {
        this.studentView = studentView;
        this.academicView = academicView;
        this.teacherView = teacherView;
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
                case EXIT_OPTION -> exit = true;
                default -> {
                    System.out.println("\n[INFO] Este módulo aún está en desarrollo.");
                    InputValidator.waitForEnter();
                }
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
        System.out.println("8. Salir");
        System.out.println();
    }
}