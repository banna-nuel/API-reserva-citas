package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.entity.Appointment;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.repository.AppointmentRepository;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.ServiceOfferingRepository;

import jakarta.persistence.EntityManagerFactory;

// El catálogo se arma por la API como ADMIN; las citas, que llegan en la Fase 6, se insertan por repositorio.
// Las fechas salen del Clock de la aplicación: el día de prueba cae siempre dentro de la ventana de reserva
@IntegrationTest
class AvailabilityIntegrationTest extends IntegrationTestSupport {

	private static final String PROFESSIONALS = "/api/v1/professionals";
	private static final String SERVICES = "/api/v1/services";
	private static final String[] AVAILABILITY_KEYS = { "professionalId", "serviceId", "date", "durationMinutes", "slots" };
	private static final String[] SLOT_KEYS = { "start", "end" };
	private static final List<String> WEEK = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY",
			"SUNDAY");
	private static final long MISSING_ID = 99_999_999L;
	private static final AtomicInteger NAME_SEQUENCE = new AtomicInteger();

	@Autowired
	private Clock clock;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private ProfessionalRepository professionals;

	@Autowired
	private ServiceOfferingRepository services;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	// ---------- lectura ----------

	@Test
	void anonymousReadsTheFreeSlotsWithExactlyTheContractKeys() throws Exception {
		LocalDate day = futureDay();
		Catalog catalog = catalog(30, day, "14:00-18:00", "09:00-12:00");

		String body = availability(catalog, day, null);

		assertThat(keys(body)).containsExactlyInAnyOrder(AVAILABILITY_KEYS);
		assertThat(JsonPath.<Number>read(body, "$.professionalId").longValue()).isEqualTo(catalog.professionalId());
		assertThat(JsonPath.<Number>read(body, "$.serviceId").longValue()).isEqualTo(catalog.serviceId());
		assertThat(JsonPath.<String>read(body, "$.date")).isEqualTo(day.toString());
		assertThat(JsonPath.<Integer>read(body, "$.durationMinutes")).isEqualTo(30);
		assertThat(JsonPath.<List<Map<String, Object>>>read(body, "$.slots"))
				.allSatisfy(slot -> assertThat(slot).containsOnlyKeys(SLOT_KEYS));
		// Fecha-hora ISO-8601 local, con segundos y sin zona (sección 8.1)
		assertThat(JsonPath.<String>read(body, "$.slots[0].start")).isEqualTo(day + "T09:00:00");
		assertThat(JsonPath.<String>read(body, "$.slots[0].end")).isEqualTo(day + "T09:30:00");
		// Ordenados, sin la pausa de 12:00 a 14:00 y sin pasar de las 18:00
		assertThat(slotTimes(body)).containsExactly(
				"09:00-09:30", "09:30-10:00", "10:00-10:30", "10:30-11:00", "11:00-11:30", "11:30-12:00",
				"14:00-14:30", "14:30-15:00", "15:00-15:30", "15:30-16:00", "16:00-16:30", "16:30-17:00",
				"17:00-17:30", "17:30-18:00");

		// Con token la respuesta es la misma, sea cual sea el rol
		String client = loginToken(registerClient(), PASSWORD);
		for (String token : new String[] { client, catalog.admin() }) {
			assertThat(availability(catalog, day, token)).isEqualTo(body);
		}
	}

	@Test
	void contiguousBlocksLetAServiceSpanThem() throws Exception {
		LocalDate day = futureDay();
		Catalog catalog = catalog(60, day, "16:00-17:00", "15:00-16:00");

		assertThat(slotTimes(availability(catalog, day, null))).containsExactly("15:00-16:00", "15:30-16:30", "16:00-17:00");
	}

	// ---------- citas ----------

	@Test
	void onlyPendingAndConfirmedAppointmentsOfThisProfessionalOccupySlots() throws Exception {
		LocalDate day = futureDay();
		Catalog catalog = catalog(30, day, "09:00-12:00");
		Catalog other = catalog(30, day, "09:00-12:00");
		Long client = idOf(registerClient());
		insertAppointment(catalog, client, day.atTime(9, 0), 30, AppointmentStatus.PENDING);
		insertAppointment(catalog, client, day.atTime(9, 30), 30, AppointmentStatus.CONFIRMED);
		insertAppointment(catalog, client, day.atTime(10, 0), 30, AppointmentStatus.CANCELLED);
		insertAppointment(catalog, client, day.atTime(10, 30), 30, AppointmentStatus.COMPLETED);
		insertAppointment(catalog, client, day.atTime(11, 0), 30, AppointmentStatus.NO_SHOW);
		insertAppointment(other, client, day.atTime(11, 30), 30, AppointmentStatus.CONFIRMED);   // de otro profesional

		assertThat(slotTimes(availability(catalog, day, null)))
				.containsExactly("10:00-10:30", "10:30-11:00", "11:00-11:30", "11:30-12:00");
		assertThat(slotTimes(availability(other, day, null)))
				.containsExactly("09:00-09:30", "09:30-10:00", "10:00-10:30", "10:30-11:00", "11:00-11:30");
	}

	@Test
	void adjacentAppointmentsDoNotBlockButOnesCrossingMidnightDo() throws Exception {
		LocalDate day = futureDay();
		Catalog catalog = catalog(30, day, "00:00-01:00", "09:00-11:00");
		Long client = idOf(registerClient());
		LocalDateTime midnight = day.atStartOfDay();
		insertAppointment(catalog, client, midnight.minusMinutes(90), 90, AppointmentStatus.CONFIRMED);   // 22:30-00:00
		insertAppointment(catalog, client, midnight.minusMinutes(30), 45, AppointmentStatus.PENDING);     // 23:30-00:15
		insertAppointment(catalog, client, day.atTime(9, 0), 60, AppointmentStatus.CONFIRMED);            // 09:00-10:00

		// 00:00 queda ocupado por la cita de la víspera que cruza la medianoche, no por la que termina a las 00:00;
		// la cita que termina a las 10:00 deja libre el slot de las 10:00
		assertThat(slotTimes(availability(catalog, day, null)))
				.containsExactly("00:30-01:00", "10:00-10:30", "10:30-11:00");
	}

	// La consulta del repositorio contra MySQL real: estados activos y límites estrictos del rango
	@Test
	void theActiveOverlapQueryUsesStrictBoundsAndIgnoresInactiveStatuses() throws Exception {
		LocalDate day = futureDay();
		Catalog catalog = catalog(30, day, "09:00-12:00");
		Long client = idOf(registerClient());
		LocalDateTime midnight = day.atStartOfDay();
		Long crossing = insertAppointment(catalog, client, midnight.minusMinutes(30), 60, AppointmentStatus.PENDING);
		insertAppointment(catalog, client, midnight.minusMinutes(60), 60, AppointmentStatus.CONFIRMED);   // termina a las 00:00
		insertAppointment(catalog, client, midnight.plusDays(1), 30, AppointmentStatus.CONFIRMED);        // empieza al día siguiente
		Long confirmed = insertAppointment(catalog, client, day.atTime(10, 0), 30, AppointmentStatus.CONFIRMED);
		for (AppointmentStatus inactive : List.of(AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED,
				AppointmentStatus.NO_SHOW)) {
			insertAppointment(catalog, client, day.atTime(11, 0), 30, inactive);
		}

		assertThat(appointments.findActiveOverlapping(catalog.professionalId(), midnight, midnight.plusDays(1)))
				.extracting(Appointment::getId)
				.containsExactlyInAnyOrder(crossing, confirmed);
	}

	// ---------- fechas fuera de la ventana de reserva (RN-04) ----------

	@Test
	void pastDatesAndDatesBeyondTheLimitAreEmptyWith200() throws Exception {
		LocalDate today = LocalDate.now(clock);
		Catalog catalog = catalog(30, today);
		List<Map<String, Object>> everyDay = new ArrayList<>();
		for (String weekDay : WEEK) {
			everyDay.add(block(weekDay, "00:00-23:59"));
		}
		putSchedule(catalog, everyDay);

		for (LocalDate date : List.of(today.minusDays(1), today.minusYears(1), today.plusDays(61),
				LocalDate.of(9999, 12, 31), LocalDate.MAX)) {
			String body = availability(catalog, date, null);
			assertThat(keys(body)).containsExactlyInAnyOrder(AVAILABILITY_KEYS);
			assertThat(JsonPath.<String>read(body, "$.date")).isEqualTo(date.toString());
			assertThat(JsonPath.<List<Object>>read(body, "$.slots")).as(date.toString()).isEmpty();
		}
		// Control: dentro de la ventana el mismo profesional sí tiene horarios
		assertThat(slotTimes(availability(catalog, futureDay(), null))).isNotEmpty();
	}

	// ---------- errores ----------

	@Test
	void missingOrMalformedParametersAre400() throws Exception {
		LocalDate day = futureDay();
		Catalog catalog = catalog(30, day, "09:00-12:00");
		String path = availabilityPath(catalog.professionalId());
		String service = String.valueOf(catalog.serviceId());

		mvc.perform(get(path).param("serviceId", service))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", path));
		mvc.perform(get(path).param("date", day.toString()))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", path));
		mvc.perform(get(path).param("date", "").param("serviceId", service))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", path));
		mvc.perform(get(path).param("date", day.toString()).param("serviceId", ""))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", path));
		for (String badDate : new String[] { "2026-11-5", "2026-13-01", "2026-02-30", "05/11/2026", "mañana",
				day + "T09:00:00" }) {
			mvc.perform(get(path).param("date", badDate).param("serviceId", service))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", path));
		}
		mvc.perform(get(path).param("date", day.toString()).param("serviceId", "abc"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", path));
		String badPath = PROFESSIONALS + "/abc/availability";
		mvc.perform(get(badPath).param("date", day.toString()).param("serviceId", service))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", badPath));
	}

	@Test
	void unknownOrInactiveProfessionalsAre404EvenForTheAdmin() throws Exception {
		LocalDate day = futureDay();
		Catalog inactiveProfile = catalog(30, day, "09:00-12:00");
		Catalog inactiveUser = catalog(30, day, "09:00-12:00");
		String admin = inactiveProfile.admin();
		String client = loginToken(registerClient(), PASSWORD);
		mvc.perform(withJson(patch(PROFESSIONALS + "/" + inactiveProfile.professionalId() + "/active"), admin,
				Map.of("active", false))).andExpect(status().isOk());
		mvc.perform(withJson(patch("/api/v1/users/" + inactiveUser.userId() + "/active"), admin,
				Map.of("active", false))).andExpect(status().isOk());

		for (String token : new String[] { null, client, admin }) {
			for (Catalog hidden : List.of(inactiveProfile, inactiveUser)) {
				String path = availabilityPath(hidden.professionalId());
				mvc.perform(authorized(query(path, day, hidden.serviceId()), token))
						.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
			}
			String missing = availabilityPath(MISSING_ID);
			mvc.perform(authorized(query(missing, day, inactiveProfile.serviceId()), token))
					.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", missing));
		}
	}

	@Test
	void unknownOrInactiveServicesAre404AndNotOfferedOnesAre422() throws Exception {
		LocalDate day = futureDay();
		Catalog catalog = catalog(30, day, "09:00-12:00");
		String admin = catalog.admin();
		String path = availabilityPath(catalog.professionalId());
		Long notOffered = createService(admin, 30);
		Long retired = createService(admin, 30);
		mvc.perform(withJson(put(PROFESSIONALS + "/" + catalog.professionalId() + "/services"), admin,
				Map.of("serviceIds", List.of(catalog.serviceId(), retired)))).andExpect(status().isOk());
		mvc.perform(withJson(patch(SERVICES + "/" + retired + "/active"), admin, Map.of("active", false)))
				.andExpect(status().isOk());

		for (String token : new String[] { null, admin }) {
			// Inexistente, o asignado pero inactivo: 404 (también para el ADMIN)
			for (Long serviceId : List.of(MISSING_ID, retired)) {
				mvc.perform(authorized(query(path, day, serviceId), token))
						.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
			}
			// Activo, pero el profesional no lo ofrece (RN-05)
			mvc.perform(authorized(query(path, day, notOffered), token))
					.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "SERVICE_NOT_OFFERED", path));
		}
	}

	// ---------- consultas ----------

	// Sin N+1: el número de consultas no depende de cuántos bloques ni cuántas citas haya en el día
	@Test
	void runsAFixedNumberOfQueriesWhateverTheBlocksAndAppointments() throws Exception {
		LocalDate day = futureDay();
		Catalog small = catalog(30, day, "09:00-10:00");
		Catalog large = catalog(30, day, "06:00-07:00", "07:00-08:00", "08:00-09:00", "10:00-11:00", "13:00-18:00");
		Long client = idOf(registerClient());
		insertAppointment(small, client, day.atTime(9, 0), 30, AppointmentStatus.PENDING);
		for (int hour = 6; hour < 18; hour += 2) {
			insertAppointment(large, client, day.atTime(hour, 0), 30, AppointmentStatus.CONFIRMED);
		}

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		try {
			for (Catalog catalog : List.of(small, large)) {
				statistics.clear();
				assertThat(slotTimes(availability(catalog, day, null))).isNotEmpty();
				// Profesional con su usuario y servicios + horario del día + citas del día. El servicio no necesita
				// consulta propia: llegó con el profesional
				assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
			}
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	// ---------- utilidades ----------

	private record Catalog(String admin, Long professionalId, Long userId, Long serviceId) {}

	// Un día que siempre cae dentro de la ventana de reserva (ni hoy ni cerca del tope de 60 días)
	private LocalDate futureDay() {
		return LocalDate.now(clock).plusDays(7);
	}

	// Servicio de la duración indicada, profesional que lo ofrece y su horario para el día de la semana de day
	private Catalog catalog(int durationMinutes, LocalDate day, String... blocks) throws Exception {
		String admin = adminToken();
		Long serviceId = createService(admin, durationMinutes);
		Map<String, Object> request = Map.of("fullName", "Profesional con agenda", "email", uniqueEmail(),
				"password", PASSWORD, "specialty", "Fisioterapia", "serviceIds", List.of(serviceId));
		String created = body(mvc.perform(withJson(post(PROFESSIONALS), admin, request))
				.andExpect(status().isCreated()).andReturn());
		Catalog catalog = new Catalog(admin, JsonPath.<Number>read(created, "$.id").longValue(),
				JsonPath.<Number>read(created, "$.userId").longValue(), serviceId);
		List<Map<String, Object>> schedule = new ArrayList<>();
		for (String block : blocks) {
			schedule.add(block(day.getDayOfWeek().name(), block));
		}
		putSchedule(catalog, schedule);
		return catalog;
	}

	private Long createService(String adminToken, int durationMinutes) throws Exception {
		Map<String, Object> request = Map.of("name", "Disponibilidad " + NAME_SEQUENCE.incrementAndGet() + "-"
				+ System.nanoTime(), "durationMinutes", durationMinutes, "price", new BigDecimal("50000.00"));
		String created = body(mvc.perform(withJson(post(SERVICES), adminToken, request))
				.andExpect(status().isCreated()).andReturn());
		return JsonPath.<Number>read(created, "$.id").longValue();
	}

	private void putSchedule(Catalog catalog, List<Map<String, Object>> blocks) throws Exception {
		mvc.perform(withJson(put(PROFESSIONALS + "/" + catalog.professionalId() + "/working-hours"), catalog.admin(),
				Map.of("blocks", blocks))).andExpect(status().isOk());
	}

	private static Map<String, Object> block(String day, String range) {
		String[] parts = range.split("-");
		Map<String, Object> block = new LinkedHashMap<>();
		block.put("dayOfWeek", day);
		block.put("startTime", parts[0]);
		block.put("endTime", parts[1]);
		return block;
	}

	// Las citas llegan en la Fase 6: aquí se guardan directamente por repositorio
	private Long insertAppointment(Catalog catalog, Long clientId, LocalDateTime start, int minutes,
			AppointmentStatus status) {
		Appointment appointment = new Appointment();
		appointment.setClient(users.findById(clientId).orElseThrow());
		appointment.setProfessional(professionals.findById(catalog.professionalId()).orElseThrow());
		appointment.setService(services.findById(catalog.serviceId()).orElseThrow());
		appointment.setStartTime(start);
		appointment.setEndTime(start.plusMinutes(minutes));
		appointment.setStatus(status);
		appointment.setPrice(new BigDecimal("50000.00"));
		return appointments.save(appointment).getId();
	}

	private static String availabilityPath(Long professionalId) {
		return PROFESSIONALS + "/" + professionalId + "/availability";
	}

	private static MockHttpServletRequestBuilder query(String path, LocalDate date, Long serviceId) {
		return get(path).param("date", date.toString()).param("serviceId", String.valueOf(serviceId));
	}

	// GET de la disponibilidad; devuelve el cuerpo de una respuesta 200
	private String availability(Catalog catalog, LocalDate date, String token) throws Exception {
		return body(mvc.perform(authorized(query(availabilityPath(catalog.professionalId()), date, catalog.serviceId()),
				token)).andExpect(status().isOk()).andReturn());
	}

	// Cada slot como "HH:mm-HH:mm", en el orden en que vienen
	private static List<String> slotTimes(String availabilityBody) {
		return JsonPath.<List<Map<String, String>>>read(availabilityBody, "$.slots").stream()
				.map(slot -> LocalDateTime.parse(slot.get("start")).toLocalTime() + "-"
						+ LocalDateTime.parse(slot.get("end")).toLocalTime())
				.toList();
	}

}
