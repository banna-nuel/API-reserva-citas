package com.q.q.integration;

import static com.q.q.entity.enums.AppointmentStatus.COMPLETED;
import static com.q.q.entity.enums.AppointmentStatus.CONFIRMED;
import static com.q.q.entity.enums.AppointmentStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;

// PATCH /appointments/{id}/reschedule: RN-12 con las mismas reglas y códigos de la creación, ventana del cliente y
// concurrencia contra reservas y otras reprogramaciones. El reloj está fijo en el lunes 2026-11-02 a las 08:00
@IntegrationTest
class AppointmentRescheduleIntegrationTest extends AppointmentTestSupport {

	private static final String[] APPOINTMENT_KEYS = { "id", "client", "professional", "service", "startTime", "endTime",
			"status", "price", "notes", "cancelReason", "createdAt" };
	private static final long MISSING_ID = 99_999_999L;

	// ---------- permisos y body ----------

	@Test
	void onlyTheOwnerClientAndAdminsCanReschedule() throws Exception {
		Catalog catalog = catalog(30);
		Client owner = newClient();
		Long id = bookOk(owner.token(), catalog, THURSDAY.atTime(10, 0));
		String path = APPOINTMENTS + "/" + id + "/reschedule";
		String pro = professionalToken(catalog);

		// Un PROFESSIONAL, aunque sea el asignado, recibe 403 por URL también con un body inválido
		for (Object requestBody : new Object[] { to(THURSDAY.atTime(11, 0)), Map.of(), "{ no es json" }) {
			change(pro, id, "reschedule", requestBody).andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", path));
			change(null, id, "reschedule", requestBody)
					.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", path));
		}
		change(newClient().token(), id, "reschedule", to(THURSDAY.atTime(11, 0)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		String missing = APPOINTMENTS + "/" + MISSING_ID + "/reschedule";
		change(catalog.admin(), MISSING_ID, "reschedule", to(THURSDAY.atTime(11, 0)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", missing));

		String empty = body(change(owner.token(), id, "reschedule", Map.of())
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path)).andReturn());
		assertThat(JsonPath.<List<String>>read(empty, "$.details[*].field")).containsExactly("startTime");
		for (Object requestBody : new Object[] { Map.of("startTime", "2026-11-05T11:00:00Z"), "{ no es json" }) {
			change(owner.token(), id, "reschedule", requestBody)
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", path));
		}
		change(owner.token(), id, "reschedule", null).andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", path));
		assertThat(appointments.findById(id).orElseThrow().getStartTime()).isEqualTo(THURSDAY.atTime(10, 0));
	}

	// ---------- caso feliz ----------

	@Test
	void movingKeepsTheStatusThePriceAndTheOriginalDuration() throws Exception {
		String serviceName = uniqueName("Reprogramable");
		Catalog catalog = catalog("Profesional", serviceName, 30, "80000.00");
		Client client = newClient();
		Long id = bookOk(client.token(), catalog, THURSDAY.atTime(10, 0));
		change(professionalToken(catalog), id, "confirm", null).andExpect(status().isOk());
		// Después de reservar, el servicio pasa a durar 60 min y a costar 95000
		mvc.perform(withJson(put(SERVICES + "/" + catalog.serviceId()), catalog.admin(),
				Map.of("name", serviceName, "durationMinutes", 60, "price", new BigDecimal("95000.00"))))
				.andExpect(status().isOk());

		String moved = body(change(client.token(), id, "reschedule", to(THURSDAY.atTime(11, 0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.startTime").value("2026-11-05T11:00:00"))
				.andExpect(jsonPath("$.endTime").value("2026-11-05T11:30:00"))
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andReturn());
		assertThat(moved).contains("\"price\":80000.00");
		assertThat(keys(moved)).containsExactlyInAnyOrder(APPOINTMENT_KEYS);
		assertThat(statusOf(id)).isEqualTo(CONFIRMED);
		// El horario viejo queda libre
		bookOk(newClient().token(), catalog, THURSDAY.atTime(10, 0));
	}

	@Test
	void itMayOverlapItsOwnOldSlotButNotTheClientsOtherAppointments() throws Exception {
		Catalog hourLong = catalog("Profesional", uniqueName("Una hora"), 60, "50000.00");
		Catalog other = catalog(30);
		Client client = newClient();
		Long mine = bookOk(client.token(), hourLong, THURSDAY.atTime(10, 0));          // 10:00-11:00
		Long elsewhere = bookOk(client.token(), other, THURSDAY.atTime(14, 0));         // 14:00-14:30

		// 10:30-11:30 se pisa con su propio horario viejo: vale, la propia cita no cuenta (RN-12)
		change(client.token(), mine, "reschedule", to(THURSDAY.atTime(10, 30)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.endTime").value("2026-11-05T11:30:00"));
		// La otra cita del cliente sí cuenta: 11:00-11:30 se pisa con 10:30-11:30
		change(client.token(), elsewhere, "reschedule", to(THURSDAY.atTime(11, 0))).andExpectAll(apiError(
				HttpStatus.CONFLICT, "CLIENT_OVERLAP", APPOINTMENTS + "/" + elsewhere + "/reschedule"));
	}

	// ---------- reglas ----------

	@Test
	void eachScheduleRuleKeepsItsCreationCode() throws Exception {
		Catalog catalog = catalog(30);
		Catalog other = catalog(30);
		Client client = newClient();
		Long id = bookOk(client.token(), catalog, THURSDAY.atTime(10, 0));
		bookOk(newClient().token(), catalog, THURSDAY.atTime(11, 0));                 // ocupa al profesional
		bookOk(client.token(), other, THURSDAY.atTime(14, 0));                         // ocupa al cliente
		String path = APPOINTMENTS + "/" + id + "/reschedule";
		Map<String, Object[]> cases = new LinkedHashMap<>();
		cases.put("2026-11-02T08:30:00", new Object[] { HttpStatus.UNPROCESSABLE_CONTENT, "TOO_SOON" });
		cases.put("2027-01-01T09:00:00", new Object[] { HttpStatus.UNPROCESSABLE_CONTENT, "TOO_FAR" });
		cases.put("2026-11-05T12:00:00", new Object[] { HttpStatus.UNPROCESSABLE_CONTENT, "OUTSIDE_WORKING_HOURS" });
		cases.put("2026-11-05T10:07:00", new Object[] { HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_SLOT_START" });
		cases.put("2026-11-05T11:00:00", new Object[] { HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE" });
		cases.put("2026-11-05T14:00:00", new Object[] { HttpStatus.CONFLICT, "CLIENT_OVERLAP" });
		cases.put("2026-11-05T10:00:00", new Object[] { HttpStatus.UNPROCESSABLE_CONTENT, "SAME_START_TIME" });

		for (Map.Entry<String, Object[]> expected : cases.entrySet()) {
			change(client.token(), id, "reschedule", Map.of("startTime", expected.getKey()))
					.andExpectAll(apiError((HttpStatus) expected.getValue()[0], (String) expected.getValue()[1], path));
		}
		assertThat(appointments.findById(id).orElseThrow().getStartTime()).isEqualTo(THURSDAY.atTime(10, 0));
	}

	@Test
	void onlyActiveAppointmentsWithAnActiveProfessionalAndServiceCanBeMoved() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		String admin = catalog.admin();
		Long cancelled = bookOk(client.token(), catalog, THURSDAY.atTime(9, 0));
		change(client.token(), cancelled, "cancel", null).andExpect(status().isOk());
		Long completed = insert(catalog, client.id(), MONDAY.atTime(7, 0), 30, COMPLETED);
		for (Long id : List.of(cancelled, completed)) {
			change(admin, id, "reschedule", to(THURSDAY.atTime(9, 30))).andExpectAll(apiError(HttpStatus.CONFLICT,
					"INVALID_STATUS_TRANSITION", APPOINTMENTS + "/" + id + "/reschedule"));
		}

		Long active = bookOk(client.token(), catalog, THURSDAY.atTime(10, 0));
		String path = APPOINTMENTS + "/" + active + "/reschedule";
		Long replacement = createService(admin, uniqueName("Reemplazo"), 30, "50000.00");
		offer(catalog, replacement);                                                   // deja de ofrecer el de la cita
		change(client.token(), active, "reschedule", to(THURSDAY.atTime(11, 0)))
				.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "SERVICE_NOT_OFFERED", path));
		offer(catalog, catalog.serviceId(), replacement);
		setActive(admin, SERVICES + "/" + catalog.serviceId(), false);
		change(client.token(), active, "reschedule", to(THURSDAY.atTime(11, 0)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		setActive(admin, SERVICES + "/" + catalog.serviceId(), true);
		setActive(admin, PROFESSIONALS + "/" + catalog.professionalId(), false);
		change(client.token(), active, "reschedule", to(THURSDAY.atTime(11, 0)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		assertThat(statusOf(active)).isEqualTo(PENDING);
	}

	@Test
	void theClientWindowUsesTheCurrentStartAndAdminsHaveNoLimit() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		// Ahora es lunes 08:00: 10:00 está justo en el límite de 2 h, 09:30 ya no
		Long atLimit = bookOk(client.token(), catalog, MONDAY.atTime(10, 0));
		Long tooLate = bookOk(client.token(), catalog, MONDAY.atTime(9, 30));

		change(client.token(), tooLate, "reschedule", to(THURSDAY.atTime(9, 0))).andExpectAll(apiError(
				HttpStatus.UNPROCESSABLE_CONTENT, "RESCHEDULE_WINDOW_CLOSED", APPOINTMENTS + "/" + tooLate + "/reschedule"));
		change(catalog.admin(), tooLate, "reschedule", to(THURSDAY.atTime(9, 0))).andExpect(status().isOk());
		change(client.token(), atLimit, "reschedule", to(THURSDAY.atTime(9, 30))).andExpect(status().isOk());
	}

	// ---------- concurrencia ----------

	// Reprogramar y reservar compiten por el bloqueo del profesional: un solo ganador por horario
	@Test
	void aRescheduleAndABookingForTheSameSlotLeaveOnlyOne() throws Exception {
		Catalog catalog = catalog(30);
		Client mover = newClient();
		Client booker = newClient();
		for (int round = 0; round < 6; round++) {
			LocalDateTime target = THURSDAY.atTime(9, 0).plusMinutes(30L * round);
			Long id = bookOk(mover.token(), catalog, FRIDAY.atTime(9, 0).plusMinutes(30L * round));

			List<String> outcomes = race(List.of(() -> change(mover.token(), id, "reschedule", to(target)),
					() -> book(booker.token(), booking(catalog, target))));

			assertThat(outcomes).as("ronda %d", round).filteredOn(o -> o.equals("200") || o.equals("201")).hasSize(1);
			assertThat(outcomes).as("ronda %d", round).filteredOn(o -> o.equals("409 SLOT_NOT_AVAILABLE")).hasSize(1);
			assertThat(appointments.countOverlapping(catalog.professionalId(), target, target.plusMinutes(30), null))
					.as("ronda %d", round).isEqualTo(1);
		}
	}

	@Test
	void twoReschedulesToTheSameSlotLeaveOnlyOne() throws Exception {
		Catalog catalog = catalog(30);
		Client ana = newClient();
		Client bea = newClient();
		for (int round = 0; round < 6; round++) {
			LocalDateTime target = THURSDAY.atTime(14, 0).plusMinutes(30L * round);
			Long anas = bookOk(ana.token(), catalog, FRIDAY.atTime(9, 0).plusMinutes(30L * round));
			Long beas = bookOk(bea.token(), catalog, FRIDAY.atTime(14, 0).plusMinutes(30L * round));

			List<String> outcomes = race(List.of(() -> change(ana.token(), anas, "reschedule", to(target)),
					() -> change(bea.token(), beas, "reschedule", to(target))));

			assertThat(outcomes).as("ronda %d", round).containsExactlyInAnyOrder("200", "409 SLOT_NOT_AVAILABLE");
			assertThat(appointments.countOverlapping(catalog.professionalId(), target, target.plusMinutes(30), null))
					.as("ronda %d", round).isEqualTo(1);
		}
	}

	// ---------- utilidades ----------

	private static Map<String, Object> to(LocalDateTime start) {
		return Map.of("startTime", ISO_SECONDS.format(start));
	}

	private void setActive(String admin, String resource, boolean active) throws Exception {
		mvc.perform(withJson(patch(resource + "/active"), admin, Map.of("active", active))).andExpect(status().isOk());
	}

}
