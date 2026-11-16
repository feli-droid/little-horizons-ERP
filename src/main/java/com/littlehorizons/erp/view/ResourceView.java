package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.LoanController;
import com.littlehorizons.erp.controller.ResourceController;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.BorrowerType;
import com.littlehorizons.erp.model.Loan;
import com.littlehorizons.erp.model.Resource;
import com.littlehorizons.erp.model.ResourceCategory;
import com.littlehorizons.erp.model.ResourceStatus;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.InputValidator;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/** Console menu for the resources inventory and loans. */
public class ResourceView {

    private static final int EXIT_OPTION = 0;

    private final ResourceController resourceController;
    private final LoanController loanController;

    public ResourceView(ResourceController resourceController, LoanController loanController) {
        this.resourceController = resourceController;
        this.loanController = loanController;
    }

    public void run() {
        boolean back = false;
        while (!back) {
            printMenu();
            int option = InputValidator.readInt("Seleccione una opción: ", 0, 9);
            try {
                switch (option) {
                    case 1 -> showInventory();
                    case 2 -> searchResources();
                    case 3 -> registerResource();
                    case 4 -> changeStatus();
                    case 5 -> lendResource();
                    case 6 -> returnResource();
                    case 7 -> printLoans("Préstamos abiertos", loanController.getOpenLoans());
                    case 8 -> showOverdueReport();
                    case 9 -> showBorrowerHistory();
                    default -> back = option == EXIT_OPTION;
                }
            } catch (BusinessException e) {
                ErrorLogger.warn("Business rule rejected: " + e.getMessage());
                System.out.println("\n[ERROR] " + e.getMessage());
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
        System.out.println("║   GESTIÓN DE RECURSOS                        ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("1. Inventario de recursos");
        System.out.println("2. Buscar recurso (código o nombre)");
        System.out.println("3. Registrar recurso");
        System.out.println("4. Cambiar estado de un recurso");
        System.out.println("5. Registrar préstamo");
        System.out.println("6. Registrar devolución");
        System.out.println("7. Préstamos abiertos");
        System.out.println("8. Reporte de préstamos vencidos");
        System.out.println("9. Historial de préstamos de una persona");
        System.out.println("0. Volver al menú principal");
    }

    // ------------------------------------------------------------------ inventory

    private void showInventory() {
        List<Resource> resources = resourceController.listResources();
        printResources(resources);
        System.out.println("\nResumen por estado:");
        resourceController.countByStatus(resources)
                .forEach((status, count) -> System.out.println("  " + status.getDbValue() + ": " + count));
    }

    private void searchResources() {
        printResources(resourceController.searchResources(InputValidator.readText("Código o nombre a buscar: ")));
    }

    private void registerResource() throws BusinessException {
        System.out.println("\n--- Registro de recurso ---");
        String code = ConsoleForm.readField("Código único (ej. LIB-013): ", resourceController::validateNewCode);
        String name = ConsoleForm.readField("Nombre: ", resourceController::validateName);
        ResourceCategory category = ConsoleForm.choose("Categoría", resourceController.listCategories(), ResourceCategory::name);
        String description = InputValidator.readOptionalText("Descripción (opcional): ");
        LocalDate acquisition = readOptionalAcquisitionDate();
        String location = InputValidator.readOptionalText("Ubicación física (opcional): ");
        System.out.println("\nResumen: " + code.toUpperCase() + " - " + name + " (" + category.name() + ")");
        if (!InputValidator.confirm("¿Confirma el registro?")) {
            System.out.println("[INFO] Registro cancelado.");
            return;
        }
        int id = resourceController.registerResource(code, name, category, description, acquisition, location);
        System.out.println("\n[OK] Recurso registrado con ID " + id + " en estado Disponible.");
    }

    private void changeStatus() throws BusinessException {
        Resource resource = resourceController.getByCode(InputValidator.readText("Código del recurso: "));
        System.out.println(resource.name() + " | Estado actual: " + resource.status().getDbValue());
        ResourceStatus status = ConsoleForm.choose("Nuevo estado", List.of(ResourceStatus.AVAILABLE,
                ResourceStatus.IN_MAINTENANCE, ResourceStatus.DISCARDED), ResourceStatus::getDbValue);
        if (!InputValidator.confirm("¿Cambiar a " + status.getDbValue() + "?")) {
            System.out.println("[INFO] Operación cancelada.");
            return;
        }
        resourceController.changeStatus(resource, status);
        System.out.println("\n[OK] Estado actualizado.");
    }

    // ---------------------------------------------------------------------- loans

    private void lendResource() throws BusinessException {
        System.out.println("\n--- Registro de préstamo ---");
        String code = InputValidator.readText("Código del recurso: ");
        BorrowerType type = ConsoleForm.choose("¿Quién lo solicita?", Arrays.asList(BorrowerType.values()), BorrowerType::getLabel);
        String document = InputValidator.readText("Documento del " + type.getLabel().toLowerCase() + ": ");
        int days = InputValidator.readInt("Días de préstamo (1 a " + LoanController.MAX_LOAN_DAYS + "): ", 1, LoanController.MAX_LOAN_DAYS);
        String observations = InputValidator.readOptionalText("Observaciones (opcional): ");
        LoanController.LoanReceipt receipt = loanController.lend(code, type, document, days, observations);
        System.out.println("\n[OK] " + receipt.resourceName() + " prestado a " + receipt.borrowerName()
                + ". Fecha de devolución: " + receipt.dueDate() + ".");
    }

    private void returnResource() throws BusinessException {
        String code = InputValidator.readText("Código del recurso a devolver: ");
        String observations = InputValidator.readOptionalText("Observaciones de la devolución (opcional): ");
        Loan loan = loanController.registerReturn(code, observations);
        long late = loan.daysLate(LocalDate.now());
        System.out.println("\n[OK] " + loan.resourceName() + " devuelto por " + loan.borrowerName()
                + ". El recurso quedó Disponible.");
        if (late > 0) {
            System.out.println("[INFO] La devolución tuvo " + late + " día(s) de retraso.");
        }
    }

    private void showOverdueReport() {
        List<Loan> overdue = loanController.getOverdueLoans();
        System.out.println();
        if (overdue.isEmpty()) {
            System.out.println("[INFO] No hay préstamos vencidos.");
            return;
        }
        printLoans("Préstamos vencidos (sin devolver después de la fecha programada)", overdue);
    }

    private void showBorrowerHistory() throws BusinessException {
        BorrowerType type = ConsoleForm.choose("Tipo de persona", Arrays.asList(BorrowerType.values()), BorrowerType::getLabel);
        String document = InputValidator.readText("Documento: ");
        String name = loanController.borrowerName(type, document);
        List<Loan> loans = loanController.getLoansOf(type, document);
        System.out.println("\nHistorial de préstamos de " + name + ":");
        if (loans.isEmpty()) {
            System.out.println("  (sin préstamos)");
            return;
        }
        loans.forEach(l -> System.out.printf("  %-8s %-34s %s -> %s  %-9s%s%n", l.resourceCode(), l.resourceName(),
                l.loanDate(), l.returnDate() != null ? l.returnDate() : l.dueDate(), l.status().getDbValue(),
                l.returnDate() == null ? " (vence " + l.dueDate() + ")" : ""));
    }

    // ------------------------------------------------------------------ helpers

    private LocalDate readOptionalAcquisitionDate() {
        while (true) {
            String text = InputValidator.readOptionalText("Fecha de adquisición AAAA-MM-DD (opcional): ");
            if (text == null) {
                return null;
            }
            try {
                LocalDate date = LocalDate.parse(text);
                resourceController.validateAcquisitionDate(date);
                return date;
            } catch (DateTimeParseException e) {
                ErrorLogger.warn("Invalid input at acquisition date: wrong format");
                System.out.println("[ERROR] Fecha inválida. Use el formato AAAA-MM-DD.");
            } catch (BusinessException e) {
                ErrorLogger.warn("Validation failed: " + e.getMessage());
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }

    private void printResources(List<Resource> resources) {
        if (resources.isEmpty()) {
            System.out.println("\n[INFO] No se encontraron recursos.");
            return;
        }
        System.out.println();
        System.out.printf("%-9s %-40s %-24s %-14s %-16s%n", "Código", "Nombre", "Categoría", "Ubicación", "Estado");
        System.out.println("-".repeat(106));
        resources.forEach(r -> System.out.printf("%-9s %-40s %-24s %-14s %-16s%n", r.code(), r.name(), r.categoryName(),
                ConsoleForm.orDash(r.location()), r.status().getDbValue()));
        System.out.println("\nTotal: " + resources.size() + " recurso(s).");
    }

    private void printLoans(String title, List<Loan> loans) {
        System.out.println("\n" + title + ":");
        if (loans.isEmpty()) {
            System.out.println("  (ninguno)");
            return;
        }
        LocalDate today = LocalDate.now();
        System.out.printf("%-8s %-30s %-9s %-26s %-11s %-11s %s%n", "Código", "Recurso", "Tipo", "Prestatario",
                "Prestado", "Vence", "Retraso");
        System.out.println("-".repeat(112));
        loans.forEach(l -> System.out.printf("%-8s %-30s %-9s %-26s %-11s %-11s %s%n", l.resourceCode(), l.resourceName(),
                l.borrowerType().getLabel(), l.borrowerName(), l.loanDate(), l.dueDate(),
                l.daysLate(today) > 0 ? l.daysLate(today) + " día(s)" : "-"));
        System.out.println("\nTotal: " + loans.size() + " préstamo(s).");
    }
}