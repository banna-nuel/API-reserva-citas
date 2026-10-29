package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;

@IntegrationTest
class WorkingHourIntegrationTest extends IntegrationTestSupport {

	private static final String PROFESSIONALS = "/api/v1/professionals";
	private static final String[] SCHEDULE_KEYS = { "professionalId", "blocks" };
	private static final String[] BLOCK_KEYS = { "dayOfWeek", "startTime", "endTime" };
	private static final List<String> WEEK = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY",
			"SUNDAY");
	private static final List<String> WEEKDAYS = WEEK.subList(0, 5);
	private static final long MISSING_ID = 99_999_999L;

	// ---------- lectura y reemplazo ----------

	@Test
	void adminSavesTheSampleScheduleAndThePublicReadsItSorted() throws Exception {
		String admin = adminToken();
		Long id = createProfessional(admin, uniqueEmail());
		String path = hoursPath(id);

		String empty = schedule(path, null);
		assertThat(keys(empty)).containsExactlyInAnyOrder(SCHEDULE_KEYS);
		assertThat(blocks(empty)).isEmpty();

		// Sección 12.3: lunes a viernes de 09:00 a 12:00 y de 14:00 a 18:00, enviado en desorden
		List<Map<String, Object>> sample = new ArrayList<>();
		for (String day : WEEKDAYS.reversed()) {
			sample.add(block(day, "14:00", "18:00"));
			sample.add(block(day, "09:00", "12:00"));
		}
		List<String> expected = WEEKDAYS.stream()
				.flatMap(day -> Stream.of(day + " 09:00-12:00", day + " 14:00-18:00"))
				.toList();

		String saved = body(mvc.perform(withJson(put(path), admin, Map.of("blocks", sample)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.professionalId").value(id.intValue()))
				.andReturn());
		assertThat(keys(saved)).containsExactlyInAnyOrder(SCHEDULE_KEYS);
		assertThat(blocks(saved)).containsExactlyElementsOf(expected);

		String read = schedule(path, null);
		assertThat(blocks(read)).containsExactlyElementsOf(expected);
		assertThat(JsonPath.<List<Map<String, Object>>>read(read, "$.blocks"))
				.allSatisfy(block -> assertThat(block).containsOnlyKeys(BLOCK_KEYS));
	}

	@Test
	void anEmptyListDeletesTheSchedule() throws Exception {
		String admin = adminToken();
		String path = hoursPath(createProfessional(admin, uniqueEmail()));
		replace(path, admin, block("MONDAY", "09:00", "12:00"), block("FRIDAY", "15:00", "19:00"));

		mvc.perform(withJson(put(path), admin, Map.of("blocks", List.of())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocks").isEmpty());
		assertThat(blocks(schedule(path, null))).isEmpty();
	}

	@Test
	void contiguousBlocksAndTheSameHoursOnOtherDaysAreValid() throws Exception {
		String admin = adminToken();
		String path = hoursPath(createProfessional(admin, uniqueEmail()));

		replace(path, admin, block("MONDAY", "12:00", "14:00"), block("MONDAY", "09:00", "12:00"),
				block("TUESDAY", "09:00", "12:00"), block("SUNDAY", "00:00", "23:59"));
		assertThat(blocks(schedule(path, null))).containsExactly("MONDAY 09:00-12:00", "MONDAY 12:00-14:00",
				"TUESDAY 09:00-12:00", "SUNDAY 00:00-23:59");
	}

	// ---------- permisos ----------

	@Test
	void theOwnerEditsOnlyItsOwnSchedule() throws Exception {
		String admin = adminToken();
		String ownerEmail = uniqueEmail();
		String ownPath = hoursPath(createProfessional(admin, ownerEmail));
		String otherPath = hoursPath(createProfessional(admin, uniqueEmail()));
		String owner = loginToken(ownerEmail, PASSWORD);
		Map<String, Object> mondayMorning = Map.of("blocks", List.of(block("MONDAY", "08:00", "12:00")));

		mvc.perform(withJson(put(ownPath), owner, mondayMorning))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocks[0].startTime").value("08:00"));
		assertThat(blocks(schedule(ownPath, null))).containsExactly("MONDAY 08:00-12:00");

		// El horario de otro profesional: 403 (es público, no se oculta con 404) y no cambia
		mvc.perform(withJson(put(otherPath), owner, mondayMorning))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", otherPath));
		assertThat(blocks(schedule(otherPath, null))).isEmpty();

		// El ADMIN edita el de cualquiera
		mvc.perform(withJson(put(otherPath), admin, mondayMorning)).andExpect(status().isOk());
	}

	@Test
	void clientsGet403AndAnonymousGet401EvenWithAnInvalidBody() throws Exception {
		String admin = adminToken();
		String path = hoursPath(createProfessional(admin, uniqueEmail()));
		String client = loginToken(registerClient(), PASSWORD);
		Map<String, Object> valid = Map.of("blocks", List.of(block("MONDAY", "09:00", "12:00")));
		Map<String, Object> invalid = Map.of("blocks", List.of(block("FUNDAY", "9:00", "08:00")));

		for (Object requestBody : new Object[] { valid, invalid, "{}", "{ no es json" }) {
			mvc.perform(withJson(put(path), null, requestBody))
					.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", path));
			// La regla por URL responde 403 antes de que Spring MVC valide el body
			mvc.perform(withJson(put(path), client, requestBody))
					.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", path));
		}
		String badId = PROFESSIONALS + "/abc/working-hours";
		mvc.perform(withJson(put(badId), client, "{}")).andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", badId));
		assertThat(blocks(schedule(path, null))).isEmpty();
	}

	@Test
	void anOwnerWithAnInactiveProfileCannotEditButTheAdminCan() throws Exception {
		String admin = adminToken();
		String ownerEmail = uniqueEmail();
		Long id = createProfessional(admin, ownerEmail);
		String path = hoursPath(id);
		String owner = loginToken(ownerEmail, PASSWORD);
		Map<String, Object> mondayMorning = Map.of("blocks", List.of(block("MONDAY", "08:00", "12:00")));

		mvc.perform(withJson(patch(PROFESSIONALS + "/" + id + "/active"), admin, Map.of("active", false)))
				.andExpect(status().isOk());

		mvc.perform(withJson(put(path), owner, mondayMorning))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", path));
		mvc.perform(withJson(put(path), admin, mondayMorning)).andExpect(status().isOk());
		assertThat(blocks(schedule(path, admin))).containsExactly("MONDAY 08:00-12:00");
	}

	@Test
	void unknownProfessionalReturns404() throws Exception {
		String admin = adminToken();
		String path = hoursPath(MISSING_ID);
		String professionalEmail = uniqueEmail();
		createProfessional(admin, professionalEmail);
		String professional = loginToken(professionalEmail, PASSWORD);
		Map<String, Object> valid = Map.of("blocks", List.of(block("MONDAY", "09:00", "12:00")));

		mvc.perform(get(path)).andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(withJson(put(path), admin, valid)).andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(withJson(put(path), professional, valid))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(get(PROFESSIONALS + "/abc/working-hours"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", PROFESSIONALS + "/abc/working-hours"));
	}

	// ---------- visibilidad ----------

	@Test
	void theScheduleIsHiddenWhileTheProfileOrTheUserIsInactive() throws Exception {
		String admin = adminToken();
		String email = uniqueEmail();
		Long id = createProfessional(admin, email);
		String path = hoursPath(id);
		String client = loginToken(registerClient(), PASSWORD);
		replace(path, admin, block("MONDAY", "09:00", "12:00"));
		String profileActive = PROFESSIONALS + "/" + id + "/active";
		String userActive = "/api/v1/users/" + idOf(email) + "/active";

		for (String toggle : new String[] { profileActive, userActive }) {
			mvc.perform(withJson(patch(toggle), admin, Map.of("active", false))).andExpect(status().isOk());
			for (String token : new String[] { null, client }) {
				mvc.perform(authorized(get(path), token))
						.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
			}
			assertThat(blocks(schedule(path, admin))).containsExactly("MONDAY 09:00-12:00");

			mvc.perform(withJson(patch(toggle), admin, Map.of("active", true))).andExpect(status().isOk());
			for (String token : new String[] { null, client }) {
				assertThat(blocks(schedule(path, token))).containsExactly("MONDAY 09:00-12:00");
			}
		}
	}

	// ---------- validación ----------

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("invalidBlocks")
	void invalidBlockReturns400AndKeepsThePreviousSchedule(String description, Map<String, Object> invalid,
			String field) throws Exception {
		String admin = adminToken();
		String path = hoursPath(createProfessional(admin, uniqueEmail()));
		replace(path, admin, block("FRIDAY", "08:00", "10:00"));

		mvc.perform(withJson(put(path), admin, Map.of("blocks", List.of(block("MONDAY", "09:00", "10:00"), invalid))))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path))
				.andExpect(jsonPath("$.details[*].field").value(everyItem(is("blocks[1]." + field))));
		assertThat(blocks(schedule(path, null))).containsExactly("FRIDAY 08:00-10:00");
	}

	static Stream<Arguments> invalidBlocks() {
		return Stream.of(
				Arguments.of("día en minúsculas", block("monday", "09:00", "10:00"), "dayOfWeek"),
				Arguments.of("día inexistente", block("FUNDAY", "09:00", "10:00"), "dayOfWeek"),
				Arguments.of("sin día", block(null, "09:00", "10:00"), "dayOfWeek"),
				Arguments.of("hora de un dígito", block("TUESDAY", "9:00", "10:00"), "startTime"),
				Arguments.of("con segundos", block("TUESDAY", "09:00:30", "10:00"), "startTime"),
				Arguments.of("sin hora de inicio", block("TUESDAY", null, "10:00"), "startTime"),
				Arguments.of("hora 25", block("TUESDAY", "09:00", "25:00"), "endTime"),
				Arguments.of("hora 24", block("TUESDAY", "09:00", "24:00"), "endTime"),
				Arguments.of("fin igual al inicio", block("TUESDAY", "09:00", "09:00"), "endTime"),
				Arguments.of("fin antes del inicio", block("TUESDAY", "18:00", "09:00"), "endTime"));
	}

	@Test
	void listRulesReturn400() throws Exception {
		String admin = adminToken();
		String path = hoursPath(createProfessional(admin, uniqueEmail()));
		List<Map<String, Object>> fullWeek = new ArrayList<>();             // 5 bloques en cada día: 35
		for (String day : WEEK) {
			for (int hour = 8; hour <= 16; hour += 2) {
				fullWeek.add(block(day, "%02d:00".formatted(hour), "%02d:00".formatted(hour + 1)));
			}
		}
		List<Map<String, Object>> sixOnMonday = new ArrayList<>();
		for (int hour = 8; hour <= 18; hour += 2) {
			sixOnMonday.add(block("MONDAY", "%02d:00".formatted(hour), "%02d:00".formatted(hour + 1)));
		}

		// 35 bloques (5 por día) es el máximo permitido
		mvc.perform(withJson(put(path), admin, Map.of("blocks", fullWeek)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocks.length()").value(35));
		List<Map<String, Object>> thirtySix = new ArrayList<>(fullWeek);
		thirtySix.add(block("SUNDAY", "20:00", "21:00"));

		for (Object requestBody : new Object[] { "{}", Map.of("blocks", sixOnMonday), Map.of("blocks", thirtySix) }) {
			mvc.perform(withJson(put(path), admin, requestBody))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path))
					.andExpect(jsonPath("$.details[*].field").value(everyItem(is("blocks"))));
		}
		mvc.perform(withJson(put(path), admin, Map.of("blocks", Arrays.asList(block("MONDAY", "09:00", "10:00"), null))))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path))
				.andExpect(jsonPath("$.details[0].field").value("blocks[1]"));
		mvc.perform(withJson(put(path), admin, "{\"blocks\":\"lunes\"}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", path));
		assertThat(blocks(schedule(path, null))).hasSize(35);
	}

	@Test
	void overlapsReturn422WithTheConflictsAndKeepThePreviousSchedule() throws Exception {
		String admin = adminToken();
		String path = hoursPath(createProfessional(admin, uniqueEmail()));
		replace(path, admin, block("FRIDAY", "08:00", "10:00"));

		mvc.perform(withJson(put(path), admin, Map.of("blocks", List.of(
						block("MONDAY", "09:00", "12:00"), block("MONDAY", "11:00", "13:00"),
						block("TUESDAY", "10:00", "11:00"), block("TUESDAY", "10:00", "11:00"),       // repetido
						block("WEDNESDAY", "08:00", "17:00"), block("WEDNESDAY", "09:00", "10:00"),
						block("THURSDAY", "09:00", "12:00"), block("THURSDAY", "12:00", "14:00")))))  // contiguos: valen
				.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "WORKING_HOURS_OVERLAP", path))
				.andExpect(jsonPath("$.details.length()").value(3))
				.andExpect(jsonPath("$.details[*].field").value(everyItem(is("blocks"))))
				.andExpect(jsonPath("$.details[0].message").value("MONDAY: 09:00-12:00 se solapa con 11:00-13:00"))
				.andExpect(jsonPath("$.details[1].message").value("TUESDAY: 10:00-11:00 se solapa con 10:00-11:00"))
				.andExpect(jsonPath("$.details[2].message").value("WEDNESDAY: 08:00-17:00 se solapa con 09:00-10:00"));

		assertThat(blocks(schedule(path, null))).containsExactly("FRIDAY 08:00-10:00");
	}

	// ---------- concurrencia ----------

	@Test
	void concurrentReplacementsLeaveExactlyOneOfTheSchedules() throws Exception {
		String admin = adminToken();
		String path = hoursPath(createProfessional(admin, uniqueEmail()));
		List<Map<String, Object>> mornings = new ArrayList<>();
		for (String day : WEEKDAYS) {
			mornings.add(block(day, "08:00", "10:00"));
			mornings.add(block(day, "11:00", "13:00"));
		}
		List<Map<String, Object>> afternoons = new ArrayList<>();
		for (String day : WEEK.subList(0, 6)) {
			afternoons.add(block(day, "14:00", "16:00"));
		}
		String morningsBody = jsonBody(Map.of("blocks", mornings));
		String afternoonsBody = jsonBody(Map.of("blocks", afternoons));
		List<List<String>> eitherSchedule = List.of(sorted(blocks(morningsBody)), sorted(blocks(afternoonsBody)));

		int threads = 6;                     // la mitad envía cada horario: más choques que con solo dos peticiones
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			for (int round = 0; round < 10; round++) {
				// Cada ronda parte de un horario vacío: sin el bloqueo, los DELETE no encuentran filas que bloquear
				// y las inserciones de ambas peticiones pueden cruzarse
				replace(path, admin);
				CountDownLatch start = new CountDownLatch(1);
				List<Future<Integer>> results = new ArrayList<>();
				for (int i = 0; i < threads; i++) {
					String requestBody = i % 2 == 0 ? morningsBody : afternoonsBody;
					results.add(pool.submit(() -> {
						start.await();
						return mvc.perform(withJson(put(path), admin, requestBody)).andReturn().getResponse().getStatus();
					}));
				}
				start.countDown();           // a la vez: el bloqueo de la fila del profesional los aplica en serie
				for (Future<Integer> result : results) {
					assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(200);
				}
				// Exactamente uno de los dos horarios, sin mezclas ni duplicados
				assertThat(eitherSchedule).contains(blocks(schedule(path, null)));
			}
		} finally {
			pool.shutdownNow();
		}
	}

	// ---------- utilidades ----------

	private static String hoursPath(Long professionalId) {
		return PROFESSIONALS + "/" + professionalId + "/working-hours";
	}

	private static Map<String, Object> block(String day, String start, String end) {
		Map<String, Object> block = new LinkedHashMap<>();                  // admite null (Map.of no)
		block.put("dayOfWeek", day);
		block.put("startTime", start);
		block.put("endTime", end);
		return block;
	}

	private Long createProfessional(String adminToken, String email) throws Exception {
		Map<String, Object> requestBody = Map.of("fullName", "Profesional con horario", "email", email,
				"password", PASSWORD, "specialty", "Fisioterapia");
		String created = body(mvc.perform(withJson(post(PROFESSIONALS), adminToken, requestBody))
				.andExpect(status().isCreated()).andReturn());
		return JsonPath.<Number>read(created, "$.id").longValue();
	}

	@SafeVarargs
	private void replace(String path, String token, Map<String, Object>... blocks) throws Exception {
		mvc.perform(withJson(put(path), token, Map.of("blocks", List.of(blocks)))).andExpect(status().isOk());
	}

	// GET del horario; devuelve el cuerpo de una respuesta 200
	private String schedule(String path, String token) throws Exception {
		return body(mvc.perform(authorized(get(path), token)).andExpect(status().isOk()).andReturn());
	}

	// Cada bloque como "MONDAY 09:00-12:00", en el orden en que vienen
	private static List<String> blocks(String scheduleBody) {
		return JsonPath.<List<Map<String, Object>>>read(scheduleBody, "$.blocks").stream()
				.map(b -> b.get("dayOfWeek") + " " + b.get("startTime") + "-" + b.get("endTime"))
				.toList();
	}

	// El orden del GET: de lunes a domingo y por hora de inicio
	private static List<String> sorted(List<String> blocks) {
		return blocks.stream()
				.sorted((a, b) -> {
					int byDay = Integer.compare(WEEK.indexOf(a.split(" ")[0]), WEEK.indexOf(b.split(" ")[0]));
					return byDay != 0 ? byDay : a.compareTo(b);
				})
				.toList();
	}

}
