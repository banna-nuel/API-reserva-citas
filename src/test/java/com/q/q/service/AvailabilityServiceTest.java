package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.q.q.config.AppProperties;
import com.q.q.dto.response.AvailabilityResponse;
import com.q.q.entity.Appointment;
import com.q.q.entity.Professional;
import com.q.q.entity.ServiceOffering;
import com.q.q.entity.User;
import com.q.q.entity.WorkingHour;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.entity.enums.Role;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.WorkingHourMapper;
import com.q.q.repository.AppointmentRepository;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.ServiceOfferingRepository;
import com.q.q.repository.WorkingHourRepository;
import com.q.q.security.AuthUser;

// Reloj fijo en la zona del negocio y app.booking como en application.yml: paso 30, aviso 60 min, tope 60 días
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

	private static final ZoneId ZONE = ZoneId.of("America/Bogota");
	private static final Long PROFESSIONAL_ID = 7L;
	private static final Long SERVICE_ID = 3L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 20);   // lunes, 10:20
	private static final LocalDate TODAY = NOW.toLocalDate();
	private static final LocalDate TOMORROW = TODAY.plusDays(1);

	@Mock
	private ProfessionalRepository professionals;

	@Mock
	private ServiceOfferingRepository services;

	@Mock
	private WorkingHourRepository workingHours;

	@Mock
	private AppointmentRepository appointments;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	// ---------- jornada ----------

	@Test
	void aDayWithoutScheduleHasNoSlotsAndSkipsTheAppointmentQuery() {
		givenCatalog(30);
		givenSchedule(TOMORROW);

		assertThat(slots(availability(NOW, TOMORROW))).isEmpty();
		verifyNoInteractions(appointments);
	}

	@Test
	void aLunchBreakLeavesNoSlotsBetweenTheBlocks() {
		givenCatalog(30);
		givenSchedule(TOMORROW, "09:00-12:00", "14:00-18:00");

		List<String> slots = slots(availability(NOW, TOMORROW));

		assertThat(slots).containsExactly(
				"09:00-09:30", "09:30-10:00", "10:00-10:30", "10:30-11:00", "11:00-11:30", "11:30-12:00",
				"14:00-14:30", "14:30-15:00", "15:00-15:30", "15:30-16:00", "16:00-16:30", "16:30-17:00",
				"17:00-17:30", "17:30-18:00");
		assertThat(slots).noneMatch(s -> s.startsWith("12:") || s.startsWith("13:"));
	}

	@Test
	void theLastSlotFitsCompletelyWhenTheDurationDoesNotDivideTheBlock() {
		givenCatalog(45);
		givenSchedule(TOMORROW, "09:00-12:00");

		// 11:30 + 45 min = 12:15 ya no cabe
		assertThat(slots(availability(NOW, TOMORROW))).containsExactly(
				"09:00-09:45", "09:30-10:15", "10:00-10:45", "10:30-11:15", "11:00-11:45");
	}

	@Test
	void aServiceLongerThanEveryBlockHasNoSlots() {
		givenCatalog(120);
		givenSchedule(TOMORROW, "09:00-10:00", "11:00-12:30", "14:00-15:59");

		assertThat(slots(availability(NOW, TOMORROW))).isEmpty();
	}

	@Test
	void contiguousBlocksAreMergedSoAServiceCanSpanThem() {
		givenCatalog(60);
		givenSchedule(TOMORROW, "10:00-11:00", "09:00-10:00", "11:00-11:30");     // desordenados

		// Sin fusionar solo saldrían 09:00 y 10:00: 09:30 y 10:30 cruzan el límite entre bloques
		assertThat(slots(availability(NOW, TOMORROW))).containsExactly(
				"09:00-10:00", "09:30-10:30", "10:00-11:00", "10:30-11:30");
	}

	// ---------- citas ----------

	@Test
	void activeAppointmentsBlockTheSlotsTheyOverlapButAdjacentOnesStayFree() {
		givenCatalog(30);
		givenSchedule(TOMORROW, "09:00-12:00");
		// La consulta solo devuelve citas activas (PENDING y CONFIRMED); que las demás no ocupen lo prueba
		// AvailabilityIntegrationTest contra MySQL
		givenAppointments(TOMORROW,
				appointment(TOMORROW.atTime(9, 30), 30, AppointmentStatus.PENDING),       // 09:30-10:00
				appointment(TOMORROW.atTime(10, 15), 30, AppointmentStatus.CONFIRMED));   // 10:15-10:45

		// 09:00-09:30 termina justo cuando empieza la primera (adyacente) y 11:00 empieza después de la segunda
		assertThat(slots(availability(NOW, TOMORROW))).containsExactly("09:00-09:30", "11:00-11:30", "11:30-12:00");
	}

	@Test
	void anAppointmentEndingAtTenDoesNotBlockTheTenOClockSlot() {
		givenCatalog(30);
		givenSchedule(TOMORROW, "09:00-11:00");
		givenAppointments(TOMORROW, appointment(TOMORROW.atTime(9, 0), 60, AppointmentStatus.CONFIRMED));

		assertThat(slots(availability(NOW, TOMORROW))).containsExactly("10:00-10:30", "10:30-11:00");
	}

	@Test
	void anAppointmentFromTheDayBeforeThatCrossesMidnightBlocksTheFirstSlots() {
		givenCatalog(30);
		givenSchedule(TOMORROW, "00:00-02:00");
		// La consulta pide el día completo [00:00, 00:00 del día siguiente): givenAppointments lo exige
		givenAppointments(TOMORROW, appointment(TODAY.atTime(23, 30), 75, AppointmentStatus.CONFIRMED));   // hasta 00:45

		assertThat(slots(availability(NOW, TOMORROW))).containsExactly("01:00-01:30", "01:30-02:00");
	}

	// ---------- anticipación mínima y tope (RN-04) ----------

	@Test
	void todayStartsAtTheFirstStepThatMeetsTheMinimumNotice() {
		givenCatalog(30);
		givenSchedule(TODAY, "09:00-13:00");

		// Ahora 10:20 + 60 min = 11:20: el primer paso que cumple es 11:30
		assertThat(slots(availability(NOW, TODAY))).containsExactly("11:30-12:00", "12:00-12:30", "12:30-13:00");
	}

	@Test
	void aMinimumNoticeThatCrossesMidnightTrimsTheStartOfTomorrow() {
		LocalDateTime lateNight = TODAY.atTime(23, 30);
		givenCatalog(30);
		givenSchedule(TOMORROW, "00:00-02:00");

		// 23:30 + 60 min = 00:30 de mañana
		assertThat(slots(availability(lateNight, TOMORROW))).containsExactly("00:30-01:00", "01:00-01:30", "01:30-02:00");
	}

	@Test
	void todayIsEmptyWithoutQueriesWhenTheMinimumNoticeAlreadyEndsTomorrow() {
		givenCatalog(30);

		assertThat(slots(availability(TODAY.atTime(23, 30), TODAY))).isEmpty();
		verifyNoInteractions(workingHours, appointments);
	}

	@Test
	void theMaxDaysAheadLimitIncludesItsExactMinute() {
		LocalDateTime now = TODAY.atTime(10, 0);
		LocalDate lastDay = TODAY.plusDays(60);                                   // 2027-01-01
		givenCatalog(30);
		givenSchedule(lastDay, "09:00-12:00");

		// Tope = 2027-01-01T10:00: el slot de las 10:00 entra justo; el de las 10:30 ya no
		assertThat(slots(availability(now, lastDay))).containsExactly("09:00-09:30", "09:30-10:00", "10:00-10:30");
	}

	@Test
	void oneMinuteAfterTheMaxDaysAheadLimitIsExcluded() {
		LocalDateTime now = TODAY.atTime(9, 59);
		LocalDate lastDay = TODAY.plusDays(60);
		givenCatalog(30);
		givenSchedule(lastDay, "09:00-12:00");

		// Tope = 2027-01-01T09:59: el slot de las 10:00 empieza un minuto después
		assertThat(slots(availability(now, lastDay))).containsExactly("09:00-09:30", "09:30-10:00");
	}

	@Test
	void aDateBeyondTheLimitIsEmptyWithoutQueries() {
		givenCatalog(30);

		for (LocalDate date : List.of(TODAY.plusDays(61), LocalDate.MAX)) {
			AvailabilityResponse response = availability(NOW, date);
			assertThat(response.slots()).isEmpty();
			assertThat(response.date()).isEqualTo(date);
		}
		verifyNoInteractions(workingHours, appointments);
	}

	@Test
	void aPastDateIsEmptyWithoutQueries() {
		givenCatalog(30);

		for (LocalDate date : List.of(TODAY.minusDays(1), LocalDate.MIN)) {
			assertThat(availability(NOW, date).slots()).isEmpty();
		}
		verifyNoInteractions(workingHours, appointments);
	}

	@Test
	void theResponseEchoesTheRequestAndTheServiceDuration() {
		givenCatalog(45);
		givenSchedule(TOMORROW, "09:00-10:00");

		AvailabilityResponse response = availability(NOW, TOMORROW);

		assertThat(response.professionalId()).isEqualTo(PROFESSIONAL_ID);
		assertThat(response.serviceId()).isEqualTo(SERVICE_ID);
		assertThat(response.date()).isEqualTo(TOMORROW);
		assertThat(response.durationMinutes()).isEqualTo(45);
		assertThat(response.slots()).containsExactly(
				new AvailabilityResponse.Slot(TOMORROW.atTime(9, 0), TOMORROW.atTime(9, 45)));
	}

	// ---------- errores ----------

	@Test
	void anUnknownProfessionalIsNotFound() {
		when(professionals.findWithDetailsById(PROFESSIONAL_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> availability(NOW, TOMORROW)).isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(services, workingHours, appointments);
	}

	@Test
	void anInactiveProfileOrUserIsNotFoundEvenForAnAdmin() {
		authenticateAsAdmin();
		ServiceOffering service = service(SERVICE_ID, 30, true);
		for (Professional hidden : List.of(professional(false, true, service), professional(true, false, service))) {
			when(professionals.findWithDetailsById(PROFESSIONAL_ID)).thenReturn(Optional.of(hidden));

			assertThatThrownBy(() -> availability(NOW, TOMORROW)).isInstanceOf(ResourceNotFoundException.class);
		}
		verifyNoInteractions(services, workingHours, appointments);
	}

	@Test
	void anUnknownOrInactiveServiceIsNotFound() {
		ServiceOffering inactive = service(SERVICE_ID, 30, false);
		when(professionals.findWithDetailsById(PROFESSIONAL_ID)).thenReturn(Optional.of(professional(true, true, inactive)));
		for (Optional<ServiceOffering> found : List.of(Optional.<ServiceOffering>empty(), Optional.of(inactive))) {
			when(services.findById(SERVICE_ID)).thenReturn(found);

			assertThatThrownBy(() -> availability(NOW, TOMORROW)).isInstanceOf(ResourceNotFoundException.class);
		}
		verifyNoInteractions(workingHours, appointments);
	}

	@Test
	void aServiceTheProfessionalDoesNotOfferIs422() {
		ServiceOffering other = service(99L, 30, true);
		when(professionals.findWithDetailsById(PROFESSIONAL_ID)).thenReturn(Optional.of(professional(true, true, other)));
		when(services.findById(SERVICE_ID)).thenReturn(Optional.of(service(SERVICE_ID, 30, true)));

		assertThatThrownBy(() -> availability(NOW, TOMORROW))
				.isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
					assertThat(ex.getCode()).isEqualTo("SERVICE_NOT_OFFERED");
				});
		verifyNoInteractions(workingHours, appointments);
	}

	// ---------- utilidades ----------

	private AvailabilityResponse availability(LocalDateTime now, LocalDate date) {
		Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
		AppProperties props = new AppProperties("America/Bogota", null, new AppProperties.Booking(30, 60, 2, 60), null);
		WorkingHourService workingHourService = new WorkingHourService(professionals, workingHours, new WorkingHourMapper());
		return new AvailabilityService(professionals, services, workingHourService, appointments, props, clock)
				.find(PROFESSIONAL_ID, date, SERVICE_ID);
	}

	// Profesional y usuario activos que ofrecen el servicio pedido, de la duración indicada
	private void givenCatalog(int durationMinutes) {
		ServiceOffering service = service(SERVICE_ID, durationMinutes, true);
		when(professionals.findWithDetailsById(PROFESSIONAL_ID)).thenReturn(Optional.of(professional(true, true, service)));
		when(services.findById(SERVICE_ID)).thenReturn(Optional.of(service));
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

	// Lo que devuelve la consulta de citas activas, que debe pedirse para el día completo de date
	private void givenAppointments(LocalDate date, Appointment... busy) {
		when(appointments.findActiveOverlapping(PROFESSIONAL_ID, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
				.thenReturn(List.of(busy));
	}

	private static Appointment appointment(LocalDateTime start, int minutes, AppointmentStatus status) {
		Appointment appointment = new Appointment();
		appointment.setStartTime(start);
		appointment.setEndTime(start.plusMinutes(minutes));
		appointment.setStatus(status);
		return appointment;
	}

	private static ServiceOffering service(Long id, int durationMinutes, boolean active) {
		ServiceOffering service = new ServiceOffering();
		service.setId(id);
		service.setName("Servicio " + id);
		service.setDurationMinutes(durationMinutes);
		service.setPrice(new BigDecimal("50000.00"));
		service.setActive(active);
		return service;
	}

	private static Professional professional(boolean active, boolean userActive, ServiceOffering... offered) {
		User user = new User();
		user.setId(50L);
		user.setRole(Role.PROFESSIONAL);
		user.setActive(userActive);
		Professional professional = new Professional();
		professional.setId(PROFESSIONAL_ID);
		professional.setUser(user);
		professional.setSpecialty("Fisioterapia");
		professional.setActive(active);
		professional.setServices(new HashSet<>(List.of(offered)));
		return professional;
	}

	// Cada slot como "HH:mm-HH:mm", en el orden en que vienen
	private static List<String> slots(AvailabilityResponse response) {
		return response.slots().stream().map(s -> s.start().toLocalTime() + "-" + s.end().toLocalTime()).toList();
	}

	private static void authenticateAsAdmin() {
		AuthUser admin = new AuthUser(1L, "admin@mail.test", "hash-sintetico", Role.ADMIN, true);
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()));
	}

}
