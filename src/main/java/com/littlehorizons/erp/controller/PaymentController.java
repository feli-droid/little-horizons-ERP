package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.GuardianDao;
import com.littlehorizons.erp.dao.PaymentDao;
import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.BillingPeriod;
import com.littlehorizons.erp.model.Charge;
import com.littlehorizons.erp.model.DebtorSummary;
import com.littlehorizons.erp.model.Guardian;
import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.Payment;
import com.littlehorizons.erp.model.PaymentBatch;
import com.littlehorizons.erp.model.PaymentConcept;
import com.littlehorizons.erp.model.PaymentLine;
import com.littlehorizons.erp.model.PaymentMethod;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.StudentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Business rules for payment concepts, charges, payments, debtors and the invoice of each payment. */
public class PaymentController {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private static final Pattern CONCEPT_CODE = Pattern.compile("^[A-Za-z0-9_]{2,20}$");
    private static final int MAX_ITEMS_PER_PAYMENT = 10;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PaymentDao paymentDao;
    private final InvoiceController invoiceController;
    private final StudentDao studentDao;
    private final GuardianDao guardianDao;

    public PaymentController(PaymentDao paymentDao, InvoiceController invoiceController, StudentDao studentDao,
                             GuardianDao guardianDao) {
        this.paymentDao = paymentDao;
        this.invoiceController = invoiceController;
        this.studentDao = studentDao;
        this.guardianDao = guardianDao;
    }

    /** What the user wants to pay: a pending charge, or a concept paid directly (then charge is null). */
    public record PaymentItem(Charge charge, PaymentConcept concept, String notes) { }

    /** Result of a payment: its invoice and where the text file was written (null if it could not be written). */
    public record PaymentReceipt(String invoiceNumber, BigDecimal total, int items, String filePath, String fileWarning) { }

    // ------------------------------------------------------------------ lookups

    public Student getStudentByDocument(String documentNumber) throws BusinessException {
        return studentDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un estudiante con el documento " + documentNumber + "."));
    }

    public Guardian getGuardianByDocument(String documentNumber) throws BusinessException {
        return guardianDao.findByDocument(documentNumber.trim())
                .orElseThrow(() -> new BusinessException("No existe un acudiente con el documento " + documentNumber + "."));
    }

    public List<Guardian> getGuardians(Student student) throws BusinessException {
        List<Guardian> guardians = guardianDao.findByStudentId(student.getId());
        if (guardians.isEmpty()) {
            throw new BusinessException("El estudiante no tiene acudientes registrados.");
        }
        return guardians;
    }

    public List<PaymentConcept> listConcepts(boolean onlyActive) {
        return paymentDao.findConcepts(onlyActive);
    }

    public List<Charge> getPendingCharges(Student student) {
        return paymentDao.findPendingCharges(student.getId());
    }

    // -------------------------------------------------------------- concepts

    public void validateConceptCode(String code) throws BusinessException {
        if (!CONCEPT_CODE.matcher(code.trim()).matches()) {
            throw new BusinessException("El código debe tener de 2 a 20 letras, números o guion bajo (ej. TUTORIA).");
        }
        if (code.chars().noneMatch(Character::isLetter)) {
            throw new BusinessException("El código debe contener letras; no puede ser solo números.");
        }
        if (paymentDao.findConceptByCode(code.trim().toUpperCase()).isPresent()) {
            throw new BusinessException("Ya existe un concepto con el código " + code.trim().toUpperCase() + ".");
        }
    }

    public void validateConceptName(String name) throws BusinessException {
        if (name.trim().length() < 3) {
            throw new BusinessException("El concepto debe tener al menos 3 caracteres.");
        }
        if (name.chars().noneMatch(Character::isLetter)) {
            throw new BusinessException("El concepto debe contener letras; no puede ser solo números o símbolos.");
        }
    }

    /** A description is optional, but when given it must contain letters. */
    public void validateConceptDescription(String description) throws BusinessException {
        if (description.chars().noneMatch(Character::isLetter)) {
            throw new BusinessException("La descripción debe contener letras; no puede ser solo números o símbolos.");
        }
    }

    public void validateAmount(BigDecimal amount) throws BusinessException {
        if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException("El valor debe ser mayor que cero y no superar " + MAX_AMOUNT.toPlainString() + ".");
        }
    }

    public int registerConcept(String code, String name, String description, BigDecimal amount, boolean recurring)
            throws BusinessException {
        validateConceptCode(code);
        validateConceptName(name);
        validateAmount(amount);
        return paymentDao.insertConcept(code.trim().toUpperCase(), name.trim(), description,
                amount.setScale(2, RoundingMode.HALF_UP), recurring);
    }

    /** Changes the base value of a concept; charges already generated keep the value they had. */
    public void updateConceptAmount(PaymentConcept concept, BigDecimal newAmount) throws BusinessException {
        validateAmount(newAmount);
        if (!paymentDao.updateConceptAmount(concept.id(), newAmount.setScale(2, RoundingMode.HALF_UP))) {
            throw new BusinessException("No se pudo actualizar el concepto.");
        }
    }

    // ---------------------------------------------------------------- charges

    /** Parses a discount percentage typed by the user (greater than 0 and lower than 100). */
    public BigDecimal parseDiscountPercent(String text) throws BusinessException {
        BigDecimal percent;
        try {
            percent = new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new BusinessException("Escriba el descuento como un número (ej. 10 para 10%).");
        }
        if (percent.signum() <= 0 || percent.compareTo(ONE_HUNDRED) >= 0) {
            throw new BusinessException("El descuento debe ser mayor que 0 y menor que 100.");
        }
        return percent;
    }

    /** Discount in pesos for a base value and a percentage. */
    public BigDecimal discountFor(BigDecimal baseValue, BigDecimal percent) {
        return baseValue.multiply(percent).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public void validateDueDate(LocalDate dueDate) throws BusinessException {
        LocalDate today = LocalDate.now();
        if (dueDate.isBefore(today.minusYears(1)) || dueDate.isAfter(today.plusYears(2))) {
            throw new BusinessException("La fecha de vencimiento debe estar entre un año atrás y dos años adelante.");
        }
    }

    /** Creates a charge for a student; a percentage discount (siblings, scholarship) needs a reason. */
    public int createCharge(Student student, PaymentConcept concept, BillingPeriod period, LocalDate dueDate,
                            BigDecimal discountPercent, String discountReason) throws BusinessException {
        if (student.getStatus() != StudentStatus.ACTIVE) {
            throw new BusinessException("Solo se generan cargos a estudiantes en estado Activo.");
        }
        if (!concept.active()) {
            throw new BusinessException("El concepto " + concept.name() + " no está activo.");
        }
        validateDueDate(dueDate);
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        if (discountPercent != null) {
            if (discountReason == null || discountReason.isBlank()) {
                throw new BusinessException("Indique el motivo del descuento.");
            }
            discount = discountFor(concept.baseAmount(), discountPercent);
        }
        if (paymentDao.chargeExists(student.getId(), concept.id(), dueDate)) {
            throw new BusinessException(student.getFullName() + " ya tiene un cargo de " + concept.name()
                    + " con vencimiento " + dueDate + ".");
        }
        return paymentDao.insertCharge(student.getId(), concept.id(), dueDate.getYear(), period,
                concept.baseAmount(), discount, discountPercent == null ? null : discountReason.trim(), dueDate);
    }

    // --------------------------------------------------------------- payments

    /**
     * Registers a payment of one or more items made by a guardian of the student, and issues its invoice.
     * Payments, paid charges and invoice are saved together; the text file is written afterwards, and if that
     * fails the payment stays saved and the file can be regenerated later.
     */
    public PaymentReceipt pay(Student student, Guardian payer, PaymentMethod method, List<PaymentItem> items)
            throws BusinessException {
        if (items.isEmpty() || items.size() > MAX_ITEMS_PER_PAYMENT) {
            throw new BusinessException("Un pago debe incluir entre 1 y " + MAX_ITEMS_PER_PAYMENT + " conceptos.");
        }
        if (guardianDao.findByStudentId(student.getId()).stream().noneMatch(g -> g.getId() == payer.getId())) {
            throw new BusinessException(payer.getFullName() + " no es acudiente de " + student.getFullName() + ".");
        }
        Set<Integer> pendingIds = new HashSet<>();
        paymentDao.findPendingCharges(student.getId()).forEach(c -> pendingIds.add(c.id()));
        Set<Integer> usedCharges = new HashSet<>();
        LocalDate today = LocalDate.now();
        int receiptSequence = paymentDao.nextReceiptSequence(today.getYear());
        List<PaymentLine> lines = new ArrayList<>();
        for (PaymentItem item : items) {
            String receipt = String.format("REC-%d-%06d", today.getYear(), receiptSequence++);
            lines.add(buildLine(student, item, pendingIds, usedCharges, receipt, today));
        }
        Invoice draft = invoiceController.buildInvoice(payer, today, lines);
        int invoiceId = paymentDao.registerPayments(new PaymentBatch(payer.getId(), method, today, lines), draft);
        if (invoiceId == 0) {
            throw new BusinessException("Uno de los cargos ya fue pagado. Consulte de nuevo los cargos pendientes.");
        }
        String filePath = null;
        String warning = null;
        try {
            filePath = invoiceController.writeFile(draft.number()).toString();
        } catch (BusinessException e) {
            warning = e.getMessage();
        }
        return new PaymentReceipt(draft.number(), draft.total(), lines.size(), filePath, warning);
    }

    private PaymentLine buildLine(Student student, PaymentItem item, Set<Integer> pendingIds, Set<Integer> usedCharges,
                                  String receipt, LocalDate today) throws BusinessException {
        if (item.charge() != null) {
            Charge charge = item.charge();
            if (!pendingIds.contains(charge.id())) {
                throw new BusinessException("El cargo de " + charge.conceptName() + " ya no está pendiente.");
            }
            if (!usedCharges.add(charge.id())) {
                throw new BusinessException("El cargo de " + charge.conceptName() + " está repetido en el pago.");
            }
            return new PaymentLine(student.getId(), student.getFullName(), charge.conceptId(), charge.id(),
                    charge.finalValue(), charge.year(), charge.period().getDbValue(),
                    charge.conceptName() + " (" + charge.period().getDbValue() + " " + charge.year() + ") - "
                            + student.getFullName(), receipt, item.notes());
        }
        PaymentConcept concept = item.concept();
        if (concept == null || !concept.active()) {
            throw new BusinessException("El concepto seleccionado no está activo.");
        }
        return new PaymentLine(student.getId(), student.getFullName(), concept.id(), null, concept.baseAmount(),
                today.getYear(), null, concept.name() + " - " + student.getFullName(), receipt, item.notes());
    }

    // ---------------------------------------------------------------- reports

    public List<Payment> getPaymentsOf(Student student) {
        return paymentDao.findPaymentsByStudent(student.getId());
    }

    public List<Payment> getPaymentsOf(Guardian guardian) {
        return paymentDao.findPaymentsByGuardian(guardian.getId());
    }

    /** Students with charges pending for more than one month, largest debt first. */
    public List<DebtorSummary> getDebtors() {
        LocalDate today = LocalDate.now();
        return paymentDao.findDebtors(today.minusMonths(1), today);
    }
}