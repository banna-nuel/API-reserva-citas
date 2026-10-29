package com.q.q.controller;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.q.q.dto.request.AssignServicesRequest;
import com.q.q.dto.request.CreateProfessionalRequest;
import com.q.q.dto.request.UpdateActiveRequest;
import com.q.q.dto.request.UpdateProfessionalRequest;
import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.ProfessionalAdminResponse;
import com.q.q.dto.response.ProfessionalView;
import com.q.q.service.ProfessionalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/professionals")
@RequiredArgsConstructor
public class ProfessionalController {

	private final ProfessionalService professionalService;

	@GetMapping
	public PageResponse<ProfessionalView> list(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Long serviceId, Pageable pageable) {
		return professionalService.list(active, serviceId, pageable);
	}

	@GetMapping("/{id}")
	public ProfessionalView get(@PathVariable Long id) {
		return professionalService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ProfessionalAdminResponse> create(@Valid @RequestBody CreateProfessionalRequest request) {
		ProfessionalAdminResponse created = professionalService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);              // 201 + Location
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ProfessionalAdminResponse update(@PathVariable Long id,
			@Valid @RequestBody UpdateProfessionalRequest request) {
		return professionalService.update(id, request);
	}

	@PatchMapping("/{id}/active")
	@PreAuthorize("hasRole('ADMIN')")
	public ProfessionalAdminResponse setActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
		return professionalService.setActive(id, request.active());
	}

	@PutMapping("/{id}/services")
	@PreAuthorize("hasRole('ADMIN')")
	public ProfessionalAdminResponse replaceServices(@PathVariable Long id,
			@Valid @RequestBody AssignServicesRequest request) {
		return professionalService.replaceServices(id, request);
	}

}
