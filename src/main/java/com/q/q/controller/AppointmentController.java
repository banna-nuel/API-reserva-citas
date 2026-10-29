package com.q.q.controller;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.q.q.dto.request.CreateAppointmentRequest;
import com.q.q.dto.response.AppointmentResponse;
import com.q.q.dto.response.PageResponse;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.service.AppointmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

	private final AppointmentService appointmentService;

	// El rol se exige aquí y por URL (un PROFESSIONAL recibe 403 antes de validar el body)
	@PostMapping
	@PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
	public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
		AppointmentResponse created = appointmentService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);              // 201 + Location
	}

	// Cualquier usuario autenticado; qué citas ve lo decide el servicio según su rol (RN-13)
	@GetMapping
	public PageResponse<AppointmentResponse> list(@RequestParam(required = false) AppointmentStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long professionalId, Pageable pageable) {
		return appointmentService.list(status, from, to, professionalId, pageable);
	}

	@GetMapping("/{id}")
	public AppointmentResponse get(@PathVariable Long id) {
		return appointmentService.get(id);
	}

}
