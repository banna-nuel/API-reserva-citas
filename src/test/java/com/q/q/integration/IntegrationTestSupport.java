package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.entity.User;
import com.q.q.repository.UserRepository;

import tools.jackson.databind.json.JsonMapper;

/** Utilidades comunes de las pruebas de extremo a extremo. Cada subclase lleva @IntegrationTest. */
abstract class IntegrationTestSupport {

	protected static final String REGISTER = "/api/v1/auth/register";
	protected static final String LOGIN = "/api/v1/auth/login";
	protected static final String ME = "/api/v1/users/me";
	protected static final String PASSWORD = "clave-segura-123";
	protected static final String[] ERROR_KEYS = { "timestamp", "status", "error", "code", "message", "path", "details" };
	protected static final String[] USER_KEYS = { "id", "fullName", "email", "phone", "role", "active", "createdAt" };
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JsonMapper json;

	@Autowired
	protected UserRepository users;

	protected static String uniqueEmail() {
		return "cliente" + SEQUENCE.incrementAndGet() + "-" + System.nanoTime() + "@mail.test";
	}

	protected String registerClient() throws Exception {
		String email = uniqueEmail();
		mvc.perform(register("Cliente de prueba", email, PASSWORD)).andExpect(status().isCreated());
		return email;
	}

	protected Long idOf(String email) {
		return users.findByEmail(User.normalizeEmail(email)).orElseThrow().getId();
	}

	protected void deactivate(String email) {
		User user = users.findByEmail(email).orElseThrow();
		user.setActive(false);
		users.save(user);
	}

	protected MockHttpServletRequestBuilder register(String fullName, String email, String password) {
		return post(REGISTER).contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody(Map.of("fullName", fullName, "email", email, "password", password)));
	}

	protected MockHttpServletRequestBuilder login(String email, String password) {
		return post(LOGIN).contentType(MediaType.APPLICATION_JSON)
				.content(jsonBody(Map.of("email", email, "password", password)));
	}

	protected String loginToken(String email, String password) throws Exception {
		String body = body(mvc.perform(login(email, password)).andExpect(status().isOk()).andReturn());
		return JsonPath.read(body, "$.accessToken");
	}

	protected String adminToken() throws Exception {
		return loginToken(IntegrationTest.ADMIN_EMAIL, IntegrationTest.ADMIN_PASSWORD);
	}

	protected String jsonBody(Object value) {
		return json.writeValueAsString(value);
	}

	protected static String bearer(String token) {
		return "Bearer " + token;
	}

	protected static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	protected static Map<String, Object> jsonMap(String body) {
		return JsonPath.read(body, "$");
	}

	protected static Iterable<String> keys(String body) {
		return jsonMap(body).keySet();
	}

	protected static ResultMatcher[] apiError(HttpStatus status, String code, String path) {
		return new ResultMatcher[] {
				status().is(status.value()),
				content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
				jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")),
				jsonPath("$.status").value(status.value()),
				jsonPath("$.error").value(status.getReasonPhrase()),
				jsonPath("$.code").value(code),
				jsonPath("$.message").isNotEmpty(),
				jsonPath("$.path").value(path),
				jsonPath("$.details").isArray(),
				result -> assertThat(keys(body(result))).containsExactlyInAnyOrder(ERROR_KEYS) };
	}

}
