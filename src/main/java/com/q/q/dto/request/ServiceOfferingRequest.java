package com.q.q.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

// Body de POST y PUT /services. Solo los campos editables: id y active se ignoran si vienen
public record ServiceOfferingRequest(
		@Schema(description = "Único sin distinguir mayúsculas ni tildes", example = "Consulta general")
		@NotBlank @Size(max = 100) String name,
		@Schema(description = "Opcional", example = "Valoración inicial")
		@Size(max = 500) String description,
		@Schema(description = "De 1 a 480 minutos", example = "30")
		@NotNull @Min(1) @Max(480) Integer durationMinutes,
		@Schema(description = "Mayor o igual que 0, con 2 decimales como máximo", example = "80000.00")
		@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price) {   // cabe en DECIMAL(10,2)

	// Los espacios sobrantes se quitan antes de validar: no cuentan para la longitud ni para la unicidad
	public ServiceOfferingRequest {
		name = name == null ? null : name.strip();
		description = description == null || description.isBlank() ? null : description.strip();
	}

}
