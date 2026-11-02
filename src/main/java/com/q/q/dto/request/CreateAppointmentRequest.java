package com.q.q.dto.request;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

// Body de POST /appointments. id, status, price, endTime y cualquier otro campo se ignoran
public record CreateAppointmentRequest(
		@Schema(description = "Id del perfil profesional", example = "2") @NotNull Long professionalId,
		@Schema(example = "1") @NotNull Long serviceId,
		// Hora local del negocio sin zona. Sin lenient=false, Jackson quitaría la "Z" de "...T10:00:00Z" y tomaría
		// como hora local una hora UTC
		@NotNull @JsonFormat(lenient = OptBoolean.FALSE) LocalDateTime startTime,
		@Size(max = 500) String notes,
		Long clientId) {                     // solo lo usa el ADMIN, para reservar en nombre de un cliente

	// Los espacios sobrantes se quitan antes de validar: no cuentan para la longitud
	public CreateAppointmentRequest {
		notes = notes == null || notes.isBlank() ? null : notes.strip();
	}

}
