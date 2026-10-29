package com.q.q.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.q.q.config.AppProperties;
import com.q.q.dto.request.CreateAppointmentRequest;
import com.q.q.dto.response.AppointmentResponse;
import com.q.q.dto.response.PageResponse;
import com.q.q.entity.Appointment;
import com.q.q.entity.Professional;
import com.q.q.entity.ServiceOffering;
import com.q.q.entity.User;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.entity.enums.Role;
import com.q.q.exception.BadRequestException;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ConflictException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.AppointmentMapper;
import com.q.q.repository.AppointmentRepository;
import com.q.q.repository.AppointmentSpecifications;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.ServiceOfferingRepository;
import com.q.q.repository.UserRepository;
import com.q.q.security.AuthUser;
import com.q.q.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppointmentService {

	// Campos por los que GET /appointments puede ordenar; sin sort, por startTime ascendente
	private static final List<String> SORTABLE_FIELDS = List.of("id", "startTime", "status", "price", "createdAt");
	// Rango de DATETIME en MySQL: fuera de él no hay citas, así que esos límites no filtran nada
	private static final LocalDate FIRST_STORABLE_DAY = LocalDate.of(1000, 1, 1);
	private static final LocalDate LAST_STORABLE_DAY = LocalDate.of(9999, 12, 31);

	private final AppointmentRepository appointments;
	private final ProfessionalRepository professionals;
	private final ServiceOfferingRepository services;
	private final UserRepository users;
	private final WorkingHourService workingHours;
	private final AppointmentMapper mapper;
	private final AppProperties props;
	private final Clock clock;

	// READ_COMMITTED + bloqueo de filas: si dos reservas chocan, la segunda espera a que termine la primera y
	// entonces ya "ve" su cita en los conteos de solape. Orden de comprobaciones: sección 8.3
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public AppointmentResponse create(CreateAppointmentRequest req) {
		AuthUser actor = CurrentUser.get().orElseThrow(AppointmentService::notAllowed);
		if (actor.role() != Role.CLIENT && actor.role() != Role.ADMIN) {
			throw notAllowed();                      // defensa en profundidad: SecurityConfig y @PreAuthorize ya lo exigen
		}

		// Siempre primero la fila del profesional y después la del cliente (también en la Fase 6b): con un orden
		// fijo, dos reservas no pueden quedar esperándose la una a la otra
		Professional professional = professionals.findByIdForUpdate(req.professionalId())
				.filter(Professional::isPubliclyVisible)
				.orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado"));
		ServiceOffering service = services.findById(req.serviceId())
				.filter(ServiceOffering::isActive)
				.orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));
		if (!professional.offers(service)) {
			throw new BusinessRuleException("SERVICE_NOT_OFFERED", "El profesional no ofrece este servicio");   // RN-05
		}
		User client = lockClient(actor, req.clientId());

		LocalDateTime start = req.startTime();
		BookingWindow window = BookingWindow.at(LocalDateTime.now(clock), props.booking());     // RN-04
		if (window.isTooSoon(start)) {
			throw new BusinessRuleException("TOO_SOON", "La cita requiere más anticipación");
		}
		if (window.isTooFar(start)) {
			throw new BusinessRuleException("TOO_FAR", "No se permiten reservas tan lejanas");
		}
		LocalDateTime end = start.plusMinutes(service.getDurationMinutes());                       // RN-01
		List<TimeRange> ranges = workingHours.workingRanges(professional.getId(), start.getDayOfWeek());
		if (ranges.stream().noneMatch(range -> range.fits(start, end))) {                          // RN-03
			throw new BusinessRuleException("OUTSIDE_WORKING_HOURS", "Fuera de la jornada del profesional");
		}
		if (!isOfferedStart(start, ranges, service.getDurationMinutes())) {
			throw new BusinessRuleException("INVALID_SLOT_START",
					"La hora de inicio no es uno de los horarios que ofrece la disponibilidad");
		}
		if (appointments.countOverlapping(professional.getId(), start, end, null) > 0) {          // RN-02
			throw new ConflictException("SLOT_NOT_AVAILABLE", "El profesional ya tiene una cita en ese horario");
		}
		if (appointments.countOverlappingForClient(client.getId(), start, end, null) > 0) {       // RN-06
			throw new ConflictException("CLIENT_OVERLAP", "El cliente ya tiene una cita en ese horario");
		}

		Appointment appointment = new Appointment();
		appointment.setClient(client);
		appointment.setProfessional(professional);
		appointment.setService(service);
		appointment.setStartTime(start);
		appointment.setEndTime(end);
		appointment.setStatus(AppointmentStatus.PENDING);
		appointment.setPrice(service.getPrice());                    // precio congelado al reservar (RN-09)
		appointment.setNotes(req.notes());
		return mapper.toResponse(appointments.save(appointment));
	}

	// RN-13: el CLIENT solo ve sus citas, el PROFESSIONAL las de su agenda y el ADMIN todas. Los filtros se suman
	// a ese alcance, así que uno que apunta fuera devuelve una página vacía y no un 403
	@Transactional(readOnly = true)
	public PageResponse<AppointmentResponse> list(AppointmentStatus status, LocalDate from, LocalDate to,
			Long professionalId, Pageable pageable) {
		AuthUser actor = CurrentUser.get().orElseThrow(AppointmentService::notAllowed);
		if (from != null && to != null && from.isAfter(to)) {
			throw new BadRequestException("INVALID_DATE_RANGE", "'from' no puede ser posterior a 'to'");
		}
		Pageable sorted = pageable.getSort().isSorted()
				? pageable
				: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("startTime"));
		Pageable safePageable = SafeSort.apply(sorted, SORTABLE_FIELDS);

		List<Specification<Appointment>> filters = new ArrayList<>();
		switch (actor.role()) {
			case CLIENT -> filters.add(AppointmentSpecifications.clientIs(actor.id()));
			case PROFESSIONAL -> filters.add(AppointmentSpecifications.professionalUserIs(actor.id()));
			case ADMIN -> { }
		}
		if (status != null) {
			filters.add(AppointmentSpecifications.statusIs(status));
		}
		if (professionalId != null) {
			filters.add(AppointmentSpecifications.professionalIs(professionalId));
		}
		// from y to son inclusivas sobre el día de startTime
		if (from != null && from.isAfter(FIRST_STORABLE_DAY)) {
			filters.add(AppointmentSpecifications.startsAtOrAfter(min(from, LAST_STORABLE_DAY).atStartOfDay()));
		}
		if (to != null && to.isBefore(LAST_STORABLE_DAY)) {
			filters.add(AppointmentSpecifications.startsBefore(max(to, FIRST_STORABLE_DAY).plusDays(1).atStartOfDay()));
		}
		return PageResponse.from(appointments.findAll(Specification.allOf(filters), safePageable)
				.map(mapper::toResponse));
	}

	// RN-13: quien no es el cliente, el profesional asignado ni ADMIN recibe 404, como si la cita no existiera
	@Transactional(readOnly = true)
	public AppointmentResponse get(Long id) {
		AuthUser actor = CurrentUser.get().orElseThrow(AppointmentService::notAllowed);
		Appointment appointment = appointments.findWithDetailsById(id)
				.filter(a -> canSee(actor, a))
				.orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada"));
		return mapper.toResponse(appointment);
	}

	// Un CLIENT reserva siempre para sí mismo (su clientId se ignora). El ADMIN puede hacerlo en nombre de un cliente
	// activo o, sin clientId, para sí mismo. La fila queda bloqueada hasta el final: RN-06 con reservas simultáneas
	private User lockClient(AuthUser actor, Long requestedClientId) {
		if (actor.role() == Role.ADMIN && requestedClientId != null) {
			return users.findByIdForUpdate(requestedClientId)
					.filter(user -> user.getRole() == Role.CLIENT && user.isActive())
					.orElseThrow(() -> new BusinessRuleException("INVALID_CLIENT_ID",
							"El cliente no existe, no tiene rol CLIENT o está inactivo"));
		}
		return users.findByIdForUpdate(actor.id()).orElseThrow(AppointmentService::notAllowed);
	}

	// El inicio debe ser uno de los horarios que ofrecería la disponibilidad: el mismo cálculo, no una copia
	private boolean isOfferedStart(LocalDateTime start, List<TimeRange> ranges, int durationMinutes) {
		int step = props.booking().slotStepMinutes();
		return ranges.stream()
				.flatMap(range -> range.candidateSlots(start.toLocalDate(), durationMinutes, step).stream())
				.anyMatch(slot -> slot.start().equals(start));       // equals compara también segundos y nanos
	}

	private static boolean canSee(AuthUser actor, Appointment appointment) {
		return switch (actor.role()) {
			case ADMIN -> true;
			case CLIENT -> appointment.getClient().getId().equals(actor.id());
			case PROFESSIONAL -> appointment.getProfessional().getUser().getId().equals(actor.id());
		};
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

	private static LocalDate max(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

	private static ForbiddenOperationException notAllowed() {
		return new ForbiddenOperationException("Solo un cliente o un administrador pueden reservar");
	}

}
