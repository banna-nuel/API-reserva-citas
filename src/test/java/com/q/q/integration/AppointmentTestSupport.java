package com.q.q.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.q.q.TestClockConfiguration;
import com.q.q.entity.Appointment;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.repository.AppointmentRepository;

/** Catálogo, clientes y reservas para las pruebas de citas. El reloj de la aplicación está fijo en TestClockConfiguration. */
abstract class AppointmentTestSupport extends IntegrationTestSupport {

	protected static final String PROFESSIONALS = "/api/v1/professionals";
	protected static final String SERVICES = "/api/v1/services";
	protected static final String APPOINTMENTS = "/api/v1/appointments";
	protected static final LocalDate MONDAY = TestClockConfiguration.NOW.toLocalDate();      // 2026-11-02, "hoy"
	protected static final LocalDate THURSDAY = MONDAY.plusDays(3);                            // 2026-11-05, el de 8.3
	protected static final LocalDate FRIDAY = MONDAY.plusDays(4);
	protected static final LocalDate SATURDAY = MONDAY.plusDays(5);
	protected static final DateTimeFormatter ISO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
	private static final List<String> WEEKDAYS = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");
	private static final AtomicInteger NAME_SEQUENCE = new AtomicInteger();

	@Autowired
	protected AppointmentRepository appointments;

	protected record Catalog(String admin, Long professionalId, Long professionalUserId, String professionalEmail,
			Long serviceId) {}

	protected record Client(Long id, String token) {}

	protected static String uniqueName(String prefix) {
		return prefix + " " + NAME_SEQUENCE.incrementAndGet() + "-" + System.nanoTime();
	}

	// Servicio, profesional que lo ofrece y el horario de la sección 12.3: lunes a viernes de 09:00 a 12:00 y de
	// 14:00 a 18:00
	protected Catalog catalog(String professionalName, String serviceName, int durationMinutes, String price)
			throws Exception {
		String admin = adminToken();
		Long serviceId = createService(admin, serviceName, durationMinutes, price);
		String email = uniqueEmail();
		Map<String, Object> request = Map.of("fullName", professionalName, "email", email, "password", PASSWORD,
				"specialty", "Fisioterapia", "serviceIds", List.of(serviceId));
		String created = body(mvc.perform(withJson(post(PROFESSIONALS), admin, request))
				.andExpect(status().isCreated()).andReturn());
		Long professionalId = JsonPath.<Number>read(created, "$.id").longValue();
		List<Map<String, Object>> blocks = new ArrayList<>();
		for (String day : WEEKDAYS) {
			blocks.add(Map.of("dayOfWeek", day, "startTime", "09:00", "endTime", "12:00"));
			blocks.add(Map.of("dayOfWeek", day, "startTime", "14:00", "endTime", "18:00"));
		}
		mvc.perform(withJson(put(PROFESSIONALS + "/" + professionalId + "/working-hours"), admin, Map.of("blocks", blocks)))
				.andExpect(status().isOk());
		return new Catalog(admin, professionalId, JsonPath.<Number>read(created, "$.userId").longValue(), email,
				serviceId);
	}

	protected Catalog catalog(int durationMinutes) throws Exception {
		return catalog("Profesional con agenda", uniqueName("Servicio"), durationMinutes, "50000.00");
	}

	protected Long createService(String admin, String name, int durationMinutes, String price) throws Exception {
		Map<String, Object> request = Map.of("name", name, "durationMinutes", durationMinutes, "price", new BigDecimal(price));
		String created = body(mvc.perform(withJson(post(SERVICES), admin, request))
				.andExpect(status().isCreated()).andReturn());
		return JsonPath.<Number>read(created, "$.id").longValue();
	}

	// Reemplaza los servicios que ofrece el profesional
	protected void offer(Catalog catalog, Long... serviceIds) throws Exception {
		mvc.perform(withJson(put(PROFESSIONALS + "/" + catalog.professionalId() + "/services"), catalog.admin(),
				Map.of("serviceIds", List.of(serviceIds)))).andExpect(status().isOk());
	}

	protected Client newClient(String fullName) throws Exception {
		String email = uniqueEmail();
		mvc.perform(register(fullName, email, PASSWORD)).andExpect(status().isCreated());
		return new Client(idOf(email), loginToken(email, PASSWORD));
	}

	protected Client newClient() throws Exception {
		return newClient("Cliente de prueba");
	}

	protected String professionalToken(Catalog catalog) throws Exception {
		return loginToken(catalog.professionalEmail(), PASSWORD);
	}

	// Body de POST /appointments; admite null (Map.of no) y startTime como texto, tal cual llega en el JSON
	protected static Map<String, Object> booking(Long professionalId, Long serviceId, String startTime) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("professionalId", professionalId);
		body.put("serviceId", serviceId);
		body.put("startTime", startTime);
		return body;
	}

	protected static Map<String, Object> booking(Catalog catalog, LocalDateTime start) {
		return booking(catalog.professionalId(), catalog.serviceId(), ISO_SECONDS.format(start));
	}

	protected ResultActions book(String token, Object requestBody) throws Exception {
		return mvc.perform(withJson(post(APPOINTMENTS), token, requestBody));
	}

	// Reserva que debe salir bien; devuelve el id de la cita
	protected Long bookOk(String token, Catalog catalog, LocalDateTime start) throws Exception {
		String created = body(book(token, booking(catalog, start)).andExpect(status().isCreated()).andReturn());
		return JsonPath.<Number>read(created, "$.id").longValue();
	}

	// Los cambios de estado llegan en la Fase 6b: aquí se aplican directamente en la base
	protected void setStatus(Long appointmentId, AppointmentStatus status) {
		Appointment appointment = appointments.findById(appointmentId).orElseThrow();
		appointment.setStatus(status);
		appointments.save(appointment);
	}

}
