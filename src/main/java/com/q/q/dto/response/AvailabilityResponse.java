package com.q.q.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Horarios libres de un profesional para un servicio en un día: solo slots, nunca las citas ni sus clientes
public record AvailabilityResponse(Long professionalId, Long serviceId, LocalDate date, int durationMinutes,
		List<Slot> slots) {

	public record Slot(LocalDateTime start, LocalDateTime end) {}           // hora local del negocio

}
