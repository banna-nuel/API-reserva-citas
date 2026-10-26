package com.q.q.controller;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.q.q.dto.request.ServiceOfferingRequest;
import com.q.q.dto.request.UpdateActiveRequest;
import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.ServiceOfferingResponse;
import com.q.q.service.ServiceOfferingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceOfferingController {

	private final ServiceOfferingService serviceOfferingService;

	@GetMapping
	public PageResponse<ServiceOfferingResponse> list(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String q, Pageable pageable) {
		return serviceOfferingService.list(active, q, pageable);
	}

	@GetMapping("/{id}")
	public ServiceOfferingResponse get(@PathVariable Long id) {
		return serviceOfferingService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ServiceOfferingResponse> create(@Valid @RequestBody ServiceOfferingRequest request) {
		ServiceOfferingResponse created = serviceOfferingService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);              // 201 + Location
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ServiceOfferingResponse update(@PathVariable Long id, @Valid @RequestBody ServiceOfferingRequest request) {
		return serviceOfferingService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable Long id) {
		serviceOfferingService.deactivate(id);
	}

	@PatchMapping("/{id}/active")
	@PreAuthorize("hasRole('ADMIN')")
	public ServiceOfferingResponse setActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
		return serviceOfferingService.setActive(id, request.active());
	}

}
