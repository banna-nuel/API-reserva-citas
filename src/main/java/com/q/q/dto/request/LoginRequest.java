package com.q.q.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
		@Schema(description = "Sin distinguir mayúsculas", example = "ana@example.com")
		@NotBlank @Email String email,
		@Schema(example = "una-clave-segura")
		@NotBlank String password) {

	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=****]";
	}

}
