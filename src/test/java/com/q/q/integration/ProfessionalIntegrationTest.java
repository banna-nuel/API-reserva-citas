package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.repository.ProfessionalRepository;

import jakarta.persistence.EntityManagerFactory;

@IntegrationTest
class ProfessionalIntegrationTest extends IntegrationTestSupport {

	private static final String PROFESSIONALS = "/api/v1/professionals";
	private static final String SERVICES = "/api/v1/services";
	private static final String[] PUBLIC_KEYS = { "id", "fullName", "specialty", "bio", "services" };
	private static final String[] ADMIN_KEYS = { "id", "userId", "fullName", "email", "phone", "specialty", "bio",
			"active", "userActive", "services", "createdAt" };
	private static final String[] PUBLIC_SERVICE_KEYS = { "id", "name", "durationMinutes", "price" };
	private static final String[] ADMIN_SERVICE_KEYS = { "id", "name", "durationMinutes", "price", "active" };
	private static final long MISSING_ID = 99_999_999L;
	private static final AtomicInteger NAME_SEQUENCE = new AtomicInteger();

	@Autowired
	private ProfessionalRepository professionals;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	// ---------- alta ----------

	@Test
	void adminCreatesAProfessionalWhoCanLogIn() throws Exception {
		String admin = adminToken();
		Long consulta = createService(admin, uniqueName("Consulta"));
		Long masaje = createService(admin, uniqueName("Masaje"));
		String email = uniqueEmail();
		Map<String, Object> request = professionalBody("  Carlos Ruiz  ", email.toUpperCase(Locale.ROOT), "  Fisioterapia  ",
				List.of(masaje, consulta, masaje));                       // repetido y desordenado
		request.put("phone", "3001112233");
		request.put("bio", "  Diez años de experiencia  ");
		request.put("role", "ADMIN");                                    // se ignoran
		request.put("active", false);
		request.put("userId", 1);

		MvcResult created = mvc.perform(withJson(post(PROFESSIONALS), admin, request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fullName").value("Carlos Ruiz"))
				.andExpect(jsonPath("$.email").value(email))                // en minúsculas (RN-11)
				.andExpect(jsonPath("$.phone").value("3001112233"))
				.andExpect(jsonPath("$.specialty").value("Fisioterapia"))
				.andExpect(jsonPath("$.bio").value("Diez años de experiencia"))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn();
		String createdBody = body(created);
		Long id = JsonPath.<Number>read(createdBody, "$.id").longValue();
		Long userId = JsonPath.<Number>read(createdBody, "$.userId").longValue();
		assertThat(keys(createdBody)).containsExactlyInAnyOrder(ADMIN_KEYS);
		assertThat(createdBody).doesNotContain(PASSWORD, "password", "$2a$");
		assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION))
				.isEqualTo("http://localhost" + PROFESSIONALS + "/" + id);
		assertThat(serviceIds(createdBody)).containsExactly(consulta, masaje);
		assertThat(JsonPath.<List<Map<String, Object>>>read(createdBody, "$.services"))
				.allSatisfy(service -> assertThat(service).containsOnlyKeys(ADMIN_SERVICE_KEYS));

		User user = users.findById(userId).orElseThrow();
		assertThat(user.getEmail()).isEqualTo(email);
		assertThat(user.getRole()).isEqualTo(Role.PROFESSIONAL);
		assertThat(user.isActive()).isTrue();

		// Inicia sesión como cualquier usuario y su perfil dice PROFESSIONAL
		String token = loginToken(email.toUpperCase(Locale.ROOT), PASSWORD);
		mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId.intValue()))
				.andExpect(jsonPath("$.role").value("PROFESSIONAL"));
	}

	@Test
	void boundaryValuesAreAccepted() throws Exception {
		String admin = adminToken();
		String specialty = "s".repeat(100);
		Map<String, Object> request = professionalBody("Límites", uniqueEmail(), "  " + specialty + "  ", null);
		request.put("password", "p".repeat(72));
		request.put("bio", "b".repeat(500));
		request.remove("serviceIds");                                    // opcional

		mvc.perform(withJson(post(PROFESSIONALS), admin, request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.specialty").value(specialty))
				.andExpect(jsonPath("$.services").isEmpty());

		Map<String, Object> shortest = professionalBody("Mínimos", uniqueEmail(), "x", List.of());
		shortest.put("password", "p".repeat(8));
		shortest.put("bio", "   ");
		mvc.perform(withJson(post(PROFESSIONALS), admin, shortest))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bio").value(nullValue()));
	}

	// ---------- permisos ----------

	@Test
	void writesRequireAdminEvenWithAnInvalidBody() throws Exception {
		String admin = adminToken();
		Long id = createProfessional(admin, uniqueEmail(), List.of());
		String path = PROFESSIONALS + "/" + id;
		String client = loginToken(registerClient(), PASSWORD);
		String professionalEmail = uniqueEmail();
		createProfessional(admin, professionalEmail, List.of());
		String professional = loginToken(professionalEmail, PASSWORD);
		String intruderEmail = uniqueEmail();
		Map<String, Object> valid = professionalBody("Intruso", intruderEmail, "Ninguna", List.of());

		List<Write> writes = List.of(new Write(HttpMethod.POST, PROFESSIONALS), new Write(HttpMethod.PUT, path),
				new Write(HttpMethod.PATCH, path + "/active"), new Write(HttpMethod.PUT, path + "/services"));
		for (Write write : writes) {
			for (Object requestBody : new Object[] { valid, Map.of("active", false), "{}", "{ no es json" }) {
				mvc.perform(withJson(request(write.method(), write.path()), null, requestBody))
						.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", write.path()));
				// La regla por URL responde 403 antes de que Spring MVC valide el body
				for (String token : new String[] { client, professional }) {
					mvc.perform(withJson(request(write.method(), write.path()), token, requestBody))
							.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", write.path()));
				}
			}
		}
		mvc.perform(withJson(put(PROFESSIONALS + "/abc"), client, "{}"))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", PROFESSIONALS + "/abc"));

		assertThat(users.existsByEmail(intruderEmail)).isFalse();
		mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.specialty").value("Especialidad de prueba"));
	}

	// ---------- email único ----------

	@Test
	void emailAlreadyUsedReturns409IgnoringCase() throws Exception {
		String admin = adminToken();
		String clientEmail = registerClient();
		String professionalEmail = uniqueEmail();
		createProfessional(admin, professionalEmail, List.of());

		for (String taken : new String[] { clientEmail.toUpperCase(Locale.ROOT), capitalize(professionalEmail),
				IntegrationTest.ADMIN_EMAIL.toLowerCase(Locale.ROOT) }) {
			mvc.perform(withJson(post(PROFESSIONALS), admin, professionalBody("Repetido", taken, "Repetida", List.of())))
					.andExpectAll(apiError(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", PROFESSIONALS));
		}
		assertThat(users.findByEmail(clientEmail).orElseThrow().getRole()).isEqualTo(Role.CLIENT);
	}

	@Test
	void concurrentCreatesWithTheSameEmailGiveOne201AndNo500() throws Exception {
		String admin = adminToken();
		String email = uniqueEmail();
		String requestBody = jsonBody(professionalBody("Carrera", email, "Carreras", List.of()));
		long professionalsBefore = professionals.count();
		int attempts = 4;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < attempts; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return mvc.perform(withJson(post(PROFESSIONALS), admin, requestBody)).andReturn().getResponse().getStatus();
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
		assertThat(professionals.count()).isEqualTo(professionalsBefore + 1);
	}

	// ---------- validación ----------

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("invalidFields")
	void invalidFieldReturns400AndCreatesNothing(String field, Object value) throws Exception {
		String email = uniqueEmail();
		Map<String, Object> requestBody = professionalBody("Válido", email, "Válida", List.of());
		requestBody.put(field, value);

		mvc.perform(withJson(post(PROFESSIONALS), adminToken(), requestBody))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", PROFESSIONALS))
				.andExpect(jsonPath("$.details").isNotEmpty())
				.andExpect(jsonPath("$.details[*].field").value(everyItem(startsWith(field))));
		assertThat(users.existsByEmail(email)).isFalse();
	}

	static Stream<Arguments> invalidFields() {
		return Stream.of(
				Arguments.of("password", "p".repeat(7)),
				Arguments.of("password", "p".repeat(73)),
				Arguments.of("password", "ñ".repeat(40)),                          // 40 caracteres, pero 80 bytes (RN-11)
				Arguments.of("password", null),
				Arguments.of("specialty", ""),
				Arguments.of("specialty", "   "),
				Arguments.of("specialty", null),
				Arguments.of("specialty", "s".repeat(101)),
				Arguments.of("bio", "b".repeat(501)),
				Arguments.of("fullName", "  "),
				Arguments.of("email", "no-es-un-email"),
				Arguments.of("phone", "3".repeat(31)),
				Arguments.of("serviceIds", Arrays.asList(1L, null)));
	}

	@Test
	void invalidServiceIdsReturn422AndLeaveNothingBehind() throws Exception {
		String admin = adminToken();
		Long active = createService(admin, uniqueName("Vigente"));
		Long inactive = createInactiveService(admin, uniqueName("Retirado"));
		String email = uniqueEmail();
		long professionalsBefore = professionals.count();

		mvc.perform(withJson(post(PROFESSIONALS), admin,
						professionalBody("Sin servicio", email, "Ninguna", List.of(MISSING_ID, active, inactive, MISSING_ID))))
				.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_SERVICE_IDS", PROFESSIONALS))
				.andExpect(jsonPath("$.details.length()").value(2))               // un elemento por id inválido, sin repetir
				.andExpect(jsonPath("$.details[*].field").value(everyItem(is("serviceIds"))))
				.andExpect(jsonPath("$.details[0].message").value("El servicio " + inactive + " no existe o está inactivo"))
				.andExpect(jsonPath("$.details[1].message").value("El servicio " + MISSING_ID + " no existe o está inactivo"));

		// Ni usuario ni perfil: el email queda libre para el siguiente intento
		assertThat(users.existsByEmail(email)).isFalse();
		assertThat(professionals.count()).isEqualTo(professionalsBefore);
		mvc.perform(withJson(post(PROFESSIONALS), admin, professionalBody("Sin servicio", email, "Ninguna", List.of(active))))
				.andExpect(status().isCreated());
	}

	// ---------- lecturas ----------

	@Test
	void publicViewHidesContactDataAndInactiveServices() throws Exception {
		String admin = adminToken();
		Long kept = createService(admin, uniqueName("Se queda"));
		Long retired = createService(admin, uniqueName("Se retira"));
		String email = uniqueEmail();
		Map<String, Object> request = professionalBody("Laura Gómez", email, "Odontología", List.of(retired, kept));
		request.put("phone", "3209998877");
		Long id = createProfessional(admin, request);
		String path = PROFESSIONALS + "/" + id;
		String client = loginToken(registerClient(), PASSWORD);
		String itself = loginToken(email, PASSWORD);
		deactivateService(admin, retired);                               // sigue asignado, pero ya no es público

		// Anónimo, CLIENT, el propio PROFESSIONAL o un token inválido (sigue como anónimo): vista pública
		for (String token : new String[] { null, client, itself, "token-invalido" }) {
			String view = view(path, token);
			assertThat(keys(view)).containsExactlyInAnyOrder(PUBLIC_KEYS);
			assertThat(view).doesNotContain(email, "3209998877", "userId", "createdAt");
			assertThat(serviceIds(view)).containsExactly(kept);
			assertThat(JsonPath.<List<Map<String, Object>>>read(view, "$.services"))
					.allSatisfy(service -> assertThat(service).containsOnlyKeys(PUBLIC_SERVICE_KEYS));

			String page = list(token, "serviceId", String.valueOf(kept));
			assertThat(listedIds(page)).containsExactly(id);
			assertThat(JsonPath.<Map<String, Object>>read(page, "$.content[0]")).containsOnlyKeys(PUBLIC_KEYS);
			assertThat(page).doesNotContain(email, "3209998877");
		}

		// El ADMIN ve los datos de contacto y todos los servicios asignados, cada uno con su estado
		String adminView = view(path, admin);
		assertThat(keys(adminView)).containsExactlyInAnyOrder(ADMIN_KEYS);
		assertThat(JsonPath.<String>read(adminView, "$.email")).isEqualTo(email);
		assertThat(JsonPath.<String>read(adminView, "$.phone")).isEqualTo("3209998877");
		assertThat(JsonPath.<Number>read(adminView, "$.userId").longValue()).isEqualTo(idOf(email));
		assertThat(serviceIds(adminView)).containsExactly(kept, retired);
		assertThat(JsonPath.<List<Boolean>>read(adminView, "$.services[*].active")).containsExactly(true, false);
		assertThat(JsonPath.<Map<String, Object>>read(list(admin, "serviceId", String.valueOf(kept)), "$.content[0]"))
				.containsOnlyKeys(ADMIN_KEYS);
	}

	@Test
	void inactiveProfessionalIsHiddenFromNonAdminsButKeepsItsLogin() throws Exception {
		String admin = adminToken();
		String service = String.valueOf(createService(admin, uniqueName("Exclusivo")));
		String email = uniqueEmail();
		Long id = createProfessional(admin, email, List.of(Long.valueOf(service)));
		String path = PROFESSIONALS + "/" + id;
		String client = loginToken(registerClient(), PASSWORD);

		mvc.perform(withJson(patch(path + "/active"), admin, Map.of("active", false)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.intValue()))
				.andExpect(jsonPath("$.active").value(false));

		for (String token : new String[] { null, client }) {
			mvc.perform(authorized(get(path), token)).andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
			assertThat(listedIds(list(token, "serviceId", service))).isEmpty();
			assertThat(listedIds(list(token, "serviceId", service, "active", "false"))).isEmpty();   // se ignora
		}
		mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
		assertThat(listedIds(list(admin, "serviceId", service))).containsExactly(id);
		assertThat(listedIds(list(admin, "serviceId", service, "active", "false"))).containsExactly(id);
		assertThat(listedIds(list(admin, "serviceId", service, "active", "true"))).isEmpty();

		// Desactivar el perfil no desactiva su usuario: sigue pudiendo iniciar sesión
		assertThat(users.findByEmail(email).orElseThrow().isActive()).isTrue();
		loginToken(email, PASSWORD);

		mvc.perform(withJson(patch(path + "/active"), admin, Map.of("active", true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));
		mvc.perform(get(path)).andExpect(status().isOk());
		mvc.perform(withJson(patch(path + "/active"), admin, "{}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path + "/active"))
				.andExpect(jsonPath("$.details[0].field").value("active"));
	}

	@Test
	void deactivatingTheUserHidesTheProfessionalUntilItIsReactivated() throws Exception {
		String admin = adminToken();
		String service = String.valueOf(createService(admin, uniqueName("Usuario inactivo")));
		String email = uniqueEmail();
		Long id = createProfessional(admin, email, List.of(Long.valueOf(service)));
		String path = PROFESSIONALS + "/" + id;
		String userActivePath = "/api/v1/users/" + idOf(email) + "/active";
		String client = loginToken(registerClient(), PASSWORD);

		mvc.perform(withJson(patch(userActivePath), admin, Map.of("active", false))).andExpect(status().isOk());

		// Su perfil sigue activo, pero sin usuario activo no existe para el público
		for (String token : new String[] { null, client }) {
			mvc.perform(authorized(get(path), token)).andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
			assertThat(listedIds(list(token, "serviceId", service))).isEmpty();
		}
		mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.userActive").value(false));
		String adminPage = list(admin, "serviceId", service);
		assertThat(listedIds(adminPage)).containsExactly(id);
		assertThat(JsonPath.<List<Boolean>>read(adminPage, "$.content[*].userActive")).containsExactly(false);

		mvc.perform(withJson(patch(userActivePath), admin, Map.of("active", true))).andExpect(status().isOk());
		for (String token : new String[] { null, client }) {
			mvc.perform(authorized(get(path), token)).andExpect(status().isOk());
			assertThat(listedIds(list(token, "serviceId", service))).containsExactly(id);
		}
	}

	@Test
	void listingFiltersByServiceWithPagingAndWhitelistedSort() throws Exception {
		String admin = adminToken();
		Long serviceId = createService(admin, uniqueName("Filtro"));
		Long other = createService(admin, uniqueName("Otro"));
		String service = String.valueOf(serviceId);
		Long beatriz = createProfessional(admin, professionalBody("Beatriz", uniqueEmail(), "Cardiología", List.of(serviceId)));
		Long andres = createProfessional(admin, professionalBody("Andrés", uniqueEmail(), "Pediatría", List.of(serviceId, other)));
		Long carmen = createProfessional(admin, professionalBody("Carmen", uniqueEmail(), "Dermatología", List.of(serviceId)));
		createProfessional(admin, professionalBody("Diego", uniqueEmail(), "Neurología", List.of(other)));

		String page = list(null, "serviceId", service);
		assertThat(keys(page)).containsExactlyInAnyOrder(PAGE_KEYS);
		assertThat(JsonPath.<Integer>read(page, "$.size")).isEqualTo(20);
		assertThat(JsonPath.<Number>read(page, "$.totalElements").longValue()).isEqualTo(3);
		assertThat(listedIds(page)).containsExactly(beatriz, andres, carmen);                  // por id
		assertThat(JsonPath.<List<Map<String, Object>>>read(page, "$.content"))
				.allSatisfy(item -> assertThat(item).containsOnlyKeys(PUBLIC_KEYS));

		assertThat(listedIds(list(null, "serviceId", service, "sort", "fullName"))).containsExactly(andres, beatriz, carmen);
		assertThat(listedIds(list(null, "serviceId", service, "sort", "specialty,desc"))).containsExactly(andres, carmen, beatriz);
		String second = list(null, "serviceId", service, "size", "2", "page", "1");
		assertThat(listedIds(second)).containsExactly(carmen);
		assertThat(JsonPath.<Integer>read(second, "$.totalPages")).isEqualTo(2);
		assertThat(JsonPath.<Integer>read(list(null, "size", "1000"), "$.size")).isEqualTo(100);
		assertThat(listedIds(list(null, "serviceId", service, "page", "9999"))).isEmpty();

		// Un servicio que no existe da una lista vacía; uno inactivo, también (salvo para el ADMIN)
		String none = list(null, "serviceId", String.valueOf(MISSING_ID));
		assertThat(listedIds(none)).isEmpty();
		assertThat(JsonPath.<Number>read(none, "$.totalElements").longValue()).isZero();
		deactivateService(admin, serviceId);
		assertThat(listedIds(list(null, "serviceId", service))).isEmpty();
		assertThat(listedIds(list(admin, "serviceId", service))).containsExactly(beatriz, andres, carmen);

		for (String forbidden : new String[] { "email", "phone", "userId", "user.fullName", "bio", "active", "price;drop" }) {
			mvc.perform(get(PROFESSIONALS).param("sort", forbidden))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_SORT", PROFESSIONALS));
		}
		mvc.perform(get(PROFESSIONALS).param("serviceId", "abc"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", PROFESSIONALS));
		mvc.perform(get(PROFESSIONALS).param("active", "quizas").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", PROFESSIONALS));
	}

	// Sin N+1: el usuario llega con un join fetch y los servicios de toda la página en una sola consulta
	@Test
	void listingRunsAFixedNumberOfQueriesWhateverThePageSize() throws Exception {
		String admin = adminToken();
		Long shared = createService(admin, uniqueName("Compartido"));
		int count = 6;
		for (int i = 0; i < count; i++) {
			Long own = createService(admin, uniqueName("Propio"));
			createProfessional(admin, professionalBody("Lote " + i, uniqueEmail(), "Especialidad", List.of(shared, own)));
		}
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		try {
			for (String token : new String[] { null, admin }) {
				statistics.clear();
				String page = list(token, "serviceId", String.valueOf(shared));
				assertThat(listedIds(page)).hasSize(count);
				assertThat(JsonPath.<List<Object>>read(page, "$.content[*].services[*].id")).hasSize(2 * count);
				// página + lote de servicios (+ el usuario del token, que carga el filtro JWT); el count sobra
				// porque la página no está llena. Con N+1 serían al menos 1 + 6 usuarios + 6 colecciones
				assertThat(statistics.getPrepareStatementCount()).isEqualTo(token == null ? 2 : 3);
			}
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	// ---------- edición ----------

	@Test
	void putReplacesOnlySpecialtyAndBio() throws Exception {
		String admin = adminToken();
		String email = uniqueEmail();
		Map<String, Object> request = professionalBody("Pablo Díaz", email, "Nutrición", List.of());
		request.put("bio", "Bio original");
		request.put("phone", "3000000000");
		Long id = createProfessional(admin, request);
		String path = PROFESSIONALS + "/" + id;
		Long userId = idOf(email);

		Map<String, Object> replacement = new LinkedHashMap<>();
		replacement.put("specialty", "  Nutrición deportiva  ");
		replacement.put("id", MISSING_ID);                              // todo esto se ignora
		replacement.put("userId", 1);
		replacement.put("active", false);
		replacement.put("email", "otro-" + email);
		replacement.put("fullName", "Otro Nombre");
		replacement.put("phone", "3111111111");

		mvc.perform(withJson(put(path), admin, replacement))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.intValue()))
				.andExpect(jsonPath("$.specialty").value("Nutrición deportiva"))
				.andExpect(jsonPath("$.bio").value(nullValue()))           // se omitió: queda vacía
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.userId").value(userId.intValue()))
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.fullName").value("Pablo Díaz"))
				.andExpect(jsonPath("$.phone").value("3000000000"));
		User user = users.findById(userId).orElseThrow();
		assertThat(user.getEmail()).isEqualTo(email);
		assertThat(user.getFullName()).isEqualTo("Pablo Díaz");
		assertThat(users.existsByEmail("otro-" + email)).isFalse();

		mvc.perform(withJson(put(path), admin, Map.of("specialty", "Nutrición", "bio", "  Nueva bio  ")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bio").value("Nueva bio"));
		mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.specialty").value("Nutrición"));

		mvc.perform(withJson(put(path), admin, Map.of("specialty", "   ")))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path))
				.andExpect(jsonPath("$.details[0].field").value("specialty"));
		mvc.perform(withJson(put(path), admin, Map.of("specialty", "Nutrición", "bio", "b".repeat(501))))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path))
				.andExpect(jsonPath("$.details[0].field").value("bio"));
	}

	@Test
	void putServicesReplacesTheWholeSet() throws Exception {
		String admin = adminToken();
		Long first = createService(admin, uniqueName("Primero"));
		Long second = createService(admin, uniqueName("Segundo"));
		Long third = createService(admin, uniqueName("Tercero"));
		Long id = createProfessional(admin, uniqueEmail(), List.of(first));
		String profilePath = PROFESSIONALS + "/" + id;
		String path = profilePath + "/services";

		String replaced = body(mvc.perform(withJson(put(path), admin, Map.of("serviceIds", List.of(third, second, third))))
				.andExpect(status().isOk())
				.andReturn());
		assertThat(keys(replaced)).containsExactlyInAnyOrder(ADMIN_KEYS);
		assertThat(serviceIds(replaced)).containsExactly(second, third);
		assertThat(listedIds(list(null, "serviceId", String.valueOf(first)))).doesNotContain(id);
		assertThat(listedIds(list(null, "serviceId", String.valueOf(second)))).contains(id);

		// Con un id inválido responde 422 y el conjunto no cambia
		mvc.perform(withJson(put(path), admin, Map.of("serviceIds", List.of(first, MISSING_ID))))
				.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_SERVICE_IDS", path))
				.andExpect(jsonPath("$.details.length()").value(1));
		assertThat(serviceIds(view(profilePath, admin))).containsExactly(second, third);

		// Un servicio que se desactiva después sigue asignado, pero desaparece de la vista pública
		deactivateService(admin, third);
		assertThat(serviceIds(view(profilePath, null))).containsExactly(second);
		assertThat(serviceIds(view(profilePath, admin))).containsExactly(second, third);
		mvc.perform(withJson(put(path), admin, Map.of("serviceIds", List.of(second, third))))
				.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_SERVICE_IDS", path))
				.andExpect(jsonPath("$.details[0].message").value("El servicio " + third + " no existe o está inactivo"));

		// Lista vacía: lo deja sin servicios
		mvc.perform(withJson(put(path), admin, Map.of("serviceIds", List.of())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.services").isEmpty());
		assertThat(serviceIds(view(profilePath, admin))).isEmpty();

		mvc.perform(withJson(put(path), admin, "{}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path))
				.andExpect(jsonPath("$.details[0].field").value("serviceIds"));
		mvc.perform(withJson(put(path), admin, "{\"serviceIds\":[" + first + ",null]}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", path))
				.andExpect(jsonPath("$.details[0].field").value("serviceIds[1]"));
		mvc.perform(withJson(put(path), admin, "{\"serviceIds\":[1.5]}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", path));
	}

	// ---------- errores ----------

	@Test
	void unknownProfessionalReturns404() throws Exception {
		String admin = adminToken();
		String path = PROFESSIONALS + "/" + MISSING_ID;

		mvc.perform(get(path)).andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(withJson(put(path), admin, Map.of("specialty", "Fantasma")))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		mvc.perform(withJson(patch(path + "/active"), admin, Map.of("active", true)))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path + "/active"));
		mvc.perform(withJson(put(path + "/services"), admin, Map.of("serviceIds", List.of())))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path + "/services"));
		mvc.perform(get(PROFESSIONALS + "/abc"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", PROFESSIONALS + "/abc"));
	}

	// ---------- utilidades ----------

	private record Write(HttpMethod method, String path) {}

	private static String uniqueName(String prefix) {
		return prefix + " " + NAME_SEQUENCE.incrementAndGet() + "-" + System.nanoTime();
	}

	private static String capitalize(String email) {
		return email.substring(0, 1).toUpperCase(Locale.ROOT) + email.substring(1);
	}

	private static Map<String, Object> professionalBody(String fullName, String email, String specialty,
			List<Long> serviceIds) {
		Map<String, Object> body = new LinkedHashMap<>();                  // admite null (Map.of no)
		body.put("fullName", fullName);
		body.put("email", email);
		body.put("password", PASSWORD);
		body.put("specialty", specialty);
		body.put("serviceIds", serviceIds);
		return body;
	}

	private Long createProfessional(String adminToken, String email, List<Long> serviceIds) throws Exception {
		return createProfessional(adminToken, professionalBody("Profesional de prueba", email, "Especialidad de prueba",
				serviceIds));
	}

	private Long createProfessional(String adminToken, Map<String, Object> requestBody) throws Exception {
		String created = body(mvc.perform(withJson(post(PROFESSIONALS), adminToken, requestBody))
				.andExpect(status().isCreated()).andReturn());
		return JsonPath.<Number>read(created, "$.id").longValue();
	}

	private Long createService(String adminToken, String name) throws Exception {
		Map<String, Object> requestBody = Map.of("name", name, "durationMinutes", 30, "price", new BigDecimal("50000.00"));
		String created = body(mvc.perform(withJson(post(SERVICES), adminToken, requestBody))
				.andExpect(status().isCreated()).andReturn());
		return JsonPath.<Number>read(created, "$.id").longValue();
	}

	private Long createInactiveService(String adminToken, String name) throws Exception {
		Long id = createService(adminToken, name);
		deactivateService(adminToken, id);
		return id;
	}

	private void deactivateService(String adminToken, Long id) throws Exception {
		mvc.perform(delete(SERVICES + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
				.andExpect(status().isNoContent());
	}

	// GET de un profesional; devuelve el cuerpo de una respuesta 200
	private String view(String path, String token) throws Exception {
		return body(mvc.perform(authorized(get(path), token)).andExpect(status().isOk()).andReturn());
	}

	// GET /professionals con pares nombre/valor de parámetros; devuelve el cuerpo de una respuesta 200
	private String list(String token, String... params) throws Exception {
		MockHttpServletRequestBuilder request = authorized(get(PROFESSIONALS), token);
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return body(mvc.perform(request).andExpect(status().isOk()).andReturn());
	}

	private static List<Long> listedIds(String pageBody) {
		return JsonPath.<List<Number>>read(pageBody, "$.content[*].id").stream().map(Number::longValue).toList();
	}

	private static List<Long> serviceIds(String professionalBody) {
		return JsonPath.<List<Number>>read(professionalBody, "$.services[*].id").stream().map(Number::longValue).toList();
	}

}
