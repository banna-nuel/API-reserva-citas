package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.entity.enums.AppointmentStatus;

// POST /appointments: el ejemplo de 8.3, cada código de error, las reglas de solape y la concurrencia
@IntegrationTest
class AppointmentIntegrationTest extends AppointmentTestSupport {

	private static final String[] APPOINTMENT_KEYS = { "id", "client", "professional", "service", "startTime", "endTime",
			"status", "price", "notes", "cancelReason", "createdAt" };
	private static final long MISSING_ID = 99_999_999L;

	// ---------- caso feliz ----------

	@Test
	void theSectionExampleIsBookedWith201AndLocation() throws Exception {
		String serviceName = uniqueName("Consulta general");
		Catalog catalog = catalog("Carlos Ruiz", serviceName, 30, "80000.00");
		Client ana = newClient("Ana Pérez");
		Map<String, Object> request = booking(catalog.professionalId(), catalog.serviceId(), "2026-11-05T10:00:00");
		request.put("notes", "Primera visita");

		MvcResult result = book(ana.token(), request)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.client.id").value(ana.id().intValue()))
				.andExpect(jsonPath("$.client.fullName").value("Ana Pérez"))
				.andExpect(jsonPath("$.professional.id").value(catalog.professionalId().intValue()))
				.andExpect(jsonPath("$.professional.fullName").value("Carlos Ruiz"))
				.andExpect(jsonPath("$.service.id").value(catalog.serviceId().intValue()))
				.andExpect(jsonPath("$.service.name").value(serviceName))
				.andExpect(jsonPath("$.service.durationMinutes").value(30))
				.andExpect(jsonPath("$.startTime").value("2026-11-05T10:00:00"))
				.andExpect(jsonPath("$.endTime").value("2026-11-05T10:30:00"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.notes").value("Primera visita"))
				.andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")))
				.andReturn();
		String created = body(result);
		Long id = JsonPath.<Number>read(created, "$.id").longValue();
		assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/appointments/" + id);
		assertThat(keys(created)).containsExactlyInAnyOrder(APPOINTMENT_KEYS);
		assertThat(created).contains("\"price\":80000.00", "\"cancelReason\":null");
		assertThat(JsonPath.<Map<String, Object>>read(created, "$.client")).containsOnlyKeys("id", "fullName");
		assertThat(JsonPath.<Map<String, Object>>read(created, "$.professional")).containsOnlyKeys("id", "fullName");
		assertThat(JsonPath.<Map<String, Object>>read(created, "$.service")).containsOnlyKeys("id", "name", "durationMinutes");

		// Queda guardada tal cual: el detalle la devuelve igual
		mvc.perform(authorized(get(APPOINTMENTS + "/" + id), ana.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.startTime").value("2026-11-05T10:00:00"))
				.andExpect(jsonPath("$.price").value(80000.0))
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void notesAreTrimmedAndBlankNotesAreSavedAsNull() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		Map<String, Object> trimmed = booking(catalog, THURSDAY.atTime(9, 0));
		trimmed.put("notes", "   Traer exámenes   ");
		Map<String, Object> blank = booking(catalog, THURSDAY.atTime(9, 30));
		blank.put("notes", "    ");

		book(client.token(), trimmed).andExpect(status().isCreated()).andExpect(jsonPath("$.notes").value("Traer exámenes"));
		String saved = body(book(client.token(), blank).andExpect(status().isCreated()).andReturn());
		assertThat(saved).contains("\"notes\":null");
	}

	// ---------- anticipación, tope y jornada ----------

	@Test
	void eachTimeRuleHasItsOwnCode() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		Map<String, List<String>> cases = Map.of(
				// Ahora es lunes 08:00: el mínimo es 09:00 (08:30 además está fuera de jornada; gana TOO_SOON)
				"TOO_SOON", List.of("2026-11-02T08:30:00", "2026-11-02T08:59:00", "2026-11-01T10:00:00"),
				// El tope es 2027-01-01T08:00; el último valor no debe desbordar al sumar la duración
				"TOO_FAR", List.of("2027-01-01T09:00:00", "2027-03-01T10:00:00", "+999999999-12-31T23:59:59"),
				// Pausa del mediodía, cruzando el fin del bloque y sábado sin horario
				"OUTSIDE_WORKING_HOURS", List.of("2026-11-05T12:00:00", "2026-11-05T11:45:00", "2026-11-07T10:00:00"),
				// Cabe en la jornada, pero no es uno de los horarios de la disponibilidad
				"INVALID_SLOT_START", List.of("2026-11-05T10:07:00", "2026-11-05T10:00:30", "2026-11-05T10:00:00.5"));

		for (Map.Entry<String, List<String>> expected : cases.entrySet()) {
			for (String start : expected.getValue()) {
				book(client.token(), booking(catalog.professionalId(), catalog.serviceId(), start))
						.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, expected.getKey(), APPOINTMENTS));
			}
		}
		assertThat(appointments.countOverlappingForClient(client.id(), MONDAY.atStartOfDay(),
				MONDAY.plusYears(1).atStartOfDay(), null)).isZero();
	}

	@Test
	void theFirstSlotAfterTheMinimumNoticeIsBookable() throws Exception {
		Catalog catalog = catalog(30);

		// Justo en el límite: ahora (lunes 08:00) + 60 min
		bookOk(newClient().token(), catalog, MONDAY.atTime(9, 0));
	}

	// ---------- profesional y servicio ----------

	@Test
	void unknownOrInactiveProfessionalsAndServicesAre404AndNotOfferedServicesAre422() throws Exception {
		Catalog inactiveProfile = catalog(30);
		Catalog inactiveUser = catalog(30);
		Catalog active = catalog(30);
		String admin = active.admin();
		Client client = newClient();
		mvc.perform(withJson(patch(PROFESSIONALS + "/" + inactiveProfile.professionalId() + "/active"), admin,
				Map.of("active", false))).andExpect(status().isOk());
		mvc.perform(withJson(patch("/api/v1/users/" + inactiveUser.professionalUserId() + "/active"), admin,
				Map.of("active", false))).andExpect(status().isOk());
		Long notOffered = createService(admin, uniqueName("No ofrecido"), 30, "50000.00");
		Long retired = createService(admin, uniqueName("Retirado"), 30, "50000.00");
		offer(active, active.serviceId(), retired);
		mvc.perform(withJson(patch(SERVICES + "/" + retired + "/active"), admin, Map.of("active", false)))
				.andExpect(status().isOk());
		String start = "2026-11-05T10:00:00";

		for (String token : new String[] { client.token(), admin }) {
			for (Long professionalId : List.of(MISSING_ID, inactiveProfile.professionalId(), inactiveUser.professionalId())) {
				book(token, booking(professionalId, active.serviceId(), start))
						.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", APPOINTMENTS));
			}
			for (Long serviceId : List.of(MISSING_ID, retired)) {
				book(token, booking(active.professionalId(), serviceId, start))
						.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", APPOINTMENTS));
			}
			// RN-05; además es TOO_SOON, que va después
			book(token, booking(active.professionalId(), notOffered, "2026-11-02T08:30:00"))
					.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "SERVICE_NOT_OFFERED", APPOINTMENTS));
		}
	}

	// ---------- solapes ----------

	@Test
	void theProfessionalCannotBeDoubleBookedEvenPartiallyButAdjacentSlotsAreFine() throws Exception {
		Catalog catalog = catalog(30);
		Long hourLong = createService(catalog.admin(), uniqueName("Una hora"), 60, "90000.00");
		offer(catalog, catalog.serviceId(), hourLong);
		bookOk(newClient().token(), catalog, THURSDAY.atTime(10, 0));                     // 10:00-10:30
		String other = newClient().token();

		book(other, booking(catalog, THURSDAY.atTime(10, 0)))
				.andExpectAll(apiError(HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE", APPOINTMENTS));
		// 09:30-10:30 solo se pisa con la mitad de la cita existente
		book(other, booking(catalog.professionalId(), hourLong, "2026-11-05T09:30:00"))
				.andExpectAll(apiError(HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE", APPOINTMENTS));
		// Adyacentes: 09:30-10:00 termina cuando empieza la existente y 10:30 empieza cuando termina
		bookOk(other, catalog, THURSDAY.atTime(9, 30));
		bookOk(newClient().token(), catalog, THURSDAY.atTime(10, 30));
	}

	@Test
	void theSameClientCannotBeInTwoPlacesAtOnce() throws Exception {
		Catalog first = catalog(30);
		Catalog second = catalog(30);
		Client client = newClient();
		bookOk(client.token(), first, THURSDAY.atTime(10, 0));

		book(client.token(), booking(second, THURSDAY.atTime(10, 0)))
				.andExpectAll(apiError(HttpStatus.CONFLICT, "CLIENT_OVERLAP", APPOINTMENTS));
		bookOk(client.token(), second, THURSDAY.atTime(10, 30));                            // adyacente
	}

	@Test
	void aCancelledAppointmentDoesNotBlockTheSlot() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		Long cancelled = bookOk(client.token(), catalog, THURSDAY.atTime(11, 0));
		setStatus(cancelled, AppointmentStatus.CANCELLED);

		// Ni al profesional (otro cliente) ni al mismo cliente
		bookOk(newClient().token(), catalog, THURSDAY.atTime(11, 0));
		Catalog another = catalog(30);
		bookOk(client.token(), another, THURSDAY.atTime(11, 0));
	}

	// ---------- precio y a nombre de quién ----------

	@Test
	void thePriceStaysFrozenWhenTheServiceChangesItsPrice() throws Exception {
		String serviceName = uniqueName("Precio congelado");
		Catalog catalog = catalog("Profesional", serviceName, 30, "80000.00");
		Client client = newClient();
		Long id = bookOk(client.token(), catalog, THURSDAY.atTime(14, 0));

		mvc.perform(withJson(put(SERVICES + "/" + catalog.serviceId()), catalog.admin(),
				Map.of("name", serviceName, "durationMinutes", 30, "price", new BigDecimal("95000.00"))))
				.andExpect(status().isOk());

		assertThat(body(mvc.perform(authorized(get(APPOINTMENTS + "/" + id), client.token()))
				.andExpect(status().isOk()).andReturn())).contains("\"price\":80000.00");
		String later = body(book(client.token(), booking(catalog, THURSDAY.atTime(15, 0)))
				.andExpect(status().isCreated()).andReturn());
		assertThat(later).contains("\"price\":95000.00");
	}

	@Test
	void clientIdIsIgnoredForClientsAndUsedByAdmins() throws Exception {
		Catalog catalog = catalog(30);
		Client ana = newClient();
		Client bea = newClient();
		String admin = catalog.admin();

		// Un CLIENT siempre reserva para sí mismo
		Map<String, Object> asAna = booking(catalog, THURSDAY.atTime(9, 0));
		asAna.put("clientId", bea.id());
		book(ana.token(), asAna).andExpect(status().isCreated()).andExpect(jsonPath("$.client.id").value(ana.id().intValue()));

		// El ADMIN reserva en nombre de un cliente, o a su propio nombre si lo omite
		Map<String, Object> forBea = booking(catalog, THURSDAY.atTime(9, 30));
		forBea.put("clientId", bea.id());
		book(admin, forBea).andExpect(status().isCreated()).andExpect(jsonPath("$.client.id").value(bea.id().intValue()));
		Long adminId = idOf(IntegrationTest.ADMIN_EMAIL);
		book(admin, booking(catalog, THURSDAY.atTime(10, 0)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.client.id").value(adminId.intValue()));

		// clientId inexistente, de un PROFESSIONAL, del propio ADMIN o de un cliente inactivo
		Client inactive = newClient();
		mvc.perform(withJson(patch("/api/v1/users/" + inactive.id() + "/active"), admin, Map.of("active", false)))
				.andExpect(status().isOk());
		for (Long clientId : List.of(MISSING_ID, catalog.professionalUserId(), adminId, inactive.id())) {
			Map<String, Object> request = booking(catalog, THURSDAY.atTime(11, 0));
			request.put("clientId", clientId);
			book(admin, request).andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_CLIENT_ID", APPOINTMENTS));
		}
	}

	// ---------- rol y body ----------

	@Test
	void professionalsGet403AndAnonymousGet401EvenWithAnInvalidBody() throws Exception {
		Catalog catalog = catalog(30);
		String professional = professionalToken(catalog);
		Object[] bodies = { booking(catalog, THURSDAY.atTime(10, 0)), Map.of(), "{ no es json" };

		for (Object requestBody : bodies) {
			book(professional, requestBody).andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", APPOINTMENTS));
			book(null, requestBody).andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", APPOINTMENTS));
		}
	}

	@Test
	void invalidBodiesAre400() throws Exception {
		Catalog catalog = catalog(30);
		String client = newClient().token();

		String missing = body(book(client, Map.of())
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", APPOINTMENTS)).andReturn());
		assertThat(JsonPath.<List<String>>read(missing, "$.details[*].field"))
				.containsExactlyInAnyOrder("professionalId", "serviceId", "startTime");
		Map<String, Object> longNotes = booking(catalog, THURSDAY.atTime(10, 0));
		longNotes.put("notes", "x".repeat(501));
		String tooLong = body(book(client, longNotes)
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", APPOINTMENTS)).andReturn());
		assertThat(JsonPath.<List<String>>read(tooLong, "$.details[*].field")).containsExactly("notes");

		// startTime con zona, sin fecha válida o de otro tipo; ids que no son números; JSON roto
		for (String startTime : new String[] { "2026-11-05T10:00:00Z", "2026-11-05T10:00:00-05:00", "mañana",
				"2026-11-31T10:00:00", "2026-11-05" }) {
			book(client, booking(catalog.professionalId(), catalog.serviceId(), startTime))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", APPOINTMENTS));
		}
		book(client, "{\"professionalId\":\"abc\",\"serviceId\":1,\"startTime\":\"2026-11-05T10:00:00\"}")
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", APPOINTMENTS));
		book(client, "{ no es json").andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", APPOINTMENTS));
	}

	// ---------- concurrencia ----------

	// Seis clientes distintos piden el mismo horario a la vez: el bloqueo del profesional las serializa
	@Test
	void concurrentBookingsOfTheSameSlotCreateExactlyOne() throws Exception {
		Catalog catalog = catalog(30);
		List<String> clients = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			clients.add(newClient().token());
		}
		for (int round = 0; round < 6; round++) {
			LocalDateTime start = THURSDAY.atTime(9, 0).plusMinutes(30L * round);
			List<String> outcomes = race(clients.stream()
					.map(token -> (Callable<ResultActions>) () -> book(token, booking(catalog, start)))
					.toList());

			assertThat(outcomes).as("ronda %d", round).containsExactlyInAnyOrder(
					"201", "409 SLOT_NOT_AVAILABLE", "409 SLOT_NOT_AVAILABLE", "409 SLOT_NOT_AVAILABLE",
					"409 SLOT_NOT_AVAILABLE", "409 SLOT_NOT_AVAILABLE");
			assertThat(appointments.countOverlapping(catalog.professionalId(), start, start.plusMinutes(30), null))
					.as("ronda %d", round).isEqualTo(1);
		}
	}

	// El mismo cliente con dos profesionales a la vez: el bloqueo del cliente hace que RN-06 también se cumpla
	@Test
	void theSameClientBookingTwoProfessionalsAtOnceGetsOnlyOne() throws Exception {
		Catalog first = catalog(30);
		Catalog second = catalog(30);
		Client client = newClient();
		for (int round = 0; round < 6; round++) {
			LocalDateTime start = FRIDAY.atTime(14, 0).plusMinutes(30L * round);
			List<String> outcomes = race(List.of(() -> book(client.token(), booking(first, start)),
					() -> book(client.token(), booking(second, start))));

			assertThat(outcomes).as("ronda %d", round).containsExactlyInAnyOrder("201", "409 CLIENT_OVERLAP");
			assertThat(appointments.countOverlappingForClient(client.id(), start, start.plusMinutes(30), null))
					.as("ronda %d", round).isEqualTo(1);
		}
	}

}
