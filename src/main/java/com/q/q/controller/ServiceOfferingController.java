package com.q.q.controller;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Las lecturas son públicas (sin @SecurityRequirement); las escrituras, solo ADMIN
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
@Tag(name = "Servicios", description = "Catálogo de servicios: lectura pública y gestión del ADMIN")
public class ServiceOfferingController {

	private final ServiceOfferingService serviceOfferingService;

	@GetMapping
	@Operation(summary = "Listar servicios",
			description = "Público: solo activos. El ADMIN ve todos y puede filtrar por active. q busca en el nombre.")
	@ApiResponse(responseCode = "200", description = "Página de servicios")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER o INVALID_SORT")
	public PageResponse<ServiceOfferingResponse> list(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String q, @ParameterObject Pageable pageable) {
		return serviceOfferingService.list(active, q, pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalle de un servicio", description = "Uno inactivo da 404, salvo al ADMIN.")
	@ApiResponse(responseCode = "200", description = "Servicio")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: el id no es un número")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	public ServiceOfferingResponse get(@PathVariable Long id) {
		return serviceOfferingService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)                   // solo para la documentación: el ResponseEntity ya es 201
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Crear un servicio (ADMIN)", description = "El nombre es único sin distinguir mayúsculas ni tildes.")
	@ApiResponse(responseCode = "201", description = "Servicio creado; Location apunta a su detalle")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o MALFORMED_REQUEST")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	@ApiResponse(responseCode = "409", description = "SERVICE_NAME_ALREADY_EXISTS")
	public ResponseEntity<ServiceOfferingResponse> create(@Valid @RequestBody ServiceOfferingRequest request) {
		ServiceOfferingResponse created = serviceOfferingService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);              // 201 + Location
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Reemplazar un servicio (ADMIN)", description = "No cambia active ni el precio de las citas ya reservadas.")
	@ApiResponse(responseCode = "200", description = "Servicio actualizado")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, MALFORMED_REQUEST o INVALID_PARAMETER")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	@ApiResponse(responseCode = "409", description = "SERVICE_NAME_ALREADY_EXISTS")
	public ServiceOfferingResponse update(@PathVariable Long id, @Valid @RequestBody ServiceOfferingRequest request) {
		return serviceOfferingService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Desactivar un servicio (ADMIN)", description = "Borrado lógico e idempotente.")
	@ApiResponse(responseCode = "204", description = "Servicio desactivado")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: el id no es un número")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	public void deactivate(@PathVariable Long id) {
		serviceOfferingService.deactivate(id);
	}

	@PatchMapping("/{id}/active")
	@PreAuthorize("hasRole('ADMIN')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Activar o desactivar un servicio (ADMIN)", description = "Body { \"active\": true }.")
	@ApiResponse(responseCode = "200", description = "Servicio actualizado")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, MALFORMED_REQUEST o INVALID_PARAMETER")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	public ServiceOfferingResponse setActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
		return serviceOfferingService.setActive(id, request.active());
	}

}
