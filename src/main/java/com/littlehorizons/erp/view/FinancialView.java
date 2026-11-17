package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.InvoiceController;
import com.littlehorizons.erp.controller.PaymentController;
import com.littlehorizons.erp.controller.PaymentController.PaymentItem;
import com.littlehorizons.erp.controller.PaymentController.PaymentReceipt;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.BillingPeriod;
import com.littlehorizons.erp.model.Charge;
import com.littlehorizons.erp.model.DebtorSummary;
import com.littlehorizons.erp.model.Guardian;
import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.Payment;
import com.littlehorizons.erp.model.PaymentConcept;
import com.littlehorizons.erp.model.PaymentMethod;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.InputValidator;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Console menu for payment concepts, charges, payments, debtors and invoices. */
public class FinancialView {

    private static final int EXIT_OPTION = 0;
    private static final int LATEST_INVOICES = 10;

    private final PaymentController paymentController;
    private final InvoiceController invoiceController;

    public FinancialView(PaymentController paymentController, InvoiceController invoiceController) {
        this.paymentController = paymentController;
        this.invoiceController = invoiceController;
    }

    public void run() {
        boolean back = false;
        while (!back) {
            printMenu();
            int option = InputValidator.readInt("Seleccione una opción: ", 0, 10);
            try {
                switch (option) {
                    case 1 -> showConcepts();
                    case 2 -> registerConcept();
                    case 3 -> updateConceptAmount();
                    case 4 -> createCharge();
                    case 5 -> showPendingCharges();
                    case 6 -> registerPayment();
                    case 7 -> showStudentPayments();
                    case 8 -> showGuardianPayments();
                    case 9 -> showDebtors();
                    case 10 -> consultInvoice();
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
        System.out.println("║   GESTIÓN FINANCIERA                         ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("1. Catálogo de conceptos de pago");
        System.out.println("2. Registrar concepto de pago");
        System.out.println("3. Actualizar el valor de un concepto");
        System.out.println("4. Generar cargo a un estudiante (con descuento)");
        System.out.println("5. Ver cargos pendientes de un estudiante");
        System.out.println("6. Registrar pago y generar factura");
        System.out.println("7. Historial de pagos de un estudiante");
        System.out.println("8. Historial de pagos de un acudiente");
        System.out.println("9. Reporte de morosos (más de un mes)");
        System.out.println("10. Consultar o regenerar una factura");
        System.out.println("0. Volver al menú principal");
    }

    // ------------------------------------------------------------------ concepts

    private void showConcepts() {
        List<PaymentConcept> concepts = paymentController.listConcepts(false);
        System.out.println();
        System.out.printf("%-14s %-28s %-18s %-11s %s%n", "Código", "Concepto", "Valor base", "Recurrente", "Estado");
        System.out.println("-".repeat(84));
        concepts.forEach(c -> System.out.printf("%-14s %-28s %-18s %-11s %s%n", c.code(), c.name(),
                Money.format(c.baseAmount()), c.recurring() ? "Sí" : "No", c.active() ? "Activo" : "Inactivo"));
    }

    private void registerConcept() throws BusinessException {
        System.out.println("\n--- Nuevo concepto de pago ---");
        String code = ConsoleForm.readField("Código (ej. TUTORIA): ", paymentController::validateConceptCode);
        String name = InputValidator.readText("Nombre: ");
        String description = InputValidator.readOptionalText("Descripción (opcional): ");
        BigDecimal amount = readAmount("Valor base en pesos: ");
        boolean recurring = InputValidator.confirm("¿Es un cobro recurrente (mensual)?");
        int id = paymentController.registerConcept(code, name, description, amount, recurring);
        System.out.println("\n[OK] Concepto registrado con ID " + id + ".");
    }

    private void updateConceptAmount() throws BusinessException {
        PaymentConcept concept = chooseConcept(false);
        System.out.println("Valor actual de " + concept.name() + ": " + Money.format(concept.baseAmount()));
        BigDecimal amount = readAmount("Nuevo valor base: ");
        paymentController.updateConceptAmount(concept, amount);
        System.out.println("\n[OK] Valor actualizado. Los cargos ya generados conservan su valor anterior.");
    }

    // ------------------------------------------------------------------- charges

    private void createCharge() throws BusinessException {
        System.out.println("\n--- Generar cargo ---");
        Student student = findStudent();
        PaymentConcept concept = chooseConcept(true);
        BillingPeriod period = ConsoleForm.choose("Periodo", Arrays.asList(BillingPeriod.values()), BillingPeriod::getDbValue);
        LocalDate dueDate = readDueDate();
        String percentText = ConsoleForm.readOptionalField("Descuento en % (ej. 10 para hermanos; Enter = ninguno): ",
                paymentController::parseDiscountPercent);
        BigDecimal percent = percentText == null ? null : paymentController.parseDiscountPercent(percentText);
        String reason = percent == null ? null : InputValidator.readText("Motivo del descuento: ");
        BigDecimal discount = percent == null ? BigDecimal.ZERO : paymentController.discountFor(concept.baseAmount(), percent);
        System.out.println("\nEstudiante: " + student.getFullName());
        System.out.println("Concepto:   " + concept.name() + " (" + period.getDbValue() + ")");
        System.out.println("Valor base: " + Money.format(concept.baseAmount()) + " | Descuento: " + Money.format(discount)
                + " | A pagar: " + Money.format(concept.baseAmount().subtract(discount)));
        if (!InputValidator.confirm("¿Confirma el cargo?")) {
            System.out.println("[INFO] Operación cancelada.");
            return;
        }
        int id = paymentController.createCharge(student, concept, period, dueDate, percent, reason);
        System.out.println("\n[OK] Cargo generado con ID " + id + ".");
    }

    private void showPendingCharges() throws BusinessException {
        Student student = findStudent();
        List<Charge> charges = paymentController.getPendingCharges(student);
        System.out.println("\nCargos pendientes de " + student.getFullName() + ":");
        if (charges.isEmpty()) {
            System.out.println("  (no tiene cargos pendientes)");
            return;
        }
        charges.forEach(c -> System.out.println("  " + describeCharge(c)));
        BigDecimal total = charges.stream().map(Charge::finalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("\nTotal pendiente: " + Money.format(total));
    }

    // ------------------------------------------------------------------ payments

    private void registerPayment() throws BusinessException {
        System.out.println("\n--- Registro de pago ---");
        Student student = findStudent();
        Guardian payer = ConsoleForm.choose("Acudiente que realiza el pago", paymentController.getGuardians(student),
                g -> g.getFullName() + " (" + g.getRelationship() + ")");
        List<PaymentItem> items = new ArrayList<>();
        do {
            items.add(readItem(student, items));
        } while (InputValidator.confirm("¿Agregar otro concepto a este pago?"));
        PaymentMethod method = ConsoleForm.choose("Método de pago", Arrays.asList(PaymentMethod.values()),
                PaymentMethod::getDbValue);

        System.out.println("\nResumen del pago de " + student.getFullName() + " (paga " + payer.getFullName() + "):");
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentItem item : items) {
            BigDecimal amount = item.charge() != null ? item.charge().finalValue() : item.concept().baseAmount();
            String name = item.charge() != null ? item.charge().conceptName() : item.concept().name();
            System.out.printf("  - %-34s %s%n", name, Money.format(amount));
            total = total.add(amount);
        }
        System.out.println("  Método: " + method.getDbValue() + " | Total: " + Money.format(total));
        if (!InputValidator.confirm("¿Confirma el pago?")) {
            System.out.println("[INFO] Pago cancelado.");
            return;
        }
        PaymentReceipt receipt = paymentController.pay(student, payer, method, items);
        System.out.println("\n[OK] Pago registrado. Factura " + receipt.invoiceNumber() + " por " + Money.format(receipt.total())
                + " (" + receipt.items() + " concepto(s)).");
        if (receipt.filePath() != null) {
            System.out.println("     Factura guardada en: " + receipt.filePath());
        } else {
            System.out.println("[INFO] " + receipt.fileWarning());
        }
    }

    private PaymentItem readItem(Student student, List<PaymentItem> chosen) throws BusinessException {
        Set<Integer> chosenCharges = chosen.stream().filter(i -> i.charge() != null)
                .map(i -> i.charge().id()).collect(Collectors.toSet());
        List<Charge> available = paymentController.getPendingCharges(student).stream()
                .filter(c -> !chosenCharges.contains(c.id())).toList();
        boolean payCharge = false;
        if (!available.isEmpty()) {
            payCharge = ConsoleForm.choose("¿Qué desea pagar?",
                    List.of("Un cargo pendiente", "Un concepto sin cargo previo"), s -> s).startsWith("Un cargo");
        } else {
            System.out.println("[INFO] El estudiante no tiene (más) cargos pendientes: se paga un concepto directo.");
        }
        if (payCharge) {
            return new PaymentItem(ConsoleForm.choose("Cargo pendiente", available, this::describeCharge), null, null);
        }
        PaymentConcept concept = chooseConcept(true);
        return new PaymentItem(null, concept, InputValidator.readOptionalText("Notas (opcional): "));
    }

    private void showStudentPayments() throws BusinessException {
        Student student = findStudent();
        printPayments("Pagos de " + student.getFullName(), paymentController.getPaymentsOf(student));
    }

    private void showGuardianPayments() throws BusinessException {
        Guardian guardian = paymentController.getGuardianByDocument(InputValidator.readText("Documento del acudiente: "));
        printPayments("Pagos de " + guardian.getFullName() + " y de sus estudiantes", paymentController.getPaymentsOf(guardian));
    }

    private void showDebtors() {
        List<DebtorSummary> debtors = paymentController.getDebtors();
        System.out.println("\nEstudiantes con cargos pendientes por más de un mes:");
        if (debtors.isEmpty()) {
            System.out.println("  Ningún estudiante tiene pagos atrasados por más de un mes.");
            return;
        }
        System.out.printf("%-12s %-30s %-24s %-11s %-7s %-16s %s%n", "Documento", "Estudiante", "Acudiente", "Teléfono",
                "Cargos", "Deuda", "Atraso");
        System.out.println("-".repeat(118));
        debtors.forEach(d -> System.out.printf("%-12s %-30s %-24s %-11s %-7d %-16s %d días%n", d.documentNumber(),
                d.studentName(), d.guardianName(), d.guardianPhone(), d.pendingCharges(), Money.format(d.totalDebt()),
                d.daysOverdue()));
        BigDecimal total = debtors.stream().map(DebtorSummary::totalDebt).reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("\nMorosos: " + debtors.size() + " | Deuda total: " + Money.format(total));
    }

    // ------------------------------------------------------------------ invoices

    private void consultInvoice() throws BusinessException {
        String input = InputValidator.readOptionalText("Número de factura (ej. LH-000001 o solo 1; Enter = últimas "
                + LATEST_INVOICES + "): ");
        if (input == null) {
            List<Invoice> latest = invoiceController.latest(LATEST_INVOICES);
            latest.forEach(i -> System.out.printf("  %-10s %s  %-30s %s%n", i.number(), i.issueDate(), i.clientName(),
                    Money.format(i.total())));
            return;
        }
        Invoice invoice = invoiceController.getByNumber(input);
        System.out.println("\nFactura " + invoice.number() + " | " + invoice.issueDate() + " | Cliente: " + invoice.clientName()
                + " (" + invoice.clientDocument() + ")");
        invoice.lines().forEach(l -> System.out.printf("  - %-60s %s%n", l.description(), Money.format(l.subtotal())));
        System.out.println("  Subtotal: " + Money.format(invoice.subtotal()) + " | IVA: " + Money.format(invoice.vat())
                + " | Total: " + Money.format(invoice.total()));
        System.out.println("  CUFE: " + invoice.cufe());
        System.out.println("  Archivo: " + (invoice.filePath() == null ? "(aún no generado)" : invoice.filePath()));
        if (InputValidator.confirm("¿Generar (o regenerar) el archivo .txt de esta factura?")) {
            Path path = invoiceController.writeFile(invoice.number());
            System.out.println("\n[OK] Factura guardada en: " + path);
        }
    }

    // ------------------------------------------------------------------- helpers

    private Student findStudent() throws BusinessException {
        return paymentController.getStudentByDocument(InputValidator.readText("Documento del estudiante: "));
    }

    private PaymentConcept chooseConcept(boolean onlyActive) {
        return ConsoleForm.choose("Concepto", paymentController.listConcepts(onlyActive),
                c -> c.name() + " - " + Money.format(c.baseAmount()));
    }

    private String describeCharge(Charge c) {
        return c.conceptName() + " (" + c.period().getDbValue() + " " + c.year() + ") vence " + c.dueDate() + " - "
                + Money.format(c.finalValue()) + (c.discount().signum() > 0 ? " [desc. " + Money.format(c.discount()) + "]" : "");
    }

    private BigDecimal readAmount(String prompt) {
        while (true) {
            BigDecimal amount = InputValidator.readDecimal(prompt, BigDecimal.ZERO, new BigDecimal("999999999999"));
            try {
                paymentController.validateAmount(amount);
                return amount;
            } catch (BusinessException e) {
                ErrorLogger.warn("Validation failed: " + e.getMessage());
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }

    private LocalDate readDueDate() {
        while (true) {
            LocalDate date = InputValidator.readDate("Fecha de vencimiento");
            try {
                paymentController.validateDueDate(date);
                return date;
            } catch (BusinessException e) {
                ErrorLogger.warn("Validation failed: " + e.getMessage());
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }

    private void printPayments(String title, List<Payment> payments) {
        System.out.println("\n" + title + ":");
        if (payments.isEmpty()) {
            System.out.println("  (sin pagos registrados)");
            return;
        }
        System.out.printf("%-12s %-16s %-26s %-26s %-16s %s%n", "Fecha", "Recibo", "Estudiante", "Concepto", "Valor", "Método");
        System.out.println("-".repeat(118));
        payments.forEach(p -> System.out.printf("%-12s %-16s %-26s %-26s %-16s %s%n", p.date(), p.receiptNumber(),
                p.studentName(), p.conceptName(), Money.format(p.amount()), p.method().getDbValue()));
        BigDecimal total = payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("\nTotal: " + payments.size() + " pago(s) por " + Money.format(total));
    }
}