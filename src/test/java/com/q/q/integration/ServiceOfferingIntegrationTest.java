package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.entity.ServiceOffering;
import com.q.q.repository.ServiceOfferingRepository;

@IntegrationTest
class ServiceOfferingIntegrationTest extends IntegrationTestSupport {

	private static final String SERVICES = "/api/v1/services";
	private static final String[] SERVICE_KEYS = { "id", "name", "description", "durationMinutes", "price", "active", "createdAt" };
	private static final AtomicInteger NAME_SEQUENCE = new AtomicInteger();

	@Autowired
	private ServiceOfferingRepository serviceOfferings;

	// ---------- CRUD con ADMIN ----------

	@Test
	void adminRunsTheWholeLifecycle() throws Exception {
		String admin = adminToken();
		String name = uniqueName("Consulta");

		// Crear: 201 con Location, nombre sin espacios sobrantes y precio siempre con 2 decimales
		MvcResult created = mvc.perform(withJson(post(SERVICES), admin,
						serviceBody("  " + name + "  ", "  Valoración inicial  ", 30, 80000)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value(name))
				.andExpect(jsonPath("$.description").value("Valoración inicial"))
				.andExpect(jsonPath("$.durationMinutes").value(30))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn();
		String createdBody = body(created);
		Long id = JsonPath.<Number>read(createdBody, "$.id").longValue();
		String path = SERVICES + "/" + id;
		assertThat(keys(createdBody)).containsExactlyInAnyOrder(SERVICE_KEYS);
		assertThat(createdBody).contains("\"price\":80000.00");
		assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("http://localhost" + path);

		mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name));

		// PUT reemplaza los 4 campos editables; active e id del body se ignoran
		Map<String, Object> replacement = serviceBody(name + " editado", null, 45, new BigDecimal("95000.50"));
		replacement.put("active", false);
		replacement.put("id", 999_999);
		String updated = body(mvc.perform(withJson(put(path), admin, replacement))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.intValue()))
				.andExpect(jsonPath("$.name").value(name + " editado"))
				.andExpect(jsonPath("$.description").value(nullValue()))
				.andExpect(jsonPath("$.durationMinutes").value(45))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn());
		assertThat(updated).contains("\"price\":95000.50");

		// DELETE: 204 sin cuerpo; desaparece de las lecturas públicas pero la fila sigue (RN-10)
		mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));
		mvc.perform(get(path)).andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		assertThat(names(list(null, "q", name))).isEmpty();
		mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
		assertThat(serviceOfferings.findById(id)).get().extracting(ServiceOffering::isActive).isEqualTo(false);

		mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isNoContent());

		// PATCH /active lo reactiva
		mvc.perform(withJson(patch(path + "/active"), admin, Map.of("active", true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));
		mvc.perform(get(path)).andExpect(status().isOk());
		assertThat(names(list(null, "q", name))).containsExactly(name + " editado");
	}

	// ---------- permisos ----------

	@Test
	void writesRequireAdminEvenWithAnInvalidBody() throws Exception {
		String admin = adminToken();
		String path = SERVICES + "/" + createService(admin, uniqueName("Protegido"));
		String activePath = path + "/active";
		String client = loginToken(registerClient(), PASSWORD);
		String intruderName = uniqueName("Intruso");
		Map<String, Object> valid = validBody(intruderName);

		mvc.perform(withJson(post(SERVICES), null, valid))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", SERVICES));
		mvc.perform(withJson(put(path), null, valid))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", path));
		mvc.perform(delete(path)).andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", path));
		mvc.perform(withJson(patch(activePath), null, Map.of("active", false)))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", activePath));

		// La regla por URL responde 403 antes de que Spring MVC valide el body
		for (Object requestBody : new Object[] { valid, Map.of("active", false), "{}", "{ no es json" }) {
			mvc.perform(withJson(post(SERVICES), client, requestBody))
					.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", SERVICES));
			mvc.perform(withJson(put(path), client, requestBody))
					.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", path));
			mvc.perform(withJson(patch(activePath), client, requestBody))
					.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", activePath));
		}
		mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(client)))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", path));
		mvc.perform(withJson(put(SERVICES + "/abc"), client, "{}"))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", SERVICES + "/abc"));

		assertThat(serviceOfferings.existsByNameIgnoreCase(intruderName)).isFalse();
		mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
	}

	// ---------- nombre único ----------

	@Test
	void duplicateNamesReturn409IgnoringCaseAndSurroundingSpaces() throws Exception {
		String admin = adminToken();
		String name = uniqueName("Corte Clásico");
		Long id = createService(admin, name);

		for (String variant : new String[] { name, name.toLowerCase(Locale.ROOT), name.toUpperCase(Locale.ROOT),
				"   " + name + "  " }) {
			mvc.perform(withJson(post(SERVICES), admin, validBody(variant)))
					.andExpectAll(apiError(HttpStatus.CONFLICT, "SERVICE_NAME_ALREADY_EXISTS", SERVICES));
		}

		// Otro servicio no puede tomar ese nombre; el propio sí puede cambiar sus mayúsculas
		String otherPath = SERVICES + "/" + createService(admin, uniqueName("Otro"));
		mvc.perform(withJson(put(otherPath), admin, validBody(name.toUpperCase(Locale.ROOT))))
				.andExpectAll(apiError(HttpStatus.CONFLICT, "SERVICE_NAME_ALREADY_EXISTS", otherPath));
		mvc.perform(withJson(put(SERVICES + "/" + id), admin, validBody(name.toLowerCase(Locale.ROOT))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value(name.toLowerCase(Locale.ROOT)));

		// Un servicio inactivo sigue ocupando su nombre
		String retired = uniqueName("Retirado");
		createInactiveService(admin, retired);
		mvc.perform(withJson(post(SERVICES), admin, validBody(retired)))
				.andExpectAll(apiError(HttpStatus.CONFLICT, "SERVICE_NAME_ALREADY_EXISTS", SERVICES));
	}

	@Test
	void concurrentCreatesWithTheSameNameGiveOne201AndNo500() throws Exception {
		String admin = adminToken();
		String requestBody = jsonBody(validBody(uniqueName("Carrera")));
		int attempts = 4;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < attempts; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return mvc.perform(withJson(post(SERVICES), admin, requestBody)).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();       // a la vez: varios pasan la comprobación previa y chocan en el índice único
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(60, TimeUnit.SECONDS));
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409);
		} finally {
			pool.shutdownNow();
		}
	}

	// ---------- validación ----------

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("invalidFields")
	void invalidFieldReturns400(String field, Object value) throws Exception {
		Map<String, Object> requestBody = validBody(uniqueName("Invalido"));
		requestBody.put(field, value);

		mvc.perform(withJson(post(SERVICES), adminToken(), requestBody))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", SERVICES))
				.andExpect(jsonPath("$.details.length()").value(1))
				.andExpect(jsonPath("$.details[0].field").value(field));
	}

	static Stream<Arguments> invalidFields() {
		return Stream.of(
				Arguments.of("name", null),
				Arguments.of("name", ""),
				Arguments.of("name", "   "),
				Arguments.of("name", "n".repeat(101)),
				Arguments.of("description", "d".repeat(501)),
				Arguments.of("durationMinutes", null),
				Arguments.of("durationMinutes", 0),
				Arguments.of("durationMinutes", -30),
				Arguments.of("durationMinutes", 481),
				Arguments.of("price", null),
				Arguments.of("price", new BigDecimal("-0.01")),
				Arguments.of("price", new BigDecimal("10.005")),                 // 3 decimales
				Arguments.of("price", new BigDecimal("100000000.00")));          // 9 dígitos enteros: no cabe en DECIMAL(10,2)
	}

	// spring.jackson.deserialization.accept-float-as-int=false: un decimal no se trunca a entero
	@Test
	void decimalDurationIsRejectedInsteadOfTruncated() throws Exception {
		String admin = adminToken();
		String name = uniqueName("Decimal");

		for (String duration : new String[] { "30.5", "30.0" }) {
			mvc.perform(withJson(post(SERVICES), admin, rawServiceJson(name, duration)))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", SERVICES));
		}
		assertThat(serviceOfferings.existsByNameIgnoreCase(name)).isFalse();

		mvc.perform(withJson(post(SERVICES), admin, rawServiceJson(name, "30")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.durationMinutes").value(30));
	}

	@Test
	void boundaryValuesAreAcceptedAndPutUsesTheSameRules() throws Exception {
		String admin = adminToken();
		String longName = padded(uniqueName("Limite"), 100);

		String first = body(mvc.perform(withJson(post(SERVICES), admin,
						serviceBody("  " + longName + "  ", "d".repeat(500), 1, BigDecimal.ZERO)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value(longName))
				.andExpect(jsonPath("$.durationMinutes").value(1))
				.andReturn());
		assertThat(first).contains("\"price\":0.00");

		String second = body(mvc.perform(withJson(post(SERVICES), admin,
						serviceBody(uniqueName("Maximo"), null, 480, new BigDecimal("99999999.99"))))
				.andExpect(status().isCreated())
				.andReturn());
		assertThat(second).contains("\"price\":99999999.99", "\"durationMinutes\":480");

		String path = SERVICES + "/" + JsonPath.<Number>read(second, "$.id");
		mvc.perform(withJson(put(path), admin, serviceBody(uniqueName("Maximo"), null, 481, BigDecimal.ONE)))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path))
				.andExpect(jsonPath("$.details[0].field").value("durationMinutes"));
	}

	// ---------- lecturas ----------

	@Test
	void nonAdminsOnlySeeActiveServices() throws Exception {
		String admin = adminToken();
		String tag = uniqueName("Visibilidad");
		Long activeId = createService(admin, tag + " activo");
		Long inactiveId = createInactiveService(admin, tag + " inactivo");
		String client = loginToken(registerClient(), PASSWORD);

		// Anónimo, CLIENT o token inválido (sigue como anónimo): solo activos, y "active" se ignora
		for (String token : new String[] { null, client, "token-invalido" }) {
			assertThat(names(list(token, "q", tag))).containsExactly(tag + " activo");
			assertThat(names(list(token, "q", tag, "active", "false"))).containsExactly(tag + " activo");
			mvc.perform(authorized(get(SERVICES + "/" + activeId), token)).andExpect(status().isOk());
			mvc.perform(authorized(get(SERVICES + "/" + inactiveId), token))
					.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", SERVICES + "/" + inactiveId));
		}

		// El ADMIN ve todos y puede filtrar
		assertThat(names(list(admin, "q", tag))).containsExactly(tag + " activo", tag + " inactivo");
		assertThat(names(list(admin, "q", tag, "active", "false"))).containsExactly(tag + " inactivo");
		assertThat(names(list(admin, "q", tag, "active", "true"))).containsExactly(tag + " activo");
		mvc.perform(get(SERVICES + "/" + inactiveId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		assertThat(JsonPath.<List<Boolean>>read(list(null, "size", "100"), "$.content[*].active")).containsOnly(true);
	}

	@Test
	void searchMatchesPartOfTheNameIgnoringCaseAndTreatsWildcardsLiterally() throws Exception {
		String admin = adminToken();
		String tag = uniqueName("Busqueda");
		createService(admin, tag + " Masaje Relajante");
		createService(admin, tag + " Descuento 50%");
		createService(admin, tag + " Corte_rapido");
		createService(admin, tag + " Corte normal");

		assertThat(names(list(null, "q", (tag + " masaje").toUpperCase(Locale.ROOT))))
				.containsExactly(tag + " Masaje Relajante");
		assertThat(names(list(null, "q", "  " + tag + " corte  "))).containsExactly(tag + " Corte_rapido", tag + " Corte normal");
		assertThat(names(list(null, "q", tag + " descuento 50%"))).containsExactly(tag + " Descuento 50%");
		assertThat(names(list(null, "q", tag + " Corte_"))).containsExactly(tag + " Corte_rapido");     // "_" no es comodín
		assertThat(names(list(null, "q", tag + "%Masaje"))).isEmpty();                                   // "%" tampoco
	}

	@Test
	void listingIsPagedWithWhitelistedSortAndCappedSize() throws Exception {
		String admin = adminToken();
		String tag = uniqueName("Orden");
		createService(admin, serviceBody(tag + " cara", null, 30, new BigDecimal("30000")));
		createService(admin, serviceBody(tag + " barata", null, 30, new BigDecimal("10000")));
		createService(admin, serviceBody(tag + " media", null, 30, new BigDecimal("20000")));

		String page = list(null, "q", tag);
		assertThat(keys(page)).containsExactlyInAnyOrder(PAGE_KEYS);
		assertThat(JsonPath.<Integer>read(page, "$.size")).isEqualTo(20);
		assertThat(JsonPath.<List<Map<String, Object>>>read(page, "$.content"))
				.allSatisfy(item -> assertThat(item).containsOnlyKeys(SERVICE_KEYS));
		assertThat(names(page)).containsExactly(tag + " cara", tag + " barata", tag + " media");   // por id

		assertThat(names(list(null, "q", tag, "sort", "price,desc"))).containsExactly(tag + " cara", tag + " media", tag + " barata");
		assertThat(names(list(null, "q", tag, "sort", "name"))).containsExactly(tag + " barata", tag + " cara", tag + " media");
		assertThat(JsonPath.<Integer>read(list(null, "size", "1000"), "$.size")).isEqualTo(100);
		assertThat(JsonPath.<List<Object>>read(list(null, "q", tag, "page", "9999"), "$.content")).isEmpty();

		for (String forbidden : new String[] { "description", "active", "updatedAt", "price;drop" }) {
			mvc.perform(get(SERVICES).param("sort", forbidden))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_SORT", SERVICES));
		}
		mvc.perform(get(SERVICES).param("active", "quizas").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", SERVICES));
	}

	// ---------- errores ----------

	@Test
	void unknownServiceReturns404() throws Exception {
		String admin = adminToken();
		String path = SERVICES + "/99999999";

		mvc.perform(get(path)).andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(withJson(put(path), admin, validBody(uniqueName("Fantasma"))))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(withJson(patch(path + "/active"), admin, Map.of("active", true)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path + "/active"));
		mvc.perform(get(SERVICES + "/abc"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", SERVICES + "/abc"));
	}

	@Test
	void changingTheActiveFlagRequiresIt() throws Exception {
		String admin = adminToken();
		String path = SERVICES + "/" + createService(admin, uniqueName("Bandera")) + "/active";

		mvc.perform(withJson(patch(path), admin, "{}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path))
				.andExpect(jsonPath("$.details[0].field").value("active"));
	}

	// ---------- utilidades ----------

	private static String uniqueName(String prefix) {
		return prefix + " " + NAME_SEQUENCE.incrementAndGet() + "-" + System.nanoTime();
	}

	private static String padded(String base, int length) {
		return base + "x".repeat(length - base.length());
	}

	private static Map<String, Object> serviceBody(String name, String description, Object durationMinutes, Object price) {
		Map<String, Object> body = new LinkedHashMap<>();                  // admite null (Map.of no)
		body.put("name", name);
		body.put("description", description);
		body.put("durationMinutes", durationMinutes);
		body.put("price", price);
		return body;
	}

	private static Map<String, Object> validBody(String name) {
		return serviceBody(name, "Servicio de prueba", 30, new BigDecimal("80000.00"));
	}

	// JSON escrito a mano: el número de durationMinutes viaja exactamente como se escribe ("30.0" incluido)
	private static String rawServiceJson(String name, String durationMinutes) {
		return "{\"name\":\"" + name + "\",\"durationMinutes\":" + durationMinutes + ",\"price\":1000}";
	}

	private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
		return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, bearer(token));
	}

	// Un String se envía tal cual (permite JSON inválido); cualquier otro objeto se serializa
	private MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, String token, Object requestBody) {
		return authorized(request, token).contentType(MediaType.APPLICATION_JSON)
				.content(requestBody instanceof String raw ? raw : jsonBody(requestBody));
	}

	private Long createService(String adminToken, String name) throws Exception {
		return createService(adminToken, validBody(name));
	}

	private Long createService(String adminToken, Map<String, Object> requestBody) throws Exception {
		String created = body(mvc.perform(withJson(post(SERVICES), adminToken, requestBody))
				.andExpect(status().isCreated()).andReturn());
		return JsonPath.<Number>read(created, "$.id").longValue();
	}

	private Long createInactiveService(String adminToken, String name) throws Exception {
		Long id = createService(adminToken, name);
		mvc.perform(delete(SERVICES + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
				.andExpect(status().isNoContent());
		return id;
	}

	// GET /services con pares nombre/valor de parámetros; devuelve el cuerpo de una respuesta 200
	private String list(String token, String... params) throws Exception {
		MockHttpServletRequestBuilder request = authorized(get(SERVICES), token);
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return body(mvc.perform(request).andExpect(status().isOk()).andReturn());
	}

	private static List<String> names(String pageBody) {
		return JsonPath.read(pageBody, "$.content[*].name");
	}

}
