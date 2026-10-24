package com.q.q.dto.request;

import com.q.q.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sin role ni active: el registro público siempre crea un CLIENT activo
public record RegisterRequest(
		@NotBlank @Size(max = 120) String fullName,
		@NotBlank @Email @Size(max = 150) String email,
		@NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String password,      // RN-11
		@Size(max = 30) String phone) {

	@Override
	public String toString() {
		return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=****, phone=" + phone + "]";
	}

}
