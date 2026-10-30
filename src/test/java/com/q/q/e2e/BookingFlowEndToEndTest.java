package com.q.q.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.TestClockConfiguration;
import com.q.q.TestcontainersConfiguration;
import com.q.q.entity.Appointment;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.repository.AppointmentRepository;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.ServiceOfferingRepository;
import com.q.q.repository.UserRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * De extremo a extremo: la aplicación completa en un puerto real, MySQL en Testcontainers y un cliente HTTP real, sin
 * MockMvc. El reloj está fijo en el lunes 2026-11-02 a las 08:00 (TestClockConfiguration).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
@TestPropertySource(properties = {
		"app.jwt.secret=" + IntegrationTest.JWT_SECRET,
		"app.jwt.expiration-minutes=60",
		"app.admin.email=" + IntegrationTest.ADMIN_EMAIL,
		"app.admin.password=" + IntegrationTest.ADMIN_PASSWORD })
class BookingFlowEndToEndTest {

	private static final String PASSWORD = "clave-segura-123";
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private final HttpClient http = HttpClient.newHttpClient();

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private UserRepository users;

	@Autowired
	private ProfessionalRepository professionals;

	@Autowired
	private ServiceOfferingRepository services;

	private record Response(int status, String body, String location) {

		<T> T read(String path) {
			return JsonPath.read(body, path);
		}

		long id() {
			return this.<Number>read("$.id").longValue();
		}
	}

	private record Catalog(String admin, long serviceId, long professionalId, String professionalToken) {}

	// Sección 12.3, pasos 1 a 12
	@Test
	void theFullBookingFlowWorksOverRealHttp() throws Exception {
		// 1-5: el ADMIN arma el catálogo y el profesional inicia sesión
		Catalog catalog = catalog();

		// 6: un cliente se registra e inicia sesión
		String clientEmail = email("cliente");
		Response registered = send("POST", "/auth/register", null,
				Map.of("fullName", "Ana Pérez", "email", clientEmail, "password", PASSWORD));
		assertThat(registered.status()).isEqualTo(201);
		assertThat(registered.location()).endsWith("/api/v1/users/me");
		String client = login(clientEmail);

		// 7: disponibilidad del jueves 2026-11-05
		Response availability = send("GET", "/professionals/" + catalog.professionalId() + "/availability?date=2026-11-05&serviceId="
				+ catalog.serviceId(), null, null);
		assertThat(availability.status()).isEqualTo(200);
		String slotStart = availability.read("$.slots[0].start");
		assertThat(slotStart).isEqualTo("2026-11-05T09:00:00");

		// 8: reserva; 9: repetirla da 409
		Map<String, Object> booking = Map.of("professionalId", catalog.professionalId(), "serviceId", catalog.serviceId(),
				"startTime", slotStart, "notes", "Primera visita");
		Response created = send("POST", "/appointments", client, booking);
		assertThat(created.status()).isEqualTo(201);
		assertThat(created.location()).isEqualTo(base() + "/appointments/" + created.id());
		assertThat(created.<String>read("$.status")).isEqualTo("PENDING");
		assertThat(created.body()).contains("\"price\":80000.00");
		long appointmentId = created.id();
		assertCode(send("POST", "/appointments", client, booking), 409, "SLOT_NOT_AVAILABLE");

		// 10: el profesional confirma; 11: el cliente cancela (faltan días: la ventana de RN-08 está abierta)
		Response confirmed = send("PATCH", "/appointments/" + appointmentId + "/confirm", catalog.professionalToken(), null);
		assertThat(confirmed.status()).isEqualTo(200);
		assertThat(confirmed.<String>read("$.status")).isEqualTo("CONFIRMED");
		Response cancelled = send("PATCH", "/appointments/" + appointmentId + "/cancel", client,
				Map.of("reason", "Me surgió un viaje"));
		assertThat(cancelled.status()).isEqualTo(200);
		assertThat(cancelled.<String>read("$.status")).isEqualTo("CANCELLED");
		assertThat(cancelled.<String>read("$.cancelReason")).isEqualTo("Me surgió un viaje");

		// 12: casos negativos
		assertCode(send("GET", "/appointments", null, null), 401, "AUTHENTICATION_REQUIRED");
		assertCode(send("POST", "/services", client, Map.of("name", "Intento", "durationMinutes", 30, "price", 1)), 403,
				"ACCESS_DENIED");
		assertCode(send("POST", "/appointments", client, Map.of()), 400, "VALIDATION_ERROR");
		assertCode(send("GET", "/appointments/99999999", client, null), 404, "RESOURCE_NOT_FOUND");
		assertCode(send("PATCH", "/appointments/" + appointmentId + "/confirm", catalog.professionalToken(), null), 409,
				"INVALID_STATUS_TRANSITION");

		// El cliente ve su cita cancelada; el horario quedó libre otra vez
		Response mine = send("GET", "/appointments", client, null);
		assertThat(mine.<List<Integer>>read("$.content[*].id")).containsExactly((int) appointmentId);
		assertThat(mine.<List<String>>read("$.content[*].status")).containsExactly("CANCELLED");
		assertThat(send("GET", "/professionals/" + catalog.professionalId() + "/availability?date=2026-11-05&serviceId="
				+ catalog.serviceId(), null, null).<List<String>>read("$.slots[*].start")).contains(slotStart);
	}

	// Opcional: reprogramar y marcar inasistencia
	@Test
	void rescheduleAndNoShowWorkOverRealHttp() throws Exception {
		Catalog catalog = catalog();
		String clientEmail = email("cliente");
		assertThat(send("POST", "/auth/register", null,
				Map.of("fullName", "Bea Gómez", "email", clientEmail, "password", PASSWORD)).status()).isEqualTo(201);
		String client = login(clientEmail);
		Response created = send("POST", "/appointments", client, Map.of("professionalId", catalog.professionalId(),
				"serviceId", catalog.serviceId(), "startTime", "2026-11-05T10:00:00"));
		assertThat(created.status()).isEqualTo(201);

		Response moved = send("PATCH", "/appointments/" + created.id() + "/reschedule", client,
				Map.of("startTime", "2026-11-05T11:00:00"));
		assertThat(moved.status()).isEqualTo(200);
		assertThat(moved.<String>read("$.startTime")).isEqualTo("2026-11-05T11:00:00");
		assertThat(moved.<String>read("$.status")).isEqualTo("PENDING");
		List<String> free = send("GET", "/professionals/" + catalog.professionalId() + "/availability?date=2026-11-05&serviceId="
				+ catalog.serviceId(), null, null).read("$.slots[*].start");
		assertThat(free).contains("2026-11-05T10:00:00").doesNotContain("2026-11-05T11:00:00");

		// La cita de mañana todavía no empezó
		send("PATCH", "/appointments/" + created.id() + "/confirm", catalog.professionalToken(), null);
		assertCode(send("PATCH", "/appointments/" + created.id() + "/no-show", catalog.professionalToken(), null), 422,
				"APPOINTMENT_NOT_STARTED");

		// Una cita que ya empezó solo puede existir antes de "ahora", que está fijo: se inserta por repositorio
		long started = startedConfirmedAppointment(catalog, clientEmail);
		Response noShow = send("PATCH", "/appointments/" + started + "/no-show", catalog.professionalToken(), null);
		assertThat(noShow.status()).isEqualTo(200);
		assertThat(noShow.<String>read("$.status")).isEqualTo("NO_SHOW");
		assertCode(send("PATCH", "/appointments/" + started + "/cancel", client, null), 409, "INVALID_STATUS_TRANSITION");
	}

	// ---------- utilidades ----------

	// Pasos 1 a 5 de 12.3: login ADMIN, servicio, profesional que lo ofrece, su horario y su login
	private Catalog catalog() throws Exception {
		String admin = login(IntegrationTest.ADMIN_EMAIL, IntegrationTest.ADMIN_PASSWORD);
		Response service = send("POST", "/services", admin, Map.of("name", "Consulta e2e " + SEQUENCE.incrementAndGet()
				+ "-" + System.nanoTime(), "durationMinutes", 30, "price", new BigDecimal("80000.00")));
		assertThat(service.status()).isEqualTo(201);
		String professionalEmail = email("profesional");
		Response professional = send("POST", "/professionals", admin, Map.of("fullName", "Carlos Ruiz",
				"email", professionalEmail, "password", PASSWORD, "specialty", "Fisioterapia",
				"serviceIds", List.of(service.id())));
		assertThat(professional.status()).isEqualTo(201);
		List<Map<String, String>> blocks = new ArrayList<>();
		for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")) {
			blocks.add(Map.of("dayOfWeek", day, "startTime", "09:00", "endTime", "12:00"));
			blocks.add(Map.of("dayOfWeek", day, "startTime", "14:00", "endTime", "18:00"));
		}
		assertThat(send("PUT", "/professionals/" + professional.id() + "/working-hours", admin, Map.of("blocks", blocks))
				.status()).isEqualTo(200);
		return new Catalog(admin, service.id(), professional.id(), login(professionalEmail));
	}

	private long startedConfirmedAppointment(Catalog catalog, String clientEmail) {
		Appointment appointment = new Appointment();
		appointment.setClient(users.findByEmail(clientEmail).orElseThrow());
		appointment.setProfessional(professionals.findById(catalog.professionalId()).orElseThrow());
		appointment.setService(services.findById(catalog.serviceId()).orElseThrow());
		appointment.setStartTime(LocalDateTime.of(2026, 11, 2, 7, 30));                // empezó antes de las 08:00
		appointment.setEndTime(LocalDateTime.of(2026, 11, 2, 8, 0));
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setPrice(new BigDecimal("80000.00"));
		return appointments.save(appointment).getId();
	}

	private String login(String email) throws Exception {
		return login(email, PASSWORD);
	}

	private String login(String email, String password) throws Exception {
		Response response = send("POST", "/auth/login", null, Map.of("email", email, "password", password));
		assertThat(response.status()).isEqualTo(200);
		return response.read("$.accessToken");
	}

	private Response send(String method, String path, String token, Object body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base() + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
		if (body != null) {
			request.header("Content-Type", "application/json");
		}
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		return new Response(response.statusCode(), response.body(), response.headers().firstValue("Location").orElse(null));
	}

	private static void assertCode(Response response, int status, String code) {
		assertThat(response.status()).isEqualTo(status);
		assertThat(response.<String>read("$.code")).isEqualTo(code);
	}

	private String base() {
		return "http://localhost:" + port + "/api/v1";
	}

	private static String email(String role) {
		return "e2e-" + role + SEQUENCE.incrementAndGet() + "-" + System.nanoTime() + "@mail.test";
	}

}
