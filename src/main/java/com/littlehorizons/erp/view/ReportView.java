package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.ReportController;
import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.AttendanceStat;
import com.littlehorizons.erp.model.ClubOccupancy;
import com.littlehorizons.erp.model.MonthlyIncome;
import com.littlehorizons.erp.model.StudentRanking;
import com.littlehorizons.erp.util.InputValidator;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Console menu of the reports and statistics module (read-only). */
public class ReportView {

    private static final int EXIT_OPTION = 0;

    private final ReportController reportController;

    public ReportView(ReportController reportController) {
        this.reportController = reportController;
    }

    public void run() {
        boolean back = false;
        while (!back) {
            printMenu();
            int option = InputValidator.readInt("Seleccione una opción: ", 0, 8);
            try {
                switch (option) {
                    case 1 -> showGeneralSummary();
                    case 2 -> showTopStudents();
                    case 3 -> showStudentsAtRisk();
                    case 4 -> showAverageByGrade();
                    case 5 -> showAttendance();
                    case 6 -> showClubOccupancy();
                    case 7 -> showIncome();
                    case 8 -> showResources();
                    default -> back = option == EXIT_OPTION;
                }
            } catch (DataAccessException e) {
                System.out.println("\n[ERROR] " + e.getMessage());
            }
            if (!back) {
                InputValidator.waitForEnter();
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   REPORTES Y ESTADÍSTICAS                    ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("1. Resumen general del colegio");
        System.out.println("2. Mejores estudiantes (ranking por promedio)");
        System.out.println("3. Estudiantes en riesgo académico (promedio < 3.0)");
        System.out.println("4. Promedio académico por grado");
        System.out.println("5. Asistencia por grado");
        System.out.println("6. Ocupación de los clubes");
        System.out.println("7. Ingresos por mes");
        System.out.println("8. Estado del inventario de recursos");
        System.out.println("0. Volver al menú principal");
        System.out.println();
    }

    private void showGeneralSummary() {
        System.out.println("\nResumen general del colegio:");
        reportController.generalCounters().forEach((name, value) -> System.out.printf("  %-24s %d%n", name, value));
    }

    private void showTopStudents() {
        int year = reportController.currentYear();
        List<StudentRanking> top = reportController.topStudents(year, reportController.defaultTop());
        System.out.println("\nMejores estudiantes del año " + year + ":");
        if (top.isEmpty()) {
            System.out.println("  No hay notas registradas para este año.");
            return;
        }
        System.out.printf("%-4s %-12s %-34s %-14s %s%n", "#", "Documento", "Estudiante", "Grado", "Promedio");
        System.out.println("-".repeat(78));
        for (int i = 0; i < top.size(); i++) {
            StudentRanking s = top.get(i);
            System.out.printf("%-4d %-12s %-34s %-14s %s%n", i + 1, s.documentNumber(), s.studentName(), s.gradeName(),
                    s.average());
        }
    }

    private void showStudentsAtRisk() {
        int year = reportController.currentYear();
        List<StudentRanking> risk = reportController.studentsAtRisk(year);
        System.out.println("\nEstudiantes con promedio inferior a 3.0 en " + year + ":");
        if (risk.isEmpty()) {
            System.out.println("  Ningún estudiante está en riesgo académico.");
            return;
        }
        System.out.printf("%-12s %-34s %-14s %s%n", "Documento", "Estudiante", "Grado", "Promedio");
        System.out.println("-".repeat(70));
        risk.forEach(s -> System.out.printf("%-12s %-34s %-14s %s%n", s.documentNumber(), s.studentName(),
                s.gradeName(), s.average()));
        System.out.println("\nTotal en riesgo: " + risk.size());
    }

    private void showAverageByGrade() {
        int year = reportController.currentYear();
        Map<String, BigDecimal> averages = reportController.averageByGrade(year);
        System.out.println("\nPromedio académico por grado en " + year + ":");
        if (averages.isEmpty()) {
            System.out.println("  No hay notas registradas para este año.");
            return;
        }
        averages.forEach((grade, average) -> System.out.printf("  %-16s %s%n", grade, average));
    }

    private void showAttendance() {
        List<AttendanceStat> stats = reportController.attendanceByGrade();
        System.out.println("\nAsistencia por grado (presente o tardanza sobre el total de registros):");
        if (stats.isEmpty()) {
            System.out.println("  No hay asistencia registrada.");
            return;
        }
        System.out.printf("%-16s %-10s %-10s %s%n", "Grado", "Registros", "Asistió", "% asistencia");
        System.out.println("-".repeat(52));
        stats.forEach(s -> System.out.printf("%-16s %-10d %-10d %.1f%%%n", s.gradeName(), s.totalRecords(),
                s.attended(), s.percentage()));
    }

    private void showClubOccupancy() {
        List<ClubOccupancy> clubs = reportController.clubOccupancy();
        System.out.println("\nOcupación de los clubes activos:");
        if (clubs.isEmpty()) {
            System.out.println("  No hay clubes activos.");
            return;
        }
        System.out.printf("%-30s %-22s %-10s %-10s %-8s %s%n", "Club", "Supervisor", "Capacidad", "Miembros", "Libres",
                "Ocupación");
        System.out.println("-".repeat(96));
        clubs.forEach(c -> System.out.printf("%-30s %-22s %-10d %-10d %-8d %.0f%%%n", c.clubName(), c.supervisorName(),
                c.capacity(), c.activeMembers(), c.freeSpots(), c.occupancyPercent()));
    }

    private void showIncome() {
        int year = reportController.currentYear();
        List<MonthlyIncome> months = reportController.monthlyIncome(year);
        System.out.println("\nIngresos por mes en " + year + ":");
        if (months.isEmpty()) {
            System.out.println("  No hay pagos registrados en este año.");
            return;
        }
        System.out.printf("%-10s %-8s %s%n", "Mes", "Pagos", "Total recaudado");
        System.out.println("-".repeat(40));
        months.forEach(m -> System.out.printf("%-10s %-8d %s%n", m.month(), m.payments(), Money.format(m.total())));
        System.out.println("\nTotal del año: " + Money.format(reportController.totalIncome(months)));
    }

    private void showResources() {
        System.out.println("\nInventario de recursos por estado:");
        Map<String, Long> counts = reportController.resourcesByStatus();
        counts.forEach((status, total) -> System.out.printf("  %-20s %d%n", status, total));
        System.out.println("  " + "-".repeat(24));
        System.out.printf("  %-20s %d%n", "Total", counts.values().stream().mapToLong(Long::longValue).sum());
    }
}