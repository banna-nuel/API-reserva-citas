package com.q.q.entity.enums;

// Ciclo de vida de la sección 4.4. PENDING y CONFIRMED son las citas activas: ocupan horario (RN-02) y se pueden
// reprogramar
public enum AppointmentStatus {
	PENDING, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW;

	// RN-07: solo las transiciones del diagrama. CANCELLED, COMPLETED y NO_SHOW son estados finales
	public boolean canTransitionTo(AppointmentStatus next) {
		return switch (this) {
			case PENDING -> next == CONFIRMED || next == CANCELLED;
			case CONFIRMED -> next == COMPLETED || next == CANCELLED || next == NO_SHOW;
			case CANCELLED, COMPLETED, NO_SHOW -> false;
		};
	}

	public boolean isActive() {
		return this == PENDING || this == CONFIRMED;
	}

}
