package com.q.q.entity.enums;

// Ciclo de vida de la sección 4.4. PENDING y CONFIRMED son las citas activas (ocupan horario, RN-02);
// las transiciones (canTransitionTo) llegan en la Fase 6
public enum AppointmentStatus {
	PENDING, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW
}
