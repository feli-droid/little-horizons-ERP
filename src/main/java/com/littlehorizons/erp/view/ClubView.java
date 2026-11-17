package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.ClubController;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.Club;
import com.littlehorizons.erp.model.ClubMembership;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.InputValidator;

import java.util.List;

/** Console menu for clubs and memberships. */
public class ClubView {

    private static final int EXIT_OPTION = 0;

    private final ClubController controller;

    public ClubView(ClubController controller) {
        this.controller = controller;
    }

    public void run() {
        boolean back = false;
        while (!back) {
            printMenu();
            int option = InputValidator.readInt("Seleccione una opción: ", 0, 6);
            try {
                switch (option) {
                    case 1 -> listClubs();
                    case 2 -> registerClub();
                    case 3 -> enrollStudent();
                    case 4 -> withdrawStudent();
                    case 5 -> showMembers();
                    case 6 -> showStudentClubs();
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
        System.out.println("║   CLUBES                                     ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("1. Listar clubes y cupos");
        System.out.println("2. Registrar club");
        System.out.println("3. Inscribir estudiante en un club");
        System.out.println("4. Retirar estudiante de un club");
        System.out.println("5. Ver miembros de un club");
        System.out.println("6. Ver clubes de un estudiante");
        System.out.println("0. Volver");
    }

    private void listClubs() {
        List<Club> clubs = controller.listClubs();
        System.out.println();
        System.out.printf("%-34s %-26s %-9s %-8s%n", "Club", "Profesor encargado", "Cupo", "Estado");
        System.out.println("-".repeat(80));
        clubs.forEach(c -> System.out.printf("%-34s %-26s %-9s %-8s%n", c.name(), ConsoleForm.orDash(c.supervisorName()),
                c.activeMembers() + "/" + c.capacity() + (c.availableSeats() <= 0 ? " LLENO" : ""),
                c.active() ? "Activo" : "Inactivo"));
        System.out.println("\nTotal: " + clubs.size() + " club(es).");
    }

    private void registerClub() throws BusinessException {
        System.out.println("\n--- Registro de club ---");
        String name = ConsoleForm.readField("Nombre del club: ", controller::validateClubName);
        String description = ConsoleForm.readOptionalField("Descripción (opcional): ", controller::validateClubDescription);
        Teacher supervisor = controller.getSupervisor(InputValidator.readText("Documento del profesor encargado: "));
        int capacity = InputValidator.readInt("Cupo máximo (1 a " + ClubController.MAX_CAPACITY + "): ", 1, ClubController.MAX_CAPACITY);
        String schedule = InputValidator.readOptionalText("Horario de reunión (ej. Martes 3-5 PM, opcional): ");
        System.out.println("\nResumen: " + name + " | Encargado: " + supervisor.fullName() + " | Cupo: " + capacity);
        if (!InputValidator.confirm("¿Confirma el registro?")) {
            System.out.println("[INFO] Registro cancelado.");
            return;
        }
        int id = controller.registerClub(name, description, supervisor, capacity, schedule);
        System.out.println("\n[OK] Club registrado con ID " + id + ".");
    }

    private void enrollStudent() throws BusinessException {
        Club club = chooseClub();
        Student student = controller.getStudentByDocument(InputValidator.readText("Documento del estudiante: "));
        System.out.println("Estudiante: " + student.getFullName() + " | Cupos libres en el club: " + club.availableSeats());
        controller.enrollStudent(club, student);
        System.out.println("\n[OK] " + student.getFullName() + " fue inscrito en " + club.name() + ".");
    }

    private void withdrawStudent() throws BusinessException {
        Club club = chooseClub();
        Student student = controller.getStudentByDocument(InputValidator.readText("Documento del estudiante: "));
        String reason = InputValidator.readOptionalText("Motivo del retiro (opcional): ");
        if (!InputValidator.confirm("¿Retirar a " + student.getFullName() + " de " + club.name() + "?")) {
            System.out.println("[INFO] Operación cancelada.");
            return;
        }
        controller.withdrawStudent(club, student, reason);
        System.out.println("\n[OK] El estudiante fue retirado del club.");
    }

    private void showMembers() {
        Club club = chooseClub();
        List<ClubMembership> members = controller.getMembers(club);
        System.out.println("\nMiembros activos de " + club.name() + " (" + members.size() + "/" + club.capacity() + "):");
        if (members.isEmpty()) {
            System.out.println("  (sin miembros)");
            return;
        }
        members.forEach(m -> System.out.printf("  %-12s %-34s ingreso: %s%n", m.studentDocument(), m.studentName(), m.joinDate()));
    }

    private void showStudentClubs() throws BusinessException {
        Student student = controller.getStudentByDocument(InputValidator.readText("Documento del estudiante: "));
        List<ClubMembership> memberships = controller.getMemberships(student);
        System.out.println("\nClubes de " + student.getFullName() + ":");
        if (memberships.isEmpty()) {
            System.out.println("  (no participa en ningún club)");
            return;
        }
        memberships.forEach(m -> System.out.printf("  %-34s %-9s desde %s%s%n", m.clubName(), m.status().getDbValue(),
                m.joinDate(), m.withdrawDate() == null ? "" : " hasta " + m.withdrawDate()));
    }

    private Club chooseClub() {
        return ConsoleForm.choose("Club", controller.listClubs(),
                c -> c.name() + " (" + c.activeMembers() + "/" + c.capacity() + ")");
    }
}