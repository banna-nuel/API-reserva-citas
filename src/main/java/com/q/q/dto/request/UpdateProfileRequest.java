package com.q.q.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Solo los campos editables: email (subject del JWT), role y active no se cambian aquí
public record UpdateProfileRequest(
		@NotBlank @Size(max = 120) String fullName,
		@Size(max = 30) String phone) {}
