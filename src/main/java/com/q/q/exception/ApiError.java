package com.q.q.exception;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;

public record ApiError(LocalDateTime timestamp, int status, String error, String code,
		String message, String path, List<FieldIssue> details) {

	public record FieldIssue(String field, String message) {}

	// Formato de la sección 8.4: hora del negocio sin fracciones de segundo ("2026-10-23T17:05:12")
	public static ApiError of(LocalDateTime timestamp, HttpStatus status, String code, String message,
			String path, List<FieldIssue> details) {
		return new ApiError(timestamp.truncatedTo(ChronoUnit.SECONDS), status.value(), status.getReasonPhrase(),
				code, message, path, details);
	}

}
