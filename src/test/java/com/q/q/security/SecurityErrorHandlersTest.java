package com.q.q.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/** Los 401 y 403 que nacen en los filtros de seguridad también salen con el JSON de la sección 8.4. */
class SecurityErrorHandlersTest {

	// 15:30:45.678 UTC son las 10:30:45 en America/Bogota
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T15:30:45.678Z"), ZoneId.of("America/Bogota"));
	private final SecurityErrorWriter writer = new SecurityErrorWriter(JsonMapper.builder().build(), clock);

	@Test
	void entryPointWrites401InErrorFormat() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new RestAuthenticationEntryPoint(writer).commence(request("/api/v1/users/me"), response,
				new InsufficientAuthenticationException("Full authentication is required"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
		assertThat(json(response))
				.containsOnlyKeys("timestamp", "status", "error", "code", "message", "path", "details")
				.containsEntry("timestamp", "2026-10-24T10:30:45")
				.containsEntry("status", 401)
				.containsEntry("error", "Unauthorized")
				.containsEntry("code", "AUTHENTICATION_REQUIRED")
				.containsEntry("path", "/api/v1/users/me");
	}

	@Test
	void accessDeniedHandlerWrites403InErrorFormat() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new RestAccessDeniedHandler(writer).handle(request("/api/v1/services"), response,
				new AccessDeniedException("Access Denied"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(json(response))
				.containsOnlyKeys("timestamp", "status", "error", "code", "message", "path", "details")
				.containsEntry("timestamp", "2026-10-24T10:30:45")
				.containsEntry("status", 403)
				.containsEntry("error", "Forbidden")
				.containsEntry("code", "ACCESS_DENIED")
				.containsEntry("path", "/api/v1/services");
	}

	private static MockHttpServletRequest request(String uri) {
		return new MockHttpServletRequest("GET", uri);
	}

	private static Map<String, Object> json(MockHttpServletResponse response) throws Exception {
		assertThat(response.getContentType()).startsWith("application/json");
		return JsonPath.read(response.getContentAsString(StandardCharsets.UTF_8), "$");
	}

}
