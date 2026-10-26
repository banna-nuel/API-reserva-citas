package com.q.q.dto.request;

import java.util.List;

import com.q.q.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body de POST /professionals: datos del usuario (mismas reglas que el registro) y del perfil.
// Sin role ni active: el alta siempre crea un PROFESSIONAL activo
public record CreateProfessionalRequest(
		@NotBlank @Size(max = 120) String fullName,
		@NotBlank @Email @Size(max = 150) String email,
		@NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String password,      // RN-11
		@Size(max = 30) String phone,
		@NotBlank @Size(max = 100) String specialty,
		@Size(max = 500) String bio,
		List<@NotNull Long> serviceIds) {                                           // opcional; los repetidos se ignoran

	// Los espacios sobrantes del perfil se quitan antes de validar: no cuentan para la longitud
	public CreateProfessionalRequest {
		specialty = specialty == null ? null : specialty.strip();
		bio = bio == null || bio.isBlank() ? null : bio.strip();
	}

	@Override
	public String toString() {
		return "CreateProfessionalRequest[fullName=" + fullName + ", email=" + email + ", password=****, phone=" + phone
				+ ", specialty=" + specialty + ", serviceIds=" + serviceIds + "]";
	}

}
