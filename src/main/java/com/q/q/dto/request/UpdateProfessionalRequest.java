package com.q.q.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body de PUT /professionals/{id}: solo el perfil. fullName y phone son del usuario (PUT /users/me);
// email, userId y active se ignoran si vienen
public record UpdateProfessionalRequest(
		@NotBlank @Size(max = 100) String specialty,
		@Size(max = 500) String bio) {

	public UpdateProfessionalRequest {
		specialty = specialty == null ? null : specialty.strip();
		bio = bio == null || bio.isBlank() ? null : bio.strip();
	}

}
