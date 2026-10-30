package com.q.q.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.q.q.entity.enums.Role;
import com.q.q.security.AuthUser;

/**
 * Base de las pruebas @WebMvcTest: un controlador, su servicio simulado y GlobalExceptionHandler. Prueban binding,
 * validación a 400 con el formato de 8.4, códigos, Location y los argumentos que llegan al servicio. La seguridad
 * (filtros, reglas por URL y @PreAuthorize) no se carga: ya está probada de extremo a extremo en las pruebas de
 * integración. Cada subclase lleva @WebMvcTest(SuControlador.class), @AutoConfigureMockMvc(addFilters = false) e
 * @Import(ControllerSliceSupport.SliceBeans.class).
 */
abstract class ControllerSliceSupport {

	private static final String[] ERROR_KEYS = { "timestamp", "status", "error", "code", "message", "path", "details" };

	@Autowired
	protected MockMvc mvc;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	// El formato de error de 8.4 con exactamente estos campos en details (ninguno si no se pasan)
	protected static ResultMatcher[] apiError(HttpStatus status, String code, String... fields) {
		return new ResultMatcher[] {
				status().is(status.value()),
				jsonPath("$.status").value(status.value()),
				jsonPath("$.error").value(status.getReasonPhrase()),
				jsonPath("$.code").value(code),
				jsonPath("$.message").isNotEmpty(),
				jsonPath("$.path").isNotEmpty(),
				result -> {
					String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
					assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet()).containsExactlyInAnyOrder(ERROR_KEYS);
					assertThat(JsonPath.<List<String>>read(body, "$.details[*].field")).containsExactlyInAnyOrder(fields);
				} };
	}

	// Sin filtros, @AuthenticationPrincipal lee directamente el SecurityContext del hilo de la prueba
	protected static AuthUser authenticateAs(Long id, Role role) {
		AuthUser user = new AuthUser(id, "usuario@mail.test", "hash-sintetico", role, true);
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
		return user;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SliceBeans {

		@Bean
		Clock clock() {                                      // lo necesita GlobalExceptionHandler
			return Clock.fixed(Instant.parse("2026-11-02T13:00:00Z"), ZoneId.of("America/Bogota"));
		}

		// Sin este bean, Spring Boot crearía un usuario en memoria y escribiría su contraseña generada en el log
		@Bean
		UserDetailsService userDetailsService() {
			return username -> {
				throw new UsernameNotFoundException("no hay usuarios en las pruebas de controlador");
			};
		}

	}

}
