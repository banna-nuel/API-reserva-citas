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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/professionals/{professionalId}/availability")
@RequiredArgsConstructor
public class AvailabilityController {

	private final AvailabilityService availabilityService;

	// Público: GET /professionals/** ya es permitAll en SecurityConfig. Sin ISO.DATE, Spring esperaría un
	// formato de fecha que depende del idioma
	@GetMapping
	public AvailabilityResponse get(@PathVariable Long professionalId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam Long serviceId) {
		return availabilityService.find(professionalId, date, serviceId);
	}

}
