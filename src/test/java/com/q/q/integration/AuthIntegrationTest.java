package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.config.AppProperties;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.security.AuthUser;
import com.q.q.security.JwtService;

@IntegrationTest
class AuthIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private AppProperties props;

	@Autowired
	private Clock clock;

	// ---------- POST /auth/register ----------

	@Test
	void registerCreatesActiveClientWithoutExposingPassword() throws Exception {
		String email = uniqueEmail();
		String body = body(mvc.perform(register("Ana Pérez", email.toUpperCase(Locale.ROOT), PASSWORD))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, endsWith(ME)))
				.andExpect(jsonPath("$.email").value(email))                     // RN-11: guardado en minúsculas
				.andExpect(jsonPath("$.role").value("CLIENT"))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn());

		assertThat(keys(body)).containsExactlyInAnyOrder(USER_KEYS);
		assertThat(body).doesNotContain(PASSWORD, "$2a$", "$2b$", "$2y$", "password");
		assertThat(users.findByEmail(email)).get()
				.satisfies(user -> assertThat(user.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD));
	}

	@Test
	void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
		String email = uniqueEmail();
		mvc.perform(register("Ana", email, PASSWORD)).andExpect(status().isCreated());

		mvc.perform(register("Otra Ana", email, PASSWORD))
				.andExpectAll(apiError(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", REGISTER));
		mvc.perform(register("Otra Ana", email.toUpperCase(Locale.ROOT), PASSWORD))
				.andExpectAll(apiError(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", REGISTER));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidRegistrations")
	void registerRejectsInvalidInput(String description, String email, String password, String invalidField)
			throws Exception {
		mvc.perform(register("Ana", email, password))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", REGISTER))
				.andExpect(jsonPath("$.details[*].field", hasItem(invalidField)));

		assertThat(users.existsByEmail(User.normalizeEmail(email))).isFalse();
	}

	static Stream<Arguments> invalidRegistrations() {
		return Stream.of(
				Arguments.of("email inválido", "no-es-un-correo", PASSWORD, "email"),
				Arguments.of("clave de 7 caracteres", uniqueEmail(), "a".repeat(7), "password"),
				Arguments.of("clave de 73 caracteres", uniqueEmail(), "a".repeat(73), "password"),
				Arguments.of("clave de 40 eñes (80 bytes)", uniqueEmail(), "ñ".repeat(40), "password"));
	}

	@Test
	void registerIgnoresRoleAndActiveSentByClient() throws Exception {
		String email = uniqueEmail();
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("fullName", "Intruso");
		request.put("email", email);
		request.put("password", PASSWORD);
		request.put("role", "ADMIN");
		request.put("active", false);

		mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(jsonBody(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("CLIENT"))
				.andExpect(jsonPath("$.active").value(true));

		assertThat(users.findByEmail(email)).get().satisfies(user -> {
			assertThat(user.getRole()).isEqualTo(Role.CLIENT);
			assertThat(user.isActive()).isTrue();
		});
	}

	// ---------- POST /auth/login ----------

	@Test
	void loginReturnsBearerTokenAndAcceptsEmailInAnyCase() throws Exception {
		String email = registerClient();

		String body = body(mvc.perform(login(email.toUpperCase(Locale.ROOT), PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresInSeconds").value(3600))
				.andReturn());

		assertThat(keys(body)).containsExactlyInAnyOrder("accessToken", "tokenType", "expiresInSeconds");
		assertThat(JsonPath.<String>read(body, "$.accessToken")).matches("[\\w-]+\\.[\\w-]+\\.[\\w-]+");
	}

	@Test
	void loginFailuresAreIndistinguishable() throws Exception {
		String activeEmail = registerClient();
		String inactiveEmail = registerClient();
		deactivate(inactiveEmail);

		Map<String, Object> unknownEmail = failedLogin(uniqueEmail(), PASSWORD);
		Map<String, Object> wrongPassword = failedLogin(activeEmail, "otra-clave-123");
		Map<String, Object> inactiveUser = failedLogin(inactiveEmail, PASSWORD);   // clave correcta, cuenta inactiva

		assertThat(unknownEmail).isEqualTo(wrongPassword).isEqualTo(inactiveUser);
		assertThat(unknownEmail).containsEntry("code", "INVALID_CREDENTIALS");
	}

	@Test
	void loginRejectsPasswordLongerThan72BytesEvenIfThePrefixMatches() throws Exception {
		String email = uniqueEmail();
		String password72 = "a".repeat(72);
		mvc.perform(register("Ana", email, password72)).andExpect(status().isCreated());

		mvc.perform(login(email, password72)).andExpect(status().isOk());
		mvc.perform(login(email, password72 + "x"))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", LOGIN));
	}

	// ---------- GET /users/me ----------

	@Test
	void meWithoutTokenReturns401InErrorFormat() throws Exception {
		mvc.perform(get(ME))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", ME))
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
	}

	@Test
	void meWithValidTokenReturnsOwnProfile() throws Exception {
		String email = registerClient();

		String body = body(mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(loginToken(email, PASSWORD))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("CLIENT"))
				.andReturn());

		assertThat(keys(body)).containsExactlyInAnyOrder(USER_KEYS);
		assertThat(body).doesNotContain("$2a$", "password");
	}

	@Test
	void meRejectsTamperedTokens() throws Exception {
		String token = loginToken(registerClient(), PASSWORD);
		String[] parts = token.split("\\.");

		// Firma alterada
		String badSignature = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
		// Payload cambiado para hacerse pasar por el admin, conservando la firma original
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
				.replaceAll("\"sub\":\"[^\"]+\"", "\"sub\":\"" + User.normalizeEmail(IntegrationTest.ADMIN_EMAIL) + "\"");
		String impersonation = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

		for (String tampered : new String[] { badSignature, impersonation, "no-es-un-jwt" }) {
			mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
					.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", ME));
		}
	}

	@Test
	void meRejectsExpiredToken() throws Exception {
		AuthUser user = AuthUser.from(users.findByEmail(registerClient()).orElseThrow());
		Clock twoHoursAgo = Clock.fixed(clock.instant().minus(2, ChronoUnit.HOURS), clock.getZone());
		String expired = new JwtService(props, twoHoursAgo).generateToken(user);    // expiró hace una hora

		mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(expired)))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", ME));
	}

	@Test
	void meRejectsTokenOfDeactivatedUser() throws Exception {
		String email = registerClient();
		String token = loginToken(email, PASSWORD);
		mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());

		deactivate(email);

		mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", ME));
	}

	// ---------- Autorización por rol ----------

	@Test
	void clientGets403OnAdminOnlyEndpoint() throws Exception {
		String token = loginToken(registerClient(), PASSWORD);

		mvc.perform(get(AdminOnlyProbeController.PATH).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", AdminOnlyProbeController.PATH));
	}

	@Test
	void bootstrapAdminCanAccessAdminOnlyEndpoint() throws Exception {
		mvc.perform(get(AdminOnlyProbeController.PATH).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}

	// ---------- Rutas públicas de 11.2 ----------

	@Test
	void healthAndApiDocsArePublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("bearerAuth")));
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	// ---------- Nada sensible en los logs ----------

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void authFlowNeverLogsPasswordsHashesOrTokens(CapturedOutput output) throws Exception {
		String email = uniqueEmail();
		mvc.perform(register("Ana", email, PASSWORD)).andExpect(status().isCreated());
		String token = loginToken(email, PASSWORD);
		mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
		mvc.perform(login(email, "otra-clave-123")).andExpect(status().isUnauthorized());
		String hash = users.findByEmail(email).orElseThrow().getPasswordHash();

		assertThat(output).doesNotContain(PASSWORD, "otra-clave-123", hash, token);
	}

	// Cuerpo del 401 sin el timestamp, para comparar respuestas de intentos distintos
	private Map<String, Object> failedLogin(String email, String password) throws Exception {
		MvcResult result = mvc.perform(login(email, password))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", LOGIN))
				.andReturn();
		Map<String, Object> error = new LinkedHashMap<>(jsonMap(body(result)));
		error.remove("timestamp");
		return error;
	}

}
