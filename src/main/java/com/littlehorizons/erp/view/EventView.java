package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.EventController;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.exception.DataAccessException;
import com.littlehorizons.erp.model.EventParticipant;
import com.littlehorizons.erp.model.EventPosition;
import com.littlehorizons.erp.model.EventType;
import com.littlehorizons.erp.model.ParticipationRole;
import com.littlehorizons.erp.model.SchoolEvent;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.model.StudentParticipation;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.InputValidator;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Console menu for events, participants and positions. */
public class EventView {

    private static final int EXIT_OPTION = 0;

    private final EventController controller;

    public EventView(EventController controller) {
        this.controller = controller;
    }

    public void run() {
        boolean back = false;
        while (!back) {
            printMenu();
            int option = InputValidator.readInt("Seleccione una opción: ", 0, 6);
            try {
                switch (option) {
                    case 1 -> listEvents();
                    case 2 -> registerEvent();
                    case 3 -> addParticipant();
                    case 4 -> setPosition();
                    case 5 -> showParticipants();
                    case 6 -> showStudentParticipation();
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
        System.out.println("║   EVENTOS                                    ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("1. Calendario de eventos");
        System.out.println("2. Registrar evento");
        System.out.println("3. Registrar participante en un evento");
        System.out.println("4. Registrar posición (eventos con competencia)");
        System.out.println("5. Ver participantes y posiciones de un evento");
        System.out.println("6. Participación de un estudiante");
        System.out.println("0. Volver");
    }

    private void listEvents() {
        List<SchoolEvent> events = controller.listEvents();
        System.out.println();
        System.out.printf("%-12s %-44s %-12s %-12s %s%n", "Fecha", "Evento", "Tipo", "Competencia", "Participantes");
        System.out.println("-".repeat(96));
        events.forEach(e -> System.out.printf("%-12s %-44s %-12s %-12s %d%n", e.date(), e.name(), e.type().getDbValue(),
                e.competition() ? "Sí" : "No", e.participantCount()));
        System.out.println("\nTotal: " + events.size() + " evento(s).");
    }

    private void registerEvent() throws BusinessException {
        System.out.println("\n--- Registro de evento ---");
        String name = ConsoleForm.readField("Nombre del evento: ", controller::validateEventName);
        String description = InputValidator.readOptionalText("Descripción (opcional): ");
        EventType type = ConsoleForm.choose("Tipo de evento", Arrays.asList(EventType.values()), EventType::getDbValue);
        LocalDate date = readEventDate();
        String place = InputValidator.readOptionalText("Lugar (opcional): ");
        boolean competition = InputValidator.confirm("¿El evento tiene competencia con posiciones?");
        Teacher organizer = controller.getOrganizer(InputValidator.readText("Documento del profesor organizador: "));
        System.out.println("\nResumen: " + name + " | " + type.getDbValue() + " | " + date + " | Organiza: " + organizer.fullName());
        if (!InputValidator.confirm("¿Confirma el registro?")) {
            System.out.println("[INFO] Registro cancelado.");
            return;
        }
        int id = controller.registerEvent(name, description, type, date, place, competition, organizer);
        System.out.println("\n[OK] Evento registrado con ID " + id + ".");
    }

    private void addParticipant() throws BusinessException {
        SchoolEvent event = chooseEvent();
        Student student = controller.getStudentByDocument(InputValidator.readText("Documento del estudiante: "));
        ParticipationRole role = ConsoleForm.choose("Rol en el evento", Arrays.asList(ParticipationRole.values()),
                ParticipationRole::getDbValue);
        controller.addParticipant(event, student, role);
        System.out.println("\n[OK] " + student.getFullName() + " fue registrado en " + event.name() + " como " + role.getDbValue() + ".");
    }

    private void setPosition() throws BusinessException {
        SchoolEvent event = chooseEvent();
        if (!event.competition()) {
            throw new BusinessException("El evento " + event.name() + " no tiene competencia.");
        }
        Student student = controller.getStudentByDocument(InputValidator.readText("Documento del estudiante: "));
        EventParticipant participant = controller.getParticipants(event).stream()
                .filter(p -> p.studentId() == student.getId())
                .findFirst()
                .orElseThrow(() -> new BusinessException(student.getFullName() + " no está registrado en " + event.name() + "."));
        EventPosition position = ConsoleForm.choose("Posición obtenida", Arrays.asList(EventPosition.values()),
                EventPosition::getDbValue);
        controller.setPosition(event, participant, position);
        System.out.println("\n[OK] " + student.getFullName() + ": " + position.getDbValue() + ".");
    }

    private void showParticipants() {
        SchoolEvent event = chooseEvent();
        List<EventParticipant> participants = controller.getParticipants(event);
        System.out.println("\n" + event.name() + " - " + event.date() + (event.competition() ? " (con competencia)" : ""));
        if (participants.isEmpty()) {
            System.out.println("  (sin participantes registrados)");
            return;
        }
        participants.forEach(p -> System.out.printf("  %-12s %-34s %-13s %s%n", p.studentDocument(), p.studentName(),
                p.role().getDbValue(), p.position() == null ? "-" : p.position().getDbValue()));
        System.out.println("\nTotal de participantes: " + participants.size());
    }

    private void showStudentParticipation() throws BusinessException {
        Student student = controller.getStudentByDocument(InputValidator.readText("Documento del estudiante: "));
        List<StudentParticipation> participations = controller.getParticipations(student);
        System.out.println("\nParticipación de " + student.getFullName() + ":");
        if (participations.isEmpty()) {
            System.out.println("  (no ha participado en eventos)");
            return;
        }
        participations.forEach(p -> System.out.printf("  %s  %-44s %-12s %-13s %s%n", p.date(), p.eventName(),
                p.type().getDbValue(), p.role().getDbValue(), p.position() == null ? "-" : p.position().getDbValue()));
        System.out.println("\nResumen por tipo de evento:");
        controller.countByType(participations).forEach((type, count) -> System.out.println("  " + type.getDbValue() + ": " + count));
    }

    private SchoolEvent chooseEvent() {
        return ConsoleForm.choose("Evento", controller.listEvents(), e -> e.date() + " - " + e.name());
    }

    private LocalDate readEventDate() {
        while (true) {
            LocalDate date = InputValidator.readDate("Fecha del evento");
            try {
                controller.validateEventDate(date);
                return date;
            } catch (BusinessException e) {
                ErrorLogger.warn("Validation failed: " + e.getMessage());
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }
}