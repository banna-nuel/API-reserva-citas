package com.q.q.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.q.q.entity.enums.AppointmentStatus;

// Cliente, profesional y servicio van resumidos: id y nombre, nunca email ni teléfono
public record AppointmentResponse(Long id, ClientSummary client, ProfessionalSummary professional,
		ServiceSummary service, LocalDateTime startTime, LocalDateTime endTime, AppointmentStatus status,
		BigDecimal price, String notes, String cancelReason, LocalDateTime createdAt) {

	public record ClientSummary(Long id, String fullName) {}

	public record ProfessionalSummary(Long id, String fullName) {}        // id del perfil, no del usuario

	public record ServiceSummary(Long id, String name, int durationMinutes) {}

}
