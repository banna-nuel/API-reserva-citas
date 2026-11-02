package com.q.q.exception;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Formato único de error (sección 8.4 de docs/GUIA.md)")
public record ApiError(
		@Schema(example = "2026-11-05T10:00:00") LocalDateTime timestamp,
		@Schema(example = "409") int status,
		@Schema(example = "Conflict") String error,
		@Schema(description = "Estable y legible por máquinas: el cliente decide con él", example = "SLOT_NOT_AVAILABLE")
		String code,
		@Schema(example = "El profesional ya tiene una cita en ese horario") String message,
		@Schema(example = "/api/v1/appointments") String path,
		@Schema(description = "Campos inválidos (400) o elementos en conflicto (algunos 422)") List<FieldIssue> details) {

	public record FieldIssue(String field, String message) {}

	// Formato de la sección 8.4: hora del negocio sin fracciones de segundo ("2026-10-23T17:05:12")
	public static ApiError of(LocalDateTime timestamp, HttpStatus status, String code, String message,
			String path, List<FieldIssue> details) {
		return new ApiError(timestamp.truncatedTo(ChronoUnit.SECONDS), status.value(), status.getReasonPhrase(),
				code, message, path, details);
	}

}
