package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.AttendanceImportController;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.AttendanceImportResult;
import com.littlehorizons.erp.util.InputValidator;

/** Console screen of the bulk attendance import (TEST option of the main menu). */
public class AttendanceImportView {

    private final AttendanceImportController controller;

    public AttendanceImportView(AttendanceImportController controller) {
        this.controller = controller;
    }

    public void run() {
        try {
            printReport(controller.importFromFile());
        } catch (BusinessException e) {
            System.out.println("\n" + e.getMessage());
        }
    }

    private void printReport(AttendanceImportResult result) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║           REPORTE DE IMPORTACIÓN DE ASISTENCIAS                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Total de líneas en el archivo: " + result.total());
        System.out.println("Líneas procesadas exitosamente: " + result.imported());
        System.out.println("Líneas con errores (no procesadas): " + result.failed());
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("Presione Enter para volver al menú principal...");
        System.out.println("═══════════════════════════════════════════════════════════════════");
        InputValidator.waitForEnter("");
    }
}