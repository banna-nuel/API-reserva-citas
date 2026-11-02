package com.q.q.dto.request;

import com.q.q.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

// Sin role ni active: el registro público siempre crea un CLIENT activo
@Schema(description = "Registro público: siempre crea un CLIENT")
public record RegisterRequest(
		@Schema(description = "Nombre completo", example = "Ana Pérez")
		@NotBlank @Size(max = 120) String fullName,
		@Schema(description = "Se guarda en minúsculas; único", example = "ana@example.com")
		@NotBlank @Email @Size(max = 150) String email,
		@Schema(description = "De 8 a 72 caracteres y como máximo 72 bytes", example = "una-clave-segura")
		@NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String password,      // RN-11
		@Schema(description = "Opcional", example = "3001234567")
		@Size(max = 30) String phone) {

	@Override
	public String toString() {
		return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=****, phone=" + phone + "]";
	}

}
