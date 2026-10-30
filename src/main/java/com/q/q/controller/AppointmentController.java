package com.q.q.controller;

import java.net.URI;
import java.time.LocalDate;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.q.q.dto.request.CancelAppointmentRequest;
import com.q.q.dto.request.CreateAppointmentRequest;
import com.q.q.dto.request.RescheduleAppointmentRequest;
import com.q.q.dto.response.AppointmentResponse;
import com.q.q.dto.response.PageResponse;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.service.AppointmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Todo /appointments exige token
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Citas", description = "Reservar, consultar y cambiar el estado de las citas")
@SecurityRequirement(name = "bearerAuth")
public class AppointmentController {

	private final AppointmentService appointmentService;

	// El rol se exige aquí y por URL (un PROFESSIONAL recibe 403 antes de validar el body)
	@PostMapping
	@PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)                   // solo para la documentación: el ResponseEntity ya es 201
	@Operation(summary = "Reservar una cita (CLIENT o ADMIN)",
			description = "startTime debe ser uno de los horarios de la disponibilidad. El ADMIN puede reservar en nombre de un cliente con clientId.")
	@ApiResponse(responseCode = "201", description = "Cita creada en PENDING; Location apunta a su detalle")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o MALFORMED_REQUEST")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: un PROFESSIONAL no reserva")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: profesional o servicio inexistente o inactivo")
	@ApiResponse(responseCode = "409", description = "SLOT_NOT_AVAILABLE o CLIENT_OVERLAP")
	@ApiResponse(responseCode = "422", description = "SERVICE_NOT_OFFERED, INVALID_CLIENT_ID, TOO_SOON, TOO_FAR, "
			+ "OUTSIDE_WORKING_HOURS o INVALID_SLOT_START")
	public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
		AppointmentResponse created = appointmentService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);              // 201 + Location
	}

	// Cualquier usuario autenticado; qué citas ve lo decide el servicio según su rol (RN-13)
	@GetMapping
	@Operation(summary = "Listar citas",
			description = "CLIENT: las suyas; PROFESSIONAL: su agenda; ADMIN: todas. from y to son inclusivas sobre el día de startTime.")
	@ApiResponse(responseCode = "200", description = "Página de citas, por startTime ascendente por defecto")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER, INVALID_DATE_RANGE o INVALID_SORT")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	public PageResponse<AppointmentResponse> list(@RequestParam(required = false) AppointmentStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long professionalId, @ParameterObject Pageable pageable) {
		return appointmentService.list(status, from, to, professionalId, pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalle de una cita", description = "La ven su cliente, el profesional asignado y el ADMIN.")
	@ApiResponse(responseCode = "200", description = "Cita")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: el id no es un número")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no existe o no es suya")
	public AppointmentResponse get(@PathVariable Long id) {
		return appointmentService.get(id);
	}

	// confirm, complete y no-show: el rol se exige aquí y por URL; que sea el profesional asignado, en el servicio
	@PatchMapping("/{id}/confirm")
	@PreAuthorize("hasAnyRole('PROFESSIONAL', 'ADMIN')")
	@Operation(summary = "Confirmar (profesional asignado o ADMIN)", description = "PENDING → CONFIRMED.")
	@ApiResponse(responseCode = "200", description = "Cita confirmada")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: el id no es un número")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: un CLIENT no confirma")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no existe o no es de su agenda")
	@ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION")
	public AppointmentResponse confirm(@PathVariable Long id) {
		return appointmentService.confirm(id);
	}

	@PatchMapping("/{id}/complete")
	@PreAuthorize("hasAnyRole('PROFESSIONAL', 'ADMIN')")
	@Operation(summary = "Marcar completada (profesional asignado o ADMIN)", description = "CONFIRMED → COMPLETED, cuando ya empezó.")
	@ApiResponse(responseCode = "200", description = "Cita completada")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: el id no es un número")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: un CLIENT no completa")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no existe o no es de su agenda")
	@ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION")
	@ApiResponse(responseCode = "422", description = "APPOINTMENT_NOT_STARTED")
	public AppointmentResponse complete(@PathVariable Long id) {
		return appointmentService.complete(id);
	}

	@PatchMapping("/{id}/no-show")
	@PreAuthorize("hasAnyRole('PROFESSIONAL', 'ADMIN')")
	@Operation(summary = "Marcar inasistencia (profesional asignado o ADMIN)", description = "CONFIRMED → NO_SHOW, cuando ya empezó.")
	@ApiResponse(responseCode = "200", description = "Cita marcada como inasistencia")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: el id no es un número")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: un CLIENT no la marca")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no existe o no es de su agenda")
	@ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION")
	@ApiResponse(responseCode = "422", description = "APPOINTMENT_NOT_STARTED")
	public AppointmentResponse markNoShow(@PathVariable Long id) {
		return appointmentService.markNoShow(id);
	}

	// Cualquier usuario autenticado; el servicio comprueba que sea su cliente, el profesional asignado o ADMIN.
	// El body es opcional: sin él, la cita se cancela sin motivo
	@PatchMapping("/{id}/cancel")
	@Operation(summary = "Cancelar (su cliente, el profesional asignado o ADMIN)",
			description = "Body opcional { \"reason\": \"...\" }. El CLIENT solo hasta cancel-limit-hours antes del inicio.")
	@ApiResponse(responseCode = "200", description = "Cita cancelada")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, MALFORMED_REQUEST o INVALID_PARAMETER")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no existe o no es suya")
	@ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION")
	@ApiResponse(responseCode = "422", description = "CANCELLATION_WINDOW_CLOSED")
	public AppointmentResponse cancel(@PathVariable Long id,
			@Valid @RequestBody(required = false) CancelAppointmentRequest request) {
		return appointmentService.cancel(id, request == null ? null : request.reason());
	}

	@PatchMapping("/{id}/reschedule")
	@PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
	@Operation(summary = "Reprogramar (su cliente o ADMIN)",
			description = "Mismas reglas que la reserva, sin contar la propia cita. Conserva estado, precio y duración.")
	@ApiResponse(responseCode = "200", description = "Cita reprogramada")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, MALFORMED_REQUEST o INVALID_PARAMETER")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: un PROFESSIONAL no reprograma")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: cita, profesional o servicio")
	@ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION, SLOT_NOT_AVAILABLE o CLIENT_OVERLAP")
	@ApiResponse(responseCode = "422", description = "RESCHEDULE_WINDOW_CLOSED, SAME_START_TIME o las reglas de horario de la reserva")
	public AppointmentResponse reschedule(@PathVariable Long id,
			@Valid @RequestBody RescheduleAppointmentRequest request) {
		return appointmentService.reschedule(id, request);
	}

}
