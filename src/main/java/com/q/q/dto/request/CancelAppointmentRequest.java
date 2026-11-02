package com.q.q.dto.request;

import jakarta.validation.constraints.Size;

// Body opcional de PATCH /appointments/{id}/cancel
public record CancelAppointmentRequest(@Size(max = 255) String reason) {

	// Los espacios sobrantes se quitan antes de validar: no cuentan para la longitud
	public CancelAppointmentRequest {
		reason = reason == null || reason.isBlank() ? null : reason.strip();
	}

}
