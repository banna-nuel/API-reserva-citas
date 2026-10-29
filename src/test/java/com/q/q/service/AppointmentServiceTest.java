package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.q.q.config.AppProperties;
import com.q.q.dto.request.CreateAppointmentRequest;
import com.q.q.dto.response.AppointmentResponse;
import com.q.q.entity.Appointment;
import com.q.q.entity.Professional;
import com.q.q.entity.ServiceOffering;
import com.q.q.entity.User;
import com.q.q.entity.WorkingHour;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.entity.enums.Role;
import com.q.q.exception.ApiException;
import com.q.q.exception.BadRequestException;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ConflictException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.AppointmentMapper;
import com.q.q.mapper.WorkingHourMapper;
import com.q.q.repository.AppointmentRepository;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.ServiceOfferingRepository;
import com.q.q.repository.UserRepository;
import com.q.q.repository.WorkingHourRepository;
import com.q.q.security.AuthUser;

// Reloj fijo: lunes 2 de noviembre de 2026, 08:00. app.booking como en application.yml: paso 30, aviso 60 min y
// tope 60 días, así que se puede reservar desde 2026-11-02T09:00 hasta 2027-01-01T08:00
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

	private static final ZoneId ZONE = ZoneId.of("America/Bogota");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
	private static final LocalDate THURSDAY = LocalDate.of(2026, 11, 5);
	private static final Long PROFESSIONAL_ID = 7L;
	private static final Long PROFESSIONAL_USER_ID = 50L;
	private static final Long SERVICE_ID = 3L;
	private static final Long CLIENT_ID = 20L;
	private static final Long ADMIN_ID = 1L;
	private static final BigDecimal PRICE = new BigDecimal("80000.00");

	@Mock
	private AppointmentRepository appointments;

	@Mock
	private ProfessionalRepository professionals;

	@Mock
	private ServiceOfferingRepository services;

	@Mock
	private UserRepository users;

	@Mock
	private WorkingHourRepository workingHours;

	private AppointmentService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
		AppProperties props = new AppProperties("America/Bogota", null, new AppProperties.Booking(30, 60, 2, 60), null);
		WorkingHourService workingHourService = new WorkingHourService(professionals, workingHours, new WorkingHourMapper());
		service = new AppointmentService(appointments, professionals, services, users, workingHourService,
				new AppointmentMapper(), props, clock);
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	// ---------- caso feliz ----------

	@Test
	void aClientBooksAPendingAppointmentWithTheFrozenPrice() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(THURSDAY, "09:00-12:00", "14:00-18:00");
		givenSaveAssignsId();
		LocalDateTime start = THURSDAY.atTime(10, 0);
		LocalDateTime end = THURSDAY.atTime(10, 30);

		AppointmentResponse response = service.create(new CreateAppointmentRequest(PROFESSIONAL_ID, SERVICE_ID, start,
				"  Primera visita  ", null));

		ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
		verify(appointments).save(saved.capture());
		Appointment appointment = saved.getValue();
		assertThat(appointment.getClient().getId()).isEqualTo(CLIENT_ID);
		assertThat(appointment.getProfessional().getId()).isEqualTo(PROFESSIONAL_ID);
		assertThat(appointment.getService().getId()).isEqualTo(SERVICE_ID);
		assertThat(appointment.getStartTime()).isEqualTo(start);
		assertThat(appointment.getEndTime()).isEqualTo(end);                    // RN-01
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);
		assertThat(appointment.getPrice()).isEqualTo(PRICE);                    // RN-09
		assertThat(appointment.getNotes()).isEqualTo("Primera visita");
		// Los dos conteos de solape, con el rango de la cita y sin excluir ninguna (eso es para reprogramar)
		verify(appointments).countOverlapping(PROFESSIONAL_ID, start, end, null);
		verify(appointments).countOverlappingForClient(CLIENT_ID, start, end, null);

		assertThat(response.id()).isEqualTo(41L);
		assertThat(response.client()).isEqualTo(new AppointmentResponse.ClientSummary(CLIENT_ID, "Ana Pérez"));
		assertThat(response.professional()).isEqualTo(new AppointmentResponse.ProfessionalSummary(PROFESSIONAL_ID, "Carlos Ruiz"));
		assertThat(response.service()).isEqualTo(new AppointmentResponse.ServiceSummary(SERVICE_ID, "Consulta general", 30));
		assertThat(response.cancelReason()).isNull();
	}

	@Test
	void notesAreTrimmedAndBlankNotesBecomeNull() {
		assertThat(new CreateAppointmentRequest(1L, 1L, NOW, "  hola  ", null).notes()).isEqualTo("hola");
		assertThat(new CreateAppointmentRequest(1L, 1L, NOW, "   ", null).notes()).isNull();
		assertThat(new CreateAppointmentRequest(1L, 1L, NOW, null, null).notes()).isNull();
	}

	@Test
	void theProfessionalIsLockedBeforeTheClient() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(THURSDAY, "09:00-12:00");
		givenSaveAssignsId();

		service.create(request(THURSDAY.atTime(10, 0)));

		InOrder order = inOrder(professionals, users, appointments);
		order.verify(professionals).findByIdForUpdate(PROFESSIONAL_ID);
		order.verify(users).findByIdForUpdate(CLIENT_ID);
		order.verify(appointments).save(any());
	}

	@Test
	void contiguousBlocksLetAnAppointmentSpanThem() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(60);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(THURSDAY, "10:00-11:00", "09:00-10:00");
		givenSaveAssignsId();

		// 09:30-10:30 cruza el límite entre los dos bloques, que cuentan como uno solo (RN-03)
		assertThat(service.create(request(THURSDAY.atTime(9, 30))).endTime()).isEqualTo(THURSDAY.atTime(10, 30));
	}

	// ---------- a nombre de quién ----------

	@Test
	void aClientAlwaysBooksForItselfEvenIfItSendsAClientId() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(THURSDAY, "09:00-12:00");
		givenSaveAssignsId();

		AppointmentResponse response = service.create(new CreateAppointmentRequest(PROFESSIONAL_ID, SERVICE_ID,
				THURSDAY.atTime(10, 0), null, 99L));

		assertThat(response.client().id()).isEqualTo(CLIENT_ID);
		verify(users, never()).findByIdForUpdate(99L);
	}

	@Test
	void anAdminBooksForAnActiveClientOrForItself() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenLockedUser(user(ADMIN_ID, Role.ADMIN, true));
		givenSchedule(THURSDAY, "09:00-12:00");
		givenSaveAssignsId();

		assertThat(service.create(new CreateAppointmentRequest(PROFESSIONAL_ID, SERVICE_ID, THURSDAY.atTime(10, 0),
				null, CLIENT_ID)).client().id()).isEqualTo(CLIENT_ID);
		assertThat(service.create(new CreateAppointmentRequest(PROFESSIONAL_ID, SERVICE_ID, THURSDAY.atTime(11, 0),
				null, null)).client().id()).isEqualTo(ADMIN_ID);
	}

	@Test
	void anAdminCannotBookForAMissingInactiveOrNonClientUser() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		givenCatalog(30);
		when(users.findByIdForUpdate(98L)).thenReturn(Optional.empty());
		when(users.findByIdForUpdate(97L)).thenReturn(Optional.of(user(97L, Role.CLIENT, false)));
		when(users.findByIdForUpdate(96L)).thenReturn(Optional.of(user(96L, Role.PROFESSIONAL, true)));
		when(users.findByIdForUpdate(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, Role.ADMIN, true)));

		// ADMIN_ID explícito tampoco vale: clientId debe ser un CLIENT; para reservar a su nombre, el ADMIN lo omite
		for (Long clientId : List.of(98L, 97L, 96L, ADMIN_ID)) {
			assertError(() -> service.create(new CreateAppointmentRequest(PROFESSIONAL_ID, SERVICE_ID,
					THURSDAY.atTime(10, 0), null, clientId)), HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_CLIENT_ID");
		}
		verifyNoInteractions(workingHours, appointments);
	}

	@Test
	void onlyClientsAndAdminsCanBook() {
		for (Role role : new Role[] { null, Role.PROFESSIONAL }) {             // null = anónimo
			authenticateAs(role, 5L);
			assertThatThrownBy(() -> service.create(request(THURSDAY.atTime(10, 0))))
					.isInstanceOf(ForbiddenOperationException.class);
		}
		verifyNoInteractions(professionals, services, users, appointments);
	}

	// ---------- profesional y servicio ----------

	@Test
	void anUnknownOrHiddenProfessionalIsNotFound() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		ServiceOffering offered = service(SERVICE_ID, 30, true);
		List<Optional<Professional>> cases = List.of(Optional.empty(),
				Optional.of(professional(false, true, offered)), Optional.of(professional(true, false, offered)));
		for (Optional<Professional> found : cases) {
			when(professionals.findByIdForUpdate(PROFESSIONAL_ID)).thenReturn(found);
			assertThatThrownBy(() -> service.create(request(THURSDAY.atTime(10, 0))))
					.isInstanceOf(ResourceNotFoundException.class);
		}
		verifyNoInteractions(services, users, appointments);
	}

	@Test
	void anUnknownOrInactiveServiceIsNotFound() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		ServiceOffering inactive = service(SERVICE_ID, 30, false);
		when(professionals.findByIdForUpdate(PROFESSIONAL_ID)).thenReturn(Optional.of(professional(true, true, inactive)));
		for (Optional<ServiceOffering> found : List.of(Optional.<ServiceOffering>empty(), Optional.of(inactive))) {
			when(services.findById(SERVICE_ID)).thenReturn(found);
			assertThatThrownBy(() -> service.create(request(THURSDAY.atTime(10, 0))))
					.isInstanceOf(ResourceNotFoundException.class);
		}
		verifyNoInteractions(users, appointments);
	}

	@Test
	void aServiceTheProfessionalDoesNotOfferIs422BeforeTheTimeRules() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		when(professionals.findByIdForUpdate(PROFESSIONAL_ID))
				.thenReturn(Optional.of(professional(true, true, service(99L, 30, true))));
		when(services.findById(SERVICE_ID)).thenReturn(Optional.of(service(SERVICE_ID, 30, true)));

		// 08:30 también sería TOO_SOON, pero SERVICE_NOT_OFFERED va antes
		assertError(() -> service.create(request(NOW.plusMinutes(30))), HttpStatus.UNPROCESSABLE_CONTENT,
				"SERVICE_NOT_OFFERED");
		verifyNoInteractions(users, workingHours, appointments);
	}

	// ---------- anticipación y tope (RN-04) ----------

	@Test
	void tooSoonWinsOverEveryOtherTimeRule() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));

		// 08:30 es TOO_SOON (mínimo 09:00), y además estaría fuera de jornada y mal alineada (08:07)
		for (LocalDateTime start : List.of(NOW.plusMinutes(30), NOW.plusMinutes(7), NOW.minusDays(1), NOW.plusMinutes(59))) {
			assertError(() -> service.create(request(start)), HttpStatus.UNPROCESSABLE_CONTENT, "TOO_SOON");
		}
		verifyNoInteractions(workingHours, appointments);
	}

	@Test
	void theFirstSlotAfterTheMinimumNoticeIsAccepted() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(NOW.toLocalDate(), "09:00-12:00");
		givenSaveAssignsId();

		// Justo en el límite: ahora (08:00) + 60 min
		assertThat(service.create(request(NOW.plusMinutes(60))).status()).isEqualTo(AppointmentStatus.PENDING);
	}

	@Test
	void tooFarIsCheckedBeforeTheScheduleAndNeverOverflows() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));

		// 2027-01-01 es viernes con jornada, pero el tope es 2027-01-01T08:00; LocalDateTime.MAX no desborda
		for (LocalDateTime start : List.of(LocalDateTime.of(2027, 1, 1, 9, 0), NOW.plusDays(60).plusMinutes(1),
				LocalDateTime.MAX)) {
			assertError(() -> service.create(request(start)), HttpStatus.UNPROCESSABLE_CONTENT, "TOO_FAR");
		}
		verifyNoInteractions(workingHours, appointments);
	}

	@Test
	void exactlyTheMaxDaysAheadLimitPassesTheWindow() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		LocalDateTime limit = NOW.plusDays(60);                                // 2027-01-01T08:00
		givenSchedule(limit.toLocalDate(), "09:00-12:00");

		// No es TOO_FAR: la siguiente comprobación (la jornada) es la que falla
		assertError(() -> service.create(request(limit)), HttpStatus.UNPROCESSABLE_CONTENT, "OUTSIDE_WORKING_HOURS");
	}

	// ---------- jornada (RN-03) y alineación ----------

	@Test
	void anAppointmentThatDoesNotFitInARangeIsOutsideWorkingHours() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(60);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(THURSDAY, "09:00-12:00", "14:00-18:00", "22:00-23:59");
		givenSchedule(THURSDAY.plusDays(2));                                   // sábado sin horario

		// En la pausa, cruzando el fin del bloque (11:30 + 60 = 12:30), cruzando medianoche y en un día sin horario.
		// 12:07 también estaría mal alineada: OUTSIDE_WORKING_HOURS va antes
		for (LocalDateTime start : List.of(THURSDAY.atTime(12, 0), THURSDAY.atTime(11, 30), THURSDAY.atTime(12, 7),
				THURSDAY.atTime(23, 30), THURSDAY.plusDays(2).atTime(10, 0))) {
			assertError(() -> service.create(request(start)), HttpStatus.UNPROCESSABLE_CONTENT, "OUTSIDE_WORKING_HOURS");
		}
		verifyNoInteractions(appointments);
	}

	@Test
	void aStartThatFitsButIsNotAnOfferedSlotIsInvalid() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(THURSDAY, "09:15-12:00");

		// Los horarios se anclan al inicio del tramo: 09:15, 09:45, 10:15... y con segundos y nanos en 0
		for (LocalDateTime start : List.of(THURSDAY.atTime(10, 0), THURSDAY.atTime(10, 7),
				THURSDAY.atTime(9, 45, 30), THURSDAY.atTime(LocalTime.of(9, 45, 0, 1)))) {
			assertError(() -> service.create(request(start)), HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_SLOT_START");
		}
		verifyNoInteractions(appointments);
	}

	@Test
	void aStartAnchoredToTheRangeIsValid() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(THURSDAY, "09:15-12:00");
		givenSaveAssignsId();

		assertThat(service.create(request(THURSDAY.atTime(9, 45))).startTime()).isEqualTo(THURSDAY.atTime(9, 45));
	}

	// ---------- solapes (RN-02 y RN-06) ----------

	@Test
	void aProfessionalOverlapIs409BeforeTheClientOverlap() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(THURSDAY, "09:00-12:00");
		when(appointments.countOverlapping(PROFESSIONAL_ID, THURSDAY.atTime(10, 0), THURSDAY.atTime(10, 30), null))
				.thenReturn(1L);

		assertError(() -> service.create(request(THURSDAY.atTime(10, 0))), HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE");
		verify(appointments, never()).countOverlappingForClient(anyLong(), any(), any(), any());
		verify(appointments, never()).save(any());
	}

	@Test
	void aClientOverlapWithAnyProfessionalIs409() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenCatalog(30);
		givenLockedUser(user(CLIENT_ID, Role.CLIENT, true));
		givenSchedule(THURSDAY, "09:00-12:00");
		when(appointments.countOverlappingForClient(CLIENT_ID, THURSDAY.atTime(10, 0), THURSDAY.atTime(10, 30), null))
				.thenReturn(1L);

		assertError(() -> service.create(request(THURSDAY.atTime(10, 0))), HttpStatus.CONFLICT, "CLIENT_OVERLAP");
		verify(appointments, never()).save(any());
	}

	// ---------- lectura ----------

	@Test
	void listingRejectsFromAfterToBeforeQuerying() {
		authenticateAs(Role.CLIENT, CLIENT_ID);

		assertThatThrownBy(() -> service.list(null, THURSDAY, THURSDAY.minusDays(1), null, PageRequest.of(0, 20)))
				.isInstanceOfSatisfying(BadRequestException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("INVALID_DATE_RANGE"));
		verifyNoInteractions(appointments);
	}

	@Test
	@SuppressWarnings("unchecked")
	void listingSortsByStartTimeByDefaultAndAlwaysAddsTheIdTieBreaker() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		when(appointments.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

		service.list(null, null, null, null, PageRequest.of(0, 20));
		service.list(null, null, null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "price")));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(appointments, times(2)).findAll(any(Specification.class), pageable.capture());
		assertThat(pageable.getAllValues().get(0).getSort()).isEqualTo(Sort.by("startTime").and(Sort.by("id")));
		assertThat(pageable.getAllValues().get(1).getSort())
				.isEqualTo(Sort.by(Sort.Direction.DESC, "price").and(Sort.by("id")));
		assertThatThrownBy(() -> service.list(null, null, null, null, PageRequest.of(0, 20, Sort.by("notes"))))
				.isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_SORT"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void listingMapsEveryAppointmentOfThePage() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		Appointment appointment = appointment(41L, CLIENT_ID);
		when(appointments.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(appointment), PageRequest.of(0, 20), 1));

		assertThat(service.list(null, null, null, null, PageRequest.of(0, 20)).content())
				.extracting(AppointmentResponse::id).containsExactly(41L);
	}

	@Test
	void theDetailIsVisibleToTheClientTheAssignedProfessionalAndAdmins() {
		when(appointments.findWithDetailsById(41L)).thenReturn(Optional.of(appointment(41L, CLIENT_ID)));

		for (Object[] viewer : new Object[][] { { Role.CLIENT, CLIENT_ID }, { Role.PROFESSIONAL, PROFESSIONAL_USER_ID },
				{ Role.ADMIN, ADMIN_ID } }) {
			authenticateAs((Role) viewer[0], (Long) viewer[1]);
			assertThat(service.get(41L).id()).isEqualTo(41L);
		}
	}

	@Test
	void anyoneElseGets404AsIfTheAppointmentDidNotExist() {
		when(appointments.findWithDetailsById(41L)).thenReturn(Optional.of(appointment(41L, CLIENT_ID)));
		when(appointments.findWithDetailsById(42L)).thenReturn(Optional.empty());

		for (Object[] viewer : new Object[][] { { Role.CLIENT, 21L }, { Role.PROFESSIONAL, 51L } }) {
			authenticateAs((Role) viewer[0], (Long) viewer[1]);
			assertThatThrownBy(() -> service.get(41L)).isInstanceOf(ResourceNotFoundException.class);
		}
		authenticateAs(Role.ADMIN, ADMIN_ID);
		assertThatThrownBy(() -> service.get(42L)).isInstanceOf(ResourceNotFoundException.class);
	}

	// ---------- utilidades ----------

	private static CreateAppointmentRequest request(LocalDateTime start) {
		return new CreateAppointmentRequest(PROFESSIONAL_ID, SERVICE_ID, start, null, null);
	}

	// Profesional y usuario activos que ofrecen el servicio pedido, de la duración indicada
	private void givenCatalog(int durationMinutes) {
		ServiceOffering offered = service(SERVICE_ID, durationMinutes, true);
		when(professionals.findByIdForUpdate(PROFESSIONAL_ID)).thenReturn(Optional.of(professional(true, true, offered)));
		when(services.findById(SERVICE_ID)).thenReturn(Optional.of(offered));
	}

	private void givenLockedUser(User user) {
		when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
	}

	// Bloques "HH:mm-HH:mm" del día de la semana de date
	private void givenSchedule(LocalDate date, String... blocks) {
		List<WorkingHour> schedule = new ArrayList<>();
		for (String block : blocks) {
			String[] parts = block.split("-");
			WorkingHour workingHour = new WorkingHour();
			workingHour.setDayOfWeek(date.getDayOfWeek());
			workingHour.setStartTime(LocalTime.parse(parts[0]));
			workingHour.setEndTime(LocalTime.parse(parts[1]));
			schedule.add(workingHour);
		}
		when(workingHours.findByProfessionalIdAndDayOfWeek(PROFESSIONAL_ID, date.getDayOfWeek())).thenReturn(schedule);
	}

	private void givenSaveAssignsId() {
		when(appointments.save(any(Appointment.class))).thenAnswer(invocation -> {
			Appointment appointment = invocation.getArgument(0);
			appointment.setId(41L);
			return appointment;
		});
	}

	private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status,
			String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
	}

	private static ServiceOffering service(Long id, int durationMinutes, boolean active) {
		ServiceOffering offering = new ServiceOffering();
		offering.setId(id);
		offering.setName("Consulta general");
		offering.setDurationMinutes(durationMinutes);
		offering.setPrice(PRICE);
		offering.setActive(active);
		return offering;
	}

	private static User user(Long id, Role role, boolean active) {
		User user = new User();
		user.setId(id);
		user.setFullName(role == Role.CLIENT ? "Ana Pérez" : "Usuario " + id);
		user.setRole(role);
		user.setActive(active);
		return user;
	}

	private static Professional professional(boolean active, boolean userActive, ServiceOffering... offered) {
		User user = user(PROFESSIONAL_USER_ID, Role.PROFESSIONAL, userActive);
		user.setFullName("Carlos Ruiz");
		Professional professional = new Professional();
		professional.setId(PROFESSIONAL_ID);
		professional.setUser(user);
		professional.setSpecialty("Fisioterapia");
		professional.setActive(active);
		professional.setServices(new HashSet<>(List.of(offered)));
		return professional;
	}

	private static Appointment appointment(Long id, Long clientId) {
		ServiceOffering offered = service(SERVICE_ID, 30, true);
		Appointment appointment = new Appointment();
		appointment.setId(id);
		appointment.setClient(user(clientId, Role.CLIENT, true));
		appointment.setProfessional(professional(true, true, offered));
		appointment.setService(offered);
		appointment.setStartTime(THURSDAY.atTime(10, 0));
		appointment.setEndTime(THURSDAY.atTime(10, 30));
		appointment.setPrice(PRICE);
		return appointment;
	}

	private static void authenticateAs(Role role, Long userId) {
		SecurityContextHolder.clearContext();
		if (role != null) {
			AuthUser user = new AuthUser(userId, "usuario@mail.test", "hash-sintetico", role, true);
			SecurityContextHolder.getContext().setAuthentication(
					UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
		}
	}

}
