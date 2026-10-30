package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.q.q.config.AppProperties;
import com.q.q.dto.request.RescheduleAppointmentRequest;
import com.q.q.dto.response.AppointmentResponse;
import com.q.q.entity.Appointment;
import com.q.q.entity.Professional;
import com.q.q.entity.ServiceOffering;
import com.q.q.entity.User;
import com.q.q.entity.WorkingHour;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.entity.enums.Role;
import com.q.q.exception.ApiException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.AppointmentMapper;
import com.q.q.mapper.WorkingHourMapper;
import com.q.q.repository.AppointmentParties;
import com.q.q.repository.AppointmentRepository;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.ServiceOfferingRepository;
import com.q.q.repository.UserRepository;
import com.q.q.repository.WorkingHourRepository;
import com.q.q.security.AuthUser;

// Cambios de estado con reloj fijo: lunes 2 de noviembre de 2026, 08:00. cancel-limit-hours = 2, como en application.yml
@ExtendWith(MockitoExtension.class)
class AppointmentStatusChangeServiceTest {

	private static final ZoneId ZONE = ZoneId.of("America/Bogota");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
	private static final LocalDate THURSDAY = LocalDate.of(2026, 11, 5);
	private static final Long APPOINTMENT_ID = 41L;
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

	// ---------- confirmar ----------

	@Test
	void theAssignedProfessionalAndAdminsConfirmWithoutATimeRestriction() {
		// Una cita PENDING que ya pasó también se puede confirmar
		for (Object[] actor : new Object[][] { { Role.PROFESSIONAL, PROFESSIONAL_USER_ID }, { Role.ADMIN, ADMIN_ID } }) {
			authenticateAs((Role) actor[0], (Long) actor[1]);
			Appointment appointment = givenLocked(AppointmentStatus.PENDING, NOW.minusDays(1));
			assertThat(service.confirm(APPOINTMENT_ID).status()).isEqualTo(AppointmentStatus.CONFIRMED);
			assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		}
	}

	@Test
	void anotherProfessionalGets404AndAClientIsRejected() {
		authenticateAs(Role.PROFESSIONAL, 51L);
		givenLocked(AppointmentStatus.PENDING, THURSDAY.atTime(10, 0));
		assertThatThrownBy(() -> service.confirm(APPOINTMENT_ID)).isInstanceOf(ResourceNotFoundException.class);

		// A un CLIENT lo frena la regla por URL; el servicio, por si acaso, tampoco lo deja
		authenticateAs(Role.CLIENT, CLIENT_ID);
		for (ThrowingCallable call : List.<ThrowingCallable>of(() -> service.confirm(APPOINTMENT_ID),
				() -> service.complete(APPOINTMENT_ID), () -> service.markNoShow(APPOINTMENT_ID))) {
			assertThatThrownBy(call).isInstanceOf(ForbiddenOperationException.class);
		}
	}

	@Test
	void aMissingAppointmentIs404() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		when(appointments.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.confirm(APPOINTMENT_ID)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void confirmingTwiceOrAFinalAppointmentIs409() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		for (AppointmentStatus status : new AppointmentStatus[] { AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED,
				AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW }) {
			givenLocked(status, THURSDAY.atTime(10, 0));
			assertError(() -> service.confirm(APPOINTMENT_ID), HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION");
		}
	}

	// ---------- completar y no-show ----------

	@Test
	void completeAndNoShowNeedTheAppointmentToHaveStarted() {
		authenticateAs(Role.PROFESSIONAL, PROFESSIONAL_USER_ID);

		givenLocked(AppointmentStatus.CONFIRMED, NOW.plusMinutes(1));
		assertError(() -> service.complete(APPOINTMENT_ID), HttpStatus.UNPROCESSABLE_CONTENT, "APPOINTMENT_NOT_STARTED");
		givenLocked(AppointmentStatus.CONFIRMED, NOW.plusMinutes(1));
		assertError(() -> service.markNoShow(APPOINTMENT_ID), HttpStatus.UNPROCESSABLE_CONTENT, "APPOINTMENT_NOT_STARTED");

		// Justo a la hora de inicio ya vale
		givenLocked(AppointmentStatus.CONFIRMED, NOW);
		assertThat(service.complete(APPOINTMENT_ID).status()).isEqualTo(AppointmentStatus.COMPLETED);
		givenLocked(AppointmentStatus.CONFIRMED, NOW);
		assertThat(service.markNoShow(APPOINTMENT_ID).status()).isEqualTo(AppointmentStatus.NO_SHOW);
	}

	@Test
	void completeAndNoShowOnlyFromConfirmedAndTheTransitionIsCheckedFirst() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		// PENDING y además futura: gana el 409 sobre el 422
		givenLocked(AppointmentStatus.PENDING, THURSDAY.atTime(10, 0));
		assertError(() -> service.complete(APPOINTMENT_ID), HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION");
		givenLocked(AppointmentStatus.PENDING, NOW.minusHours(1));
		assertError(() -> service.markNoShow(APPOINTMENT_ID), HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION");
		givenLocked(AppointmentStatus.COMPLETED, NOW.minusHours(1));
		assertError(() -> service.markNoShow(APPOINTMENT_ID), HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION");
	}

	// ---------- cancelar ----------

	@Test
	void aClientCancelsUpToTheLimitIncludedAndTheReasonIsKept() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		// Inicio a las 10:00: el límite es exactamente ahora (08:00)
		Appointment appointment = givenLocked(AppointmentStatus.PENDING, NOW.plusHours(2));

		AppointmentResponse response = service.cancel(APPOINTMENT_ID, "No puedo asistir");

		assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(response.cancelReason()).isEqualTo("No puedo asistir");
		assertThat(appointment.getCancelReason()).isEqualTo("No puedo asistir");
	}

	@Test
	void oneMinuteAfterTheLimitTheClientCanNoLongerCancel() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		Appointment appointment = givenLocked(AppointmentStatus.CONFIRMED, NOW.plusHours(2).minusMinutes(1));

		assertError(() -> service.cancel(APPOINTMENT_ID, null), HttpStatus.UNPROCESSABLE_CONTENT,
				"CANCELLATION_WINDOW_CLOSED");
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
	}

	@Test
	void theAssignedProfessionalAndAdminsCancelWithoutALimit() {
		for (Object[] actor : new Object[][] { { Role.PROFESSIONAL, PROFESSIONAL_USER_ID }, { Role.ADMIN, ADMIN_ID } }) {
			authenticateAs((Role) actor[0], (Long) actor[1]);
			givenLocked(AppointmentStatus.CONFIRMED, NOW.plusMinutes(10));
			AppointmentResponse response = service.cancel(APPOINTMENT_ID, null);
			assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
			assertThat(response.cancelReason()).isNull();
		}
	}

	@Test
	void strangersGet404AndAFinalAppointmentIs409BeforeTheWindow() {
		for (Object[] stranger : new Object[][] { { Role.CLIENT, 21L }, { Role.PROFESSIONAL, 51L } }) {
			authenticateAs((Role) stranger[0], (Long) stranger[1]);
			givenLocked(AppointmentStatus.PENDING, THURSDAY.atTime(10, 0));
			assertThatThrownBy(() -> service.cancel(APPOINTMENT_ID, null)).isInstanceOf(ResourceNotFoundException.class);
		}
		// Ya completada y además dentro de la ventana: gana el 409
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenLocked(AppointmentStatus.COMPLETED, NOW.plusMinutes(30));
		assertError(() -> service.cancel(APPOINTMENT_ID, null), HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION");
	}

	// ---------- reprogramar ----------

	@Test
	void reschedulingLocksTheProfessionalThenTheClientThenTheAppointment() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenReschedulable(AppointmentStatus.PENDING, THURSDAY.atTime(10, 0), 30);
		givenSchedule(THURSDAY, "09:00-12:00");

		service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(THURSDAY.atTime(11, 0)));

		InOrder order = inOrder(appointments, professionals, users);
		order.verify(appointments).findPartiesById(APPOINTMENT_ID);
		order.verify(professionals).findByIdForUpdate(PROFESSIONAL_ID);
		order.verify(users).findByIdForUpdate(CLIENT_ID);
		order.verify(appointments).findByIdForUpdate(APPOINTMENT_ID);
	}

	@Test
	void reschedulingKeepsStatusPriceAndOriginalDurationAndExcludesItself() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		// La cita dura 45 min aunque el servicio dure hoy 30
		Appointment appointment = givenReschedulable(AppointmentStatus.CONFIRMED, THURSDAY.atTime(10, 0), 45);
		givenSchedule(THURSDAY, "09:00-12:00");
		LocalDateTime start = THURSDAY.atTime(10, 30);
		LocalDateTime end = THURSDAY.atTime(11, 15);

		AppointmentResponse response = service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(start));

		assertThat(response.startTime()).isEqualTo(start);
		assertThat(response.endTime()).isEqualTo(end);
		assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(response.price()).isEqualTo(PRICE);
		assertThat(appointment.getEndTime()).isEqualTo(end);
		// RN-12: la propia cita no cuenta en los solapes
		verify(appointments).countOverlapping(PROFESSIONAL_ID, start, end, APPOINTMENT_ID);
		verify(appointments).countOverlappingForClient(CLIENT_ID, start, end, APPOINTMENT_ID);
	}

	@Test
	void anotherClientGets404WithoutLockingAnything() {
		authenticateAs(Role.CLIENT, 21L);
		when(appointments.findPartiesById(APPOINTMENT_ID))
				.thenReturn(Optional.of(new AppointmentParties(APPOINTMENT_ID, PROFESSIONAL_ID, CLIENT_ID)));

		assertThatThrownBy(() -> service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(THURSDAY.atTime(11, 0))))
				.isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(professionals, users);
		verify(appointments, never()).findByIdForUpdate(anyLong());
	}

	@Test
	void onlyActiveAppointmentsCanBeRescheduledAndThatIsCheckedBeforeTheWindow() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		for (AppointmentStatus status : new AppointmentStatus[] { AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED,
				AppointmentStatus.NO_SHOW }) {
			// Además la ventana estaría cerrada (empieza en 30 min): gana el 409
			givenReschedulable(status, NOW.plusMinutes(30), 30);
			assertError(() -> service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(THURSDAY.atTime(11, 0))),
					HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION");
		}
	}

	@Test
	void theClientRescheduleWindowUsesTheCurrentStartButAdminsHaveNoLimit() {
		authenticateAs(Role.CLIENT, CLIENT_ID);
		givenReschedulable(AppointmentStatus.PENDING, NOW.plusHours(2).minusMinutes(1), 30);
		assertError(() -> service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(THURSDAY.atTime(11, 0))),
				HttpStatus.UNPROCESSABLE_CONTENT, "RESCHEDULE_WINDOW_CLOSED");

		// Justo en el límite todavía puede
		givenReschedulable(AppointmentStatus.PENDING, NOW.plusHours(2), 30);
		givenSchedule(THURSDAY, "09:00-12:00");
		assertThat(service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(THURSDAY.atTime(11, 0))).startTime())
				.isEqualTo(THURSDAY.atTime(11, 0));

		authenticateAs(Role.ADMIN, ADMIN_ID);
		givenReschedulable(AppointmentStatus.PENDING, NOW.plusMinutes(30), 30);
		assertThat(service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(THURSDAY.atTime(11, 30))).startTime())
				.isEqualTo(THURSDAY.atTime(11, 30));
	}

	@Test
	void theSameStartIsRejectedBeforeTheScheduleRules() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		// La hora actual además sería TOO_SOON: SAME_START_TIME va antes
		givenReschedulable(AppointmentStatus.PENDING, NOW.plusMinutes(30), 30);

		assertError(() -> service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(NOW.plusMinutes(30))),
				HttpStatus.UNPROCESSABLE_CONTENT, "SAME_START_TIME");
		verify(appointments, never()).countOverlapping(anyLong(), any(), any(), any());
	}

	@Test
	void theProfessionalAndTheServiceMustStillBeActiveAndOffered() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		LocalDateTime current = THURSDAY.atTime(10, 0);
		RescheduleAppointmentRequest request = new RescheduleAppointmentRequest(THURSDAY.atTime(11, 0));

		Appointment appointment = givenReschedulable(AppointmentStatus.PENDING, current, 30);
		appointment.getProfessional().setActive(false);
		assertThatThrownBy(() -> service.reschedule(APPOINTMENT_ID, request)).isInstanceOf(ResourceNotFoundException.class);

		appointment = givenReschedulable(AppointmentStatus.PENDING, current, 30);
		appointment.getService().setActive(false);
		assertThatThrownBy(() -> service.reschedule(APPOINTMENT_ID, request)).isInstanceOf(ResourceNotFoundException.class);

		appointment = givenReschedulable(AppointmentStatus.PENDING, current, 30);
		appointment.getProfessional().setServices(new HashSet<>());
		assertError(() -> service.reschedule(APPOINTMENT_ID, request), HttpStatus.UNPROCESSABLE_CONTENT,
				"SERVICE_NOT_OFFERED");
	}

	@Test
	void theNewTimeGoesThroughTheSameScheduleRulesAsABooking() {
		authenticateAs(Role.ADMIN, ADMIN_ID);
		givenSchedule(THURSDAY, "09:00-12:00");
		Object[][] cases = { { NOW.plusMinutes(30), "TOO_SOON" }, { NOW.plusDays(61), "TOO_FAR" },
				{ THURSDAY.atTime(12, 0), "OUTSIDE_WORKING_HOURS" }, { THURSDAY.atTime(10, 7), "INVALID_SLOT_START" } };
		for (Object[] testCase : cases) {
			givenReschedulable(AppointmentStatus.PENDING, THURSDAY.atTime(9, 0), 30);
			assertError(() -> service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest((LocalDateTime) testCase[0])),
					HttpStatus.UNPROCESSABLE_CONTENT, (String) testCase[1]);
		}

		givenReschedulable(AppointmentStatus.PENDING, THURSDAY.atTime(9, 0), 30);
		when(appointments.countOverlapping(PROFESSIONAL_ID, THURSDAY.atTime(11, 0), THURSDAY.atTime(11, 30), APPOINTMENT_ID))
				.thenReturn(1L);
		assertError(() -> service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(THURSDAY.atTime(11, 0))),
				HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE");

		givenReschedulable(AppointmentStatus.PENDING, THURSDAY.atTime(9, 0), 30);
		when(appointments.countOverlappingForClient(CLIENT_ID, THURSDAY.atTime(11, 30), THURSDAY.atTime(12, 0),
				APPOINTMENT_ID)).thenReturn(1L);
		assertError(() -> service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(THURSDAY.atTime(11, 30))),
				HttpStatus.CONFLICT, "CLIENT_OVERLAP");
	}

	@Test
	void onlyClientsAndAdminsCanReschedule() {
		authenticateAs(Role.PROFESSIONAL, PROFESSIONAL_USER_ID);

		assertThatThrownBy(() -> service.reschedule(APPOINTMENT_ID, new RescheduleAppointmentRequest(THURSDAY.atTime(11, 0))))
				.isInstanceOf(ForbiddenOperationException.class);
		verifyNoInteractions(appointments, professionals, users);
	}

	// ---------- utilidades ----------

	// La cita bloqueada que devuelve findByIdForUpdate: de CLIENT_ID, con el profesional PROFESSIONAL_ID
	private Appointment givenLocked(AppointmentStatus status, LocalDateTime start) {
		Appointment appointment = appointment(status, start, 30);
		when(appointments.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
		return appointment;
	}

	// Lo que lee reschedule: las partes sin bloquear y después profesional, cliente y cita bloqueados
	private Appointment givenReschedulable(AppointmentStatus status, LocalDateTime start, int minutes) {
		Appointment appointment = appointment(status, start, minutes);
		when(appointments.findPartiesById(APPOINTMENT_ID))
				.thenReturn(Optional.of(new AppointmentParties(APPOINTMENT_ID, PROFESSIONAL_ID, CLIENT_ID)));
		when(professionals.findByIdForUpdate(PROFESSIONAL_ID)).thenReturn(Optional.of(appointment.getProfessional()));
		when(users.findByIdForUpdate(CLIENT_ID)).thenReturn(Optional.of(appointment.getClient()));
		when(appointments.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
		// lenient: varios caminos fallan antes de mirar el servicio (estado, ventana, misma hora)
		lenient().when(services.findById(SERVICE_ID)).thenReturn(Optional.of(appointment.getService()));
		return appointment;
	}

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

	private static Appointment appointment(AppointmentStatus status, LocalDateTime start, int minutes) {
		ServiceOffering offered = new ServiceOffering();
		offered.setId(SERVICE_ID);
		offered.setName("Consulta general");
		offered.setDurationMinutes(30);
		offered.setPrice(new BigDecimal("95000.00"));          // el precio de hoy; la cita guardó otro (RN-09)
		offered.setActive(true);
		User professionalUser = user(PROFESSIONAL_USER_ID, Role.PROFESSIONAL);
		Professional professional = new Professional();
		professional.setId(PROFESSIONAL_ID);
		professional.setUser(professionalUser);
		professional.setSpecialty("Fisioterapia");
		professional.setServices(new HashSet<>(List.of(offered)));
		Appointment appointment = new Appointment();
		appointment.setId(APPOINTMENT_ID);
		appointment.setClient(user(CLIENT_ID, Role.CLIENT));
		appointment.setProfessional(professional);
		appointment.setService(offered);
		appointment.setStartTime(start);
		appointment.setEndTime(start.plusMinutes(minutes));
		appointment.setStatus(status);
		appointment.setPrice(PRICE);
		return appointment;
	}

	private static User user(Long id, Role role) {
		User user = new User();
		user.setId(id);
		user.setFullName("Usuario " + id);
		user.setRole(role);
		user.setActive(true);
		return user;
	}

	private static void assertError(ThrowingCallable call, HttpStatus status, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getStatus()).isEqualTo(status);
			assertThat(ex.getCode()).isEqualTo(code);
		});
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
