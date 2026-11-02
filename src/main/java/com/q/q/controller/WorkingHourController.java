package com.q.q.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.q.q.dto.request.WorkingHoursRequest;
import com.q.q.dto.response.WorkingHoursResponse;
import com.q.q.service.WorkingHourService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/professionals/{professionalId}/working-hours")
@RequiredArgsConstructor
@Tag(name = "Profesionales")
public class WorkingHourController {

	private final WorkingHourService workingHourService;

	@GetMapping
	@Operation(summary = "Ver el horario laboral", description = "Público. Bloques ordenados por día y hora.")
	@ApiResponse(responseCode = "200", description = "Horario")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: el id no es un número")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	public WorkingHoursResponse get(@PathVariable Long professionalId) {
		return workingHourService.get(professionalId);
	}

	// Acceso mixto: el rol se exige aquí y por URL; que el PROFESSIONAL sea el dueño lo comprueba el servicio
	@PutMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'PROFESSIONAL')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Reemplazar el horario laboral (ADMIN o el propio profesional)",
			description = "Reemplaza el horario completo; { \"blocks\": [] } lo borra.")
	@ApiResponse(responseCode = "200", description = "Horario resultante")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, MALFORMED_REQUEST o INVALID_PARAMETER")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED (CLIENT) o FORBIDDEN_OPERATION (otro profesional o perfil inactivo)")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	@ApiResponse(responseCode = "422", description = "WORKING_HOURS_OVERLAP")
	public WorkingHoursResponse replace(@PathVariable Long professionalId,
			@Valid @RequestBody WorkingHoursRequest request) {
		return workingHourService.replace(professionalId, request);
	}

}
