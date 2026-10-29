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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/professionals/{professionalId}/working-hours")
@RequiredArgsConstructor
public class WorkingHourController {

	private final WorkingHourService workingHourService;

	@GetMapping
	public WorkingHoursResponse get(@PathVariable Long professionalId) {
		return workingHourService.get(professionalId);
	}

	// Acceso mixto: el rol se exige aquí y por URL; que el PROFESSIONAL sea el dueño lo comprueba el servicio
	@PutMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'PROFESSIONAL')")
	public WorkingHoursResponse replace(@PathVariable Long professionalId,
			@Valid @RequestBody WorkingHoursRequest request) {
		return workingHourService.replace(professionalId, request);
	}

}
