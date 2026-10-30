package com.q.q.integration;

import static com.q.q.entity.enums.AppointmentStatus.CANCELLED;
import static com.q.q.entity.enums.AppointmentStatus.COMPLETED;
import static com.q.q.entity.enums.AppointmentStatus.CONFIRMED;
import static com.q.q.entity.enums.AppointmentStatus.NO_SHOW;
import static com.q.q.entity.enums.AppointmentStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.entity.Appointment;

// confirm, complete, no-show y cancel: permisos (RN-13), transiciones (RN-07), ventana del cliente (RN-08) y
// concurrencia. El reloj está fijo en el lunes 2026-11-02 a las 08:00
@IntegrationTest
class AppointmentStatusIntegrationTest extends AppointmentTestSupport {

	private static final String[] APPOINTMENT_KEYS = { "id", "client", "professional", "service", "startTime", "endTime",
			"status", "price", "notes", "cancelReason", "createdAt" };
	private static final long MISSING_ID = 99_999_999L;

	// ---------- permisos ----------

	@Test
	void confirmCompleteAndNoShowAreOnlyForTheAssignedProfessionalAndAdmins() throws Exception {
		Catalog catalog = catalog(30);
		Catalog other = catalog(30);
		Client owner = newClient();
		Long id = bookOk(owner.token(), catalog, THURSDAY.atTime(10, 0));
		String otherPro = professionalToken(other);

		for (String action : List.of("confirm", "complete", "no-show")) {
			String path = APPOINTMENTS + "/" + id + "/" + action;
			String badPath = APPOINTMENTS + "/abc/" + action;
			// Un CLIENT, aunque sea el dueño, recibe 403 por URL antes que cualquier otra cosa
			change(owner.token(), id, action, null).andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", path));
			change(owner.token(), id, action, "{ no es json").andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", path));
			change(owner.token(), "abc", action, null).andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", badPath));
			change(null, id, action, null)
					.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", path));
			// Otro profesional: 404, igual que una cita que no existe (RN-13)
			change(otherPro, id, action, null).andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
			String missing = APPOINTMENTS + "/" + MISSING_ID + "/" + action;
			change(catalog.admin(), MISSING_ID, action, null)
					.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", missing));
			change(catalog.admin(), "abc", action, null)
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", badPath));
		}
		assertThat(statusOf(id)).isEqualTo(PENDING);
	}

	@Test
	void cancelIsForTheClientTheAssignedProfessionalAndAdmins() throws Exception {
		Catalog catalog = catalog(30);
		Catalog other = catalog(30);
		Client owner = newClient();
		Long byOwner = bookOk(owner.token(), catalog, THURSDAY.atTime(9, 0));
		Long byProfessional = bookOk(owner.token(), catalog, THURSDAY.atTime(9, 30));
		Long byAdmin = bookOk(owner.token(), catalog, THURSDAY.atTime(10, 0));
		String path = APPOINTMENTS + "/" + byOwner + "/cancel";

		// Otra clienta y otro profesional: 404 (RN-13); sin token, 401
		for (String stranger : new String[] { newClient().token(), professionalToken(other) }) {
			change(stranger, byOwner, "cancel", null).andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		}
		change(null, byOwner, "cancel", null).andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", path));
		String missing = APPOINTMENTS + "/" + MISSING_ID + "/cancel";
		change(catalog.admin(), MISSING_ID, "cancel", null)
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", missing));
		assertThat(statusOf(byOwner)).isEqualTo(PENDING);

		String cancelled = body(change(owner.token(), byOwner, "cancel", Map.of("reason", "  No puedo asistir  "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelReason").value("No puedo asistir"))
				.andReturn());
		assertThat(keys(cancelled)).containsExactlyInAnyOrder(APPOINTMENT_KEYS);
		// Sin body y con {}: se cancela sin motivo
		assertThat(body(change(professionalToken(catalog), byProfessional, "cancel", null)
				.andExpect(status().isOk()).andReturn())).contains("\"status\":\"CANCELLED\"", "\"cancelReason\":null");
		assertThat(body(change(catalog.admin(), byAdmin, "cancel", Map.of())
				.andExpect(status().isOk()).andReturn())).contains("\"status\":\"CANCELLED\"", "\"cancelReason\":null");
	}

	@Test
	void theCancelReasonIsTrimmedAndLimitedTo255Characters() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		Long id = bookOk(client.token(), catalog, THURSDAY.atTime(11, 0));
		String path = APPOINTMENTS + "/" + id + "/cancel";

		String tooLong = body(change(client.token(), id, "cancel", Map.of("reason", "x".repeat(256)))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path)).andReturn());
		assertThat(JsonPath.<List<String>>read(tooLong, "$.details[*].field")).containsExactly("reason");
		change(client.token(), id, "cancel", "{ no es json")
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", path));
		assertThat(statusOf(id)).isEqualTo(PENDING);

		// 255 caracteres después de quitar los espacios sobrantes
		change(client.token(), id, "cancel", Map.of("reason", "  " + "y".repeat(255) + "  "))
				.andExpect(status().isOk()).andExpect(jsonPath("$.cancelReason").value("y".repeat(255)));
	}

	// ---------- transiciones (RN-07) ----------

	@Test
	void transitionsFollowTheLifecycleDiagram() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		String pro = professionalToken(catalog);
		String admin = catalog.admin();

		Long future = bookOk(client.token(), catalog, THURSDAY.atTime(10, 0));
		change(pro, future, "confirm", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
		change(pro, future, "confirm", null)
				.andExpectAll(apiError(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", APPOINTMENTS + "/" + future + "/confirm"));
		// CONFIRMED pero todavía no empezó
		for (String action : List.of("complete", "no-show")) {
			change(pro, future, action, null).andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT,
					"APPOINTMENT_NOT_STARTED", APPOINTMENTS + "/" + future + "/" + action));
		}

		// Ya empezadas (insertadas directamente: la API no deja reservar en el pasado)
		Long pendingStarted = insert(catalog, client.id(), MONDAY.atTime(7, 0), 30, PENDING);
		for (String action : List.of("complete", "no-show")) {
			change(pro, pendingStarted, action, null).andExpectAll(apiError(HttpStatus.CONFLICT,
					"INVALID_STATUS_TRANSITION", APPOINTMENTS + "/" + pendingStarted + "/" + action));
		}
		Long startsNow = insert(catalog, client.id(), MONDAY.atTime(8, 0), 30, CONFIRMED);   // ahora == inicio
		change(pro, startsNow, "complete", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
		change(admin, startsNow, "cancel", null)
				.andExpectAll(apiError(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", APPOINTMENTS + "/" + startsNow + "/cancel"));
		Long missed = insert(catalog, client.id(), MONDAY.atTime(7, 30), 30, CONFIRMED);
		change(admin, missed, "no-show", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));
		change(pro, missed, "confirm", null)
				.andExpectAll(apiError(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", APPOINTMENTS + "/" + missed + "/confirm"));
		Long cancelled = bookOk(client.token(), catalog, THURSDAY.atTime(11, 0));
		change(client.token(), cancelled, "cancel", null).andExpect(status().isOk());
		change(client.token(), cancelled, "cancel", null)
				.andExpectAll(apiError(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", APPOINTMENTS + "/" + cancelled + "/cancel"));

		assertThat(List.of(statusOf(future), statusOf(pendingStarted), statusOf(startsNow), statusOf(missed),
				statusOf(cancelled))).containsExactly(CONFIRMED, PENDING, COMPLETED, NO_SHOW, CANCELLED);
	}

	// ---------- ventana del cliente (RN-08) ----------

	@Test
	void theClientCancellationWindowClosesCancelLimitHoursBeforeTheStart() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		// Ahora es lunes 08:00 y el límite son 2 h: la cita de las 10:00 está justo en el límite, la de las 09:30 no
		Long atLimit = bookOk(client.token(), catalog, MONDAY.atTime(10, 0));
		Long tooLate = bookOk(client.token(), catalog, MONDAY.atTime(9, 30));
		Long forAdmin = bookOk(client.token(), catalog, MONDAY.atTime(9, 0));

		change(client.token(), atLimit, "cancel", null).andExpect(status().isOk());
		change(client.token(), tooLate, "cancel", null).andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT,
				"CANCELLATION_WINDOW_CLOSED", APPOINTMENTS + "/" + tooLate + "/cancel"));
		assertThat(statusOf(tooLate)).isEqualTo(PENDING);
		// El profesional asignado y el ADMIN no tienen límite
		change(professionalToken(catalog), tooLate, "cancel", null).andExpect(status().isOk());
		change(catalog.admin(), forAdmin, "cancel", null).andExpect(status().isOk());
	}

	@Test
	void aCancelledAppointmentFreesItsSlot() throws Exception {
		Catalog catalog = catalog(30);
		Client first = newClient();
		Client second = newClient();
		Long id = bookOk(first.token(), catalog, THURSDAY.atTime(11, 30));
		book(second.token(), booking(catalog, THURSDAY.atTime(11, 30)))
				.andExpectAll(apiError(HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE", APPOINTMENTS));

		change(first.token(), id, "cancel", null).andExpect(status().isOk());

		String availability = body(mvc.perform(get(PROFESSIONALS + "/" + catalog.professionalId() + "/availability")
				.param("date", THURSDAY.toString()).param("serviceId", catalog.serviceId().toString()))
				.andExpect(status().isOk()).andReturn());
		assertThat(JsonPath.<List<String>>read(availability, "$.slots[*].start")).contains("2026-11-05T11:30:00");
		bookOk(second.token(), catalog, THURSDAY.atTime(11, 30));
	}

	// ---------- concurrencia ----------

	// La cita bloqueada (SELECT ... FOR UPDATE): la segunda cancelación ve la primera y recibe 409
	@Test
	void twoSimultaneousCancellationsOfTheSameAppointmentGive200And409() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		String pro = professionalToken(catalog);
		for (int round = 0; round < 6; round++) {
			Long id = bookOk(client.token(), catalog, THURSDAY.atTime(9, 0).plusMinutes(30L * round));

			List<String> outcomes = race(List.of(() -> change(client.token(), id, "cancel", Map.of("reason", "cliente")),
					() -> change(pro, id, "cancel", Map.of("reason", "profesional"))));

			assertThat(outcomes).as("ronda %d", round).containsExactlyInAnyOrder("200", "409 INVALID_STATUS_TRANSITION");
			assertThat(statusOf(id)).isEqualTo(CANCELLED);
		}
	}

	// complete y cancel a la vez sobre una CONFIRMED que ya empezó: gana exactamente uno y el otro ve su resultado
	@Test
	void completeAndCancelAtTheSameTimeLeaveOneCoherentFinalState() throws Exception {
		Catalog catalog = catalog(30);
		Client client = newClient();
		String pro = professionalToken(catalog);
		for (int round = 0; round < 6; round++) {
			Long id = insert(catalog, client.id(), MONDAY.atTime(5, 0).plusMinutes(30L * round), 30, CONFIRMED);

			List<String> outcomes = race(List.of(() -> change(pro, id, "complete", null),
					() -> change(catalog.admin(), id, "cancel", Map.of("reason", "carrera"))));

			assertThat(outcomes).as("ronda %d", round).containsExactlyInAnyOrder("200", "409 INVALID_STATUS_TRANSITION");
			Appointment saved = appointments.findById(id).orElseThrow();
			if (outcomes.get(0).equals("200")) {                                // ganó complete
				assertThat(saved.getStatus()).isEqualTo(COMPLETED);
				assertThat(saved.getCancelReason()).isNull();
			} else {                                                            // ganó cancel
				assertThat(saved.getStatus()).isEqualTo(CANCELLED);
				assertThat(saved.getCancelReason()).isEqualTo("carrera");
			}
		}
	}

}
