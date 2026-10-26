package com.q.q.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body de POST y PUT /services. Solo los campos editables: id y active se ignoran si vienen
public record ServiceOfferingRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 500) String description,
		@NotNull @Min(1) @Max(480) Integer durationMinutes,
		@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price) {   // cabe en DECIMAL(10,2)

	// Los espacios sobrantes se quitan antes de validar: no cuentan para la longitud ni para la unicidad
	public ServiceOfferingRequest {
		name = name == null ? null : name.strip();
		description = description == null || description.isBlank() ? null : description.strip();
	}

}
