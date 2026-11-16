package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.LoanDao;
import com.littlehorizons.erp.dao.ResourceDao;
import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.dao.TeacherDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.BorrowerType;
import com.littlehorizons.erp.model.Loan;
import com.littlehorizons.erp.model.Resource;
import com.littlehorizons.erp.model.ResourceStatus;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.StudentStatus;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.model.TeacherStatus;

import java.time.LocalDate;
import java.util.List;

/** Business rules for lending resources to students and teachers. */
public class LoanController {

    public static final int MAX_LOAN_DAYS = 30;

    private final LoanDao loanDao;
    private final ResourceDao resourceDao;
    private final StudentDao studentDao;
    private final TeacherDao teacherDao;

    public LoanController(LoanDao loanDao, ResourceDao resourceDao, StudentDao studentDao, TeacherDao teacherDao) {
        this.loanDao = loanDao;
        this.resourceDao = resourceDao;
        this.studentDao = studentDao;
        this.teacherDao = teacherDao;
    }

    /** Result of a loan: who has the resource and until when. */
    public record LoanReceipt(String resourceName, String borrowerName, LocalDate dueDate) { }

    /**
     * Lends a resource. The borrower must be active, must not have overdue loans and must not exceed the
     * limit of open loans of their type. Availability is checked again inside the database transaction.
     */
    public LoanReceipt lend(String resourceCode, BorrowerType type, String borrowerDocument, int days,
                            String observations) throws BusinessException {
        if (days < 1 || days > MAX_LOAN_DAYS) {
            throw new BusinessException("El préstamo debe durar entre 1 y " + MAX_LOAN_DAYS + " días.");
        }
        Resource resource = resourceDao.findByCode(resourceCode.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("No existe un recurso con el código " + resourceCode.trim().toUpperCase() + "."));
        if (resource.status() != ResourceStatus.AVAILABLE) {
            throw new BusinessException("El recurso " + resource.code() + " no está disponible (estado: "
                    + resource.status().getDbValue() + ").");
        }
        int borrowerId;
        String borrowerName;
        if (type == BorrowerType.STUDENT) {
            Student student = studentDao.findByDocument(borrowerDocument.trim())
                    .orElseThrow(() -> new BusinessException("No existe un estudiante con el documento " + borrowerDocument + "."));
            if (student.getStatus() != StudentStatus.ACTIVE) {
                throw new BusinessException("Solo los estudiantes en estado Activo pueden pedir préstamos.");
            }
            borrowerId = student.getId();
            borrowerName = student.getFullName();
        } else {
            Teacher teacher = teacherDao.findByDocument(borrowerDocument.trim())
                    .orElseThrow(() -> new BusinessException("No existe un profesor con el documento " + borrowerDocument + "."));
            if (teacher.status() != TeacherStatus.ACTIVE) {
                throw new BusinessException("Solo los profesores en estado Activo pueden pedir préstamos.");
            }
            borrowerId = teacher.id();
            borrowerName = teacher.fullName();
        }
        LocalDate today = LocalDate.now();
        if (loanDao.countOverdue(type, borrowerId, today) > 0) {
            throw new BusinessException(borrowerName + " tiene préstamos vencidos: debe devolverlos antes de pedir otro.");
        }
        if (loanDao.countOpen(type, borrowerId) >= type.getMaxOpenLoans()) {
            throw new BusinessException(borrowerName + " ya tiene " + type.getMaxOpenLoans()
                    + " préstamos abiertos, el máximo para " + type.getLabel().toLowerCase() + "s.");
        }
        LocalDate dueDate = today.plusDays(days);
        if (!loanDao.lend(type, borrowerId, resource.id(), today, dueDate, observations)) {
            throw new BusinessException("El recurso " + resource.code() + " ya no está disponible.");
        }
        return new LoanReceipt(resource.name(), borrowerName, dueDate);
    }

    /** Registers the return of a resource and gives back the closed loan so the view can show late days. */
    public Loan registerReturn(String resourceCode, String observations) throws BusinessException {
        Resource resource = resourceDao.findByCode(resourceCode.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("No existe un recurso con el código " + resourceCode.trim().toUpperCase() + "."));
        if (resource.status() != ResourceStatus.ON_LOAN) {
            throw new BusinessException("El recurso " + resource.code() + " no está prestado (estado: "
                    + resource.status().getDbValue() + ").");
        }
        Loan loan = loanDao.findOpenByResource(resource.id())
                .orElseThrow(() -> new BusinessException("No se encontró un préstamo abierto de " + resource.code() + "."));
        if (!loanDao.registerReturn(loan.borrowerType(), loan.id(), LocalDate.now(), observations)) {
            throw new BusinessException("El préstamo ya había sido cerrado.");
        }
        return loan;
    }

    /** Open loans; first refreshes the ones whose due date has passed to Vencido. */
    public List<Loan> getOpenLoans() {
        loanDao.markOverdue(LocalDate.now());
        return loanDao.findOpen();
    }

    /** Loans not returned after their due date, most delayed first. */
    public List<Loan> getOverdueLoans() {
        loanDao.markOverdue(LocalDate.now());
        return loanDao.findOverdue(LocalDate.now());
    }

    public String borrowerName(BorrowerType type, String document) throws BusinessException {
        return findBorrower(type, document).name();
    }

    public List<Loan> getLoansOf(BorrowerType type, String document) throws BusinessException {
        return loanDao.findByBorrower(type, findBorrower(type, document).id());
    }

    private record Borrower(int id, String name) { }

    private Borrower findBorrower(BorrowerType type, String document) throws BusinessException {
        if (type == BorrowerType.STUDENT) {
            Student student = studentDao.findByDocument(document.trim())
                    .orElseThrow(() -> new BusinessException("No existe un estudiante con el documento " + document + "."));
            return new Borrower(student.getId(), student.getFullName());
        }
        Teacher teacher = teacherDao.findByDocument(document.trim())
                .orElseThrow(() -> new BusinessException("No existe un profesor con el documento " + document + "."));
        return new Borrower(teacher.id(), teacher.fullName());
    }
}
