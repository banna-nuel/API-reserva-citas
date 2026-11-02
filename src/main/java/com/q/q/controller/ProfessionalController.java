package com.q.q.controller;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Las lecturas son públicas (sin @SecurityRequirement); las escrituras, solo ADMIN
@RestController
@RequestMapping("/api/v1/professionals")
@RequiredArgsConstructor
@Tag(name = "Profesionales", description = "Profesionales, sus servicios, su horario laboral y su disponibilidad")
public class ProfessionalController {

	private final ProfessionalService professionalService;

	@GetMapping
	@Operation(summary = "Listar profesionales",
			description = "Público: perfil y usuario activos, vista sin datos de contacto. El ADMIN ve la vista completa y filtra por active.")
	@ApiResponse(responseCode = "200", description = "Página de profesionales")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER o INVALID_SORT")
	public PageResponse<ProfessionalView> list(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Long serviceId, @ParameterObject Pageable pageable) {
		return professionalService.list(active, serviceId, pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalle de un profesional", description = "Con el perfil o el usuario inactivo da 404, salvo al ADMIN.")
	@ApiResponse(responseCode = "200", description = "Profesional")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: el id no es un número")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	public ProfessionalView get(@PathVariable Long id) {
		return professionalService.get(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)                   // solo para la documentación: el ResponseEntity ya es 201
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Dar de alta un profesional (ADMIN)", description = "Crea el usuario PROFESSIONAL y su perfil en una transacción.")
	@ApiResponse(responseCode = "201", description = "Profesional creado; Location apunta a su detalle")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o MALFORMED_REQUEST")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	@ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_USED")
	@ApiResponse(responseCode = "422", description = "INVALID_SERVICE_IDS")
	public ResponseEntity<ProfessionalAdminResponse> create(@Valid @RequestBody CreateProfessionalRequest request) {
		ProfessionalAdminResponse created = professionalService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);              // 201 + Location
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Reemplazar specialty y bio (ADMIN)", description = "No cambia active ni los datos del usuario.")
	@ApiResponse(responseCode = "200", description = "Profesional actualizado")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, MALFORMED_REQUEST o INVALID_PARAMETER")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	public ProfessionalAdminResponse update(@PathVariable Long id,
			@Valid @RequestBody UpdateProfessionalRequest request) {
		return professionalService.update(id, request);
	}

	@PatchMapping("/{id}/active")
	@PreAuthorize("hasRole('ADMIN')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Activar o desactivar el perfil (ADMIN)", description = "No desactiva al usuario: puede seguir iniciando sesión.")
	@ApiResponse(responseCode = "200", description = "Profesional actualizado")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, MALFORMED_REQUEST o INVALID_PARAMETER")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	public ProfessionalAdminResponse setActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
		return professionalService.setActive(id, request.active());
	}

	@PutMapping("/{id}/services")
	@PreAuthorize("hasRole('ADMIN')")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Reemplazar los servicios que ofrece (ADMIN)", description = "Body { \"serviceIds\": [1, 2] }; [] los quita todos.")
	@ApiResponse(responseCode = "200", description = "Profesional actualizado")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, MALFORMED_REQUEST o INVALID_PARAMETER")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	@ApiResponse(responseCode = "422", description = "INVALID_SERVICE_IDS")
	public ProfessionalAdminResponse replaceServices(@PathVariable Long id,
			@Valid @RequestBody AssignServicesRequest request) {
		return professionalService.replaceServices(id, request);
	}

}
