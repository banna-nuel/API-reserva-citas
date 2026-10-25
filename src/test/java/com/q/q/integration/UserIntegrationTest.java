package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;

@IntegrationTest
class UserIntegrationTest extends IntegrationTestSupport {

	private static final String USERS = "/api/v1/users";
	private static final String MY_PASSWORD = ME + "/password";
	private static final String NEW_PASSWORD = "otra-clave-segura-456";
	private static final String[] PAGE_KEYS = { "content", "page", "size", "totalElements", "totalPages" };

	// ---------- PUT /users/me ----------

	@Test
	void updateProfileChangesOnlyNameAndPhone() throws Exception {
		String email = registerClient();
		String token = loginToken(email, PASSWORD);
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("fullName", "  Nombre Actualizado  ");
		request.put("phone", "3109876543");
		request.put("email", "otro-" + email);                              // se ignoran: no son editables
		request.put("role", "ADMIN");
		request.put("active", false);

		String body = body(mvc.perform(put(ME).header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content(jsonBody(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Nombre Actualizado"))
				.andExpect(jsonPath("$.phone").value("3109876543"))
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("CLIENT"))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn());

		assertThat(keys(body)).containsExactlyInAnyOrder(USER_KEYS);
		assertThat(users.findByEmail(email)).get().satisfies(user -> {
			assertThat(user.getFullName()).isEqualTo("Nombre Actualizado");
			assertThat(user.getRole()).isEqualTo(Role.CLIENT);
			assertThat(user.isActive()).isTrue();
		});
		assertThat(users.existsByEmail("otro-" + email)).isFalse();
		// El email (subject del JWT) no cambió: el mismo token sigue sirviendo
		mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Nombre Actualizado"));
	}

	@Test
	void updateProfileRequiresAuthenticationAndAName() throws Exception {
		mvc.perform(put(ME).contentType(MediaType.APPLICATION_JSON).content(jsonBody(Map.of("fullName", "Ana"))))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", ME));

		String token = loginToken(registerClient(), PASSWORD);
		mvc.perform(put(ME).header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content(jsonBody(Map.of("fullName", "   "))))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ME))
				.andExpect(jsonPath("$.details[0].field").value("fullName"));
	}

	// ---------- PATCH /users/me/password ----------

	@Test
	void afterChangingPasswordOnlyTheNewOneLogsIn() throws Exception {
		String email = registerClient();
		String token = loginToken(email, PASSWORD);

		mvc.perform(changePassword(token, PASSWORD, NEW_PASSWORD))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		mvc.perform(login(email, PASSWORD)).andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", LOGIN));
		mvc.perform(login(email, NEW_PASSWORD)).andExpect(status().isOk());
	}

	@Test
	void wrongCurrentPasswordReturns422AndKeepsThePassword() throws Exception {
		String email = registerClient();
		String token = loginToken(email, PASSWORD);

		mvc.perform(changePassword(token, "no-es-la-actual", NEW_PASSWORD))
				.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_CURRENT_PASSWORD", MY_PASSWORD));

		mvc.perform(login(email, PASSWORD)).andExpect(status().isOk());
	}

	@Test
	void newPasswordEqualToCurrentReturns422() throws Exception {
		String token = loginToken(registerClient(), PASSWORD);

		mvc.perform(changePassword(token, PASSWORD, PASSWORD))
				.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "PASSWORD_UNCHANGED", MY_PASSWORD));
	}

	@ParameterizedTest
	@MethodSource("passwordsOutsideRn11")
	void newPasswordOutsideRn11Returns400(String newPassword) throws Exception {
		String email = registerClient();
		String token = loginToken(email, PASSWORD);

		mvc.perform(changePassword(token, PASSWORD, newPassword))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MY_PASSWORD))
				.andExpect(jsonPath("$.details[0].field").value("newPassword"));

		mvc.perform(login(email, PASSWORD)).andExpect(status().isOk());
	}

	static Stream<String> passwordsOutsideRn11() {
		return Stream.of("a".repeat(7), "a".repeat(73), "ñ".repeat(40));     // 7 y 73 caracteres, 80 bytes
	}

	@Test
	void changePasswordRequiresAuthentication() throws Exception {
		mvc.perform(patch(MY_PASSWORD).contentType(MediaType.APPLICATION_JSON)
						.content(jsonBody(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", MY_PASSWORD));
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void changePasswordNeverLogsPasswords(CapturedOutput output) throws Exception {
		String token = loginToken(registerClient(), PASSWORD);

		mvc.perform(changePassword(token, "intento-fallido-1", NEW_PASSWORD)).andExpect(status().isUnprocessableContent());
		mvc.perform(changePassword(token, PASSWORD, NEW_PASSWORD)).andExpect(status().isNoContent());

		assertThat(output).doesNotContain(PASSWORD, NEW_PASSWORD, "intento-fallido-1");
	}

	// ---------- GET /users ----------

	@Test
	void adminListsUsersPagedWithStableDefaultOrder() throws Exception {
		registerClient();
		registerClient();

		String body = body(mvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andReturn());

		assertThat(keys(body)).containsExactlyInAnyOrder(PAGE_KEYS);
		List<Map<String, Object>> content = JsonPath.read(body, "$.content");
		assertThat(content).isNotEmpty().allSatisfy(user -> assertThat(user).containsOnlyKeys(USER_KEYS));
		assertThat(JsonPath.<List<Integer>>read(body, "$.content[*].id")).isSorted();
		assertThat(body).doesNotContain("passwordHash", "$2a$");
	}

	@Test
	void adminFiltersByRole() throws Exception {
		registerClient();
		String token = adminToken();

		String clients = body(mvc.perform(get(USERS).param("role", "CLIENT").param("size", "100")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk()).andReturn());
		assertThat(JsonPath.<List<String>>read(clients, "$.content[*].role")).isNotEmpty().containsOnly("CLIENT");

		String admins = body(mvc.perform(get(USERS).param("role", "ADMIN")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk()).andReturn());
		assertThat(JsonPath.<List<String>>read(admins, "$.content[*].role")).containsOnly("ADMIN");
		assertThat(JsonPath.<List<String>>read(admins, "$.content[*].email"))
				.contains(User.normalizeEmail(IntegrationTest.ADMIN_EMAIL));
	}

	@Test
	void pageBeyondTheLastReturnsEmptyContentWithRealTotals() throws Exception {
		String body = body(mvc.perform(get(USERS).param("page", "9999")
				.header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))).andExpect(status().isOk()).andReturn());

		assertThat(JsonPath.<List<Object>>read(body, "$.content")).isEmpty();
		assertThat(JsonPath.<Integer>read(body, "$.page")).isEqualTo(9999);
		assertThat(JsonPath.<Integer>read(body, "$.totalElements")).isPositive();
	}

	@Test
	void pageSizeIsCappedAt100() throws Exception {
		mvc.perform(get(USERS).param("size", "1000").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
	}

	@Test
	void invalidRoleFilterReturns400() throws Exception {
		mvc.perform(get(USERS).param("role", "SUPERUSER").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", USERS));
	}

	@Test
	void sortOnlyAcceptsWhitelistedFields() throws Exception {
		String token = adminToken();
		for (String forbidden : new String[] { "passwordHash", "passwordHash,desc", "password" }) {
			mvc.perform(get(USERS).param("sort", forbidden).header(HttpHeaders.AUTHORIZATION, bearer(token)))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_SORT", USERS));
		}

		registerClient();
		String body = body(mvc.perform(get(USERS).param("sort", "id,desc").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk()).andReturn());
		assertThat(JsonPath.<List<Integer>>read(body, "$.content[*].id")).isSortedAccordingTo(Comparator.reverseOrder());
	}

	@Test
	void listingUsersRequiresAdmin() throws Exception {
		mvc.perform(get(USERS)).andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", USERS));

		String clientToken = loginToken(registerClient(), PASSWORD);
		mvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", USERS));
	}

	// ---------- PATCH /users/{id}/active ----------

	@Test
	void deactivatedUserLosesAccessImmediatelyAndCanBeReactivated() throws Exception {
		String email = registerClient();
		String clientToken = loginToken(email, PASSWORD);
		Long id = idOf(email);
		String adminToken = adminToken();
		mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(clientToken))).andExpect(status().isOk());

		String body = body(mvc.perform(setActive(adminToken, id, false))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.intValue()))
				.andExpect(jsonPath("$.active").value(false))
				.andReturn());
		assertThat(keys(body)).containsExactlyInAnyOrder(USER_KEYS);

		mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))       // la siguiente petición
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", ME));
		mvc.perform(login(email, PASSWORD)).andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", LOGIN));

		mvc.perform(setActive(adminToken, id, true)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
		mvc.perform(login(email, PASSWORD)).andExpect(status().isOk());
	}

	@Test
	void adminCannotDeactivateThemselves() throws Exception {
		String adminToken = adminToken();
		Long adminId = idOf(IntegrationTest.ADMIN_EMAIL);
		String path = USERS + "/" + adminId + "/active";

		mvc.perform(setActive(adminToken, adminId, false))
				.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "CANNOT_DEACTIVATE_SELF", path));

		assertThat(users.findById(adminId)).get().extracting(User::isActive).isEqualTo(true);
		mvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))).andExpect(status().isOk());
	}

	@Test
	void unknownUserReturns404() throws Exception {
		mvc.perform(setActive(adminToken(), 99_999_999L, false))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", USERS + "/99999999/active"));
	}

	@Test
	void missingActiveFlagReturns400() throws Exception {
		Long id = idOf(registerClient());

		mvc.perform(patch(USERS + "/" + id + "/active").header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", USERS + "/" + id + "/active"))
				.andExpect(jsonPath("$.details[0].field").value("active"));
	}

	@Test
	void changingActiveFlagRequiresAdmin() throws Exception {
		String targetEmail = registerClient();
		Long targetId = idOf(targetEmail);
		String path = USERS + "/" + targetId + "/active";

		mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(jsonBody(Map.of("active", false))))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", path));

		String clientToken = loginToken(registerClient(), PASSWORD);
		mvc.perform(setActive(clientToken, targetId, false))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", path));

		assertThat(users.findById(targetId)).get().extracting(User::isActive).isEqualTo(true);
	}

	// ---------- utilidades ----------

	private MockHttpServletRequestBuilder changePassword(String token, String currentPassword, String newPassword) {
		return patch(MY_PASSWORD).header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody(Map.of("currentPassword", currentPassword, "newPassword", newPassword)));
	}

	private MockHttpServletRequestBuilder setActive(String token, Long userId, boolean active) {
		return patch(USERS + "/" + userId + "/active").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody(Map.of("active", active)));
	}

}
