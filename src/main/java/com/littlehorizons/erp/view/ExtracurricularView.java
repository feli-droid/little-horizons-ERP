package com.littlehorizons.erp.view;

import com.littlehorizons.erp.util.InputValidator;

/** Menu of the extracurricular module: clubs and events. */
public class ExtracurricularView {

    private final ClubView clubView;
    private final EventView eventView;

    public ExtracurricularView(ClubView clubView, EventView eventView) {
        this.clubView = clubView;
        this.eventView = eventView;
    }

    public void run() {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║   ACTIVIDADES EXTRACURRICULARES              ║");
            System.out.println("╚══════════════════════════════════════════════╝");
            System.out.println("1. Clubes");
            System.out.println("2. Eventos");
            System.out.println("0. Volver al menú principal");
            int option = InputValidator.readInt("Seleccione una opción: ", 0, 2);
            switch (option) {
                case 1 -> clubView.run();
                case 2 -> eventView.run();
                default -> back = true;
            }
        }
    }
}