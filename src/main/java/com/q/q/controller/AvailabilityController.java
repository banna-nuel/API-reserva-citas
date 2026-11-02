package com.q.q.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.q.q.dto.response.AvailabilityResponse;
import com.q.q.service.AvailabilityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/professionals/{professionalId}/availability")
@RequiredArgsConstructor
@Tag(name = "Profesionales")
public class AvailabilityController {

	private final AvailabilityService availabilityService;

	// Público: GET /professionals/** ya es permitAll en SecurityConfig. Sin ISO.DATE, Spring esperaría un
	// formato de fecha que depende del idioma
	@GetMapping
	@Operation(summary = "Horarios libres de un día",
			description = "Público. date en YYYY-MM-DD. Una fecha pasada o tras el tope de reserva da 200 con slots vacíos.")
	@ApiResponse(responseCode = "200", description = "Horarios libres, ordenados")
	@ApiResponse(responseCode = "400", description = "MISSING_PARAMETER o INVALID_PARAMETER")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: profesional o servicio inexistente o inactivo")
	@ApiResponse(responseCode = "422", description = "SERVICE_NOT_OFFERED")
	public AvailabilityResponse get(@PathVariable Long professionalId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam Long serviceId) {
		return availabilityService.find(professionalId, date, serviceId);
	}

}
