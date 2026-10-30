package com.q.q.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ConflictException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Verifica que cada tipo de error responda con el JSON de la sección 8.4.
 * {@link ProbeController} existe solo en src/test y provoca cada caso a propósito.
 */
@WebMvcTest(GlobalExceptionHandlerTest.ProbeController.class)
@Import({ GlobalExceptionHandlerTest.ProbeController.class, GlobalExceptionHandlerTest.FixedClockConfig.class })
@WithMockUser
class GlobalExceptionHandlerTest {

	// 22:05:12.345 UTC son las 17:05:12 en America/Bogota: el timestamp sale en hora del negocio y sin milisegundos
	private static final String EXPECTED_TIMESTAMP = "2026-10-23T17:05:12";

	@Autowired
	private MockMvc mvc;

	@Test
	void invalidBodyReturns400WithFieldDetails() throws Exception {
		mvc.perform(post("/probe/items").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"\", \"email\": \"no-es-un-correo\"}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "/probe/items"))
				.andExpect(jsonPath("$.details[*].field", containsInAnyOrder("name", "email")))
				.andExpect(jsonPath("$.details[*].message", everyItem(not(emptyOrNullString()))));
	}

	@Test
	void malformedJsonReturns400() throws Exception {
		mvc.perform(post("/probe/items").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Ana\", "))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "/probe/items"))
				.andExpect(jsonPath("$.details").isEmpty());
	}

	@Test
	void wrongParameterTypeReturns400() throws Exception {
		mvc.perform(get("/probe/items/abc"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "/probe/items/abc"));
	}

	@Test
	void missingRequiredParameterReturns400() throws Exception {
		mvc.perform(get("/probe/search"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "/probe/search"));
	}

	@Test
	void constrainedParameterReturns400WithFieldDetails() throws Exception {
		mvc.perform(get("/probe/search").param("size", "0"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "/probe/search"))
				.andExpect(jsonPath("$.details[0].field").value("size"));
	}

	@Test
	void missingResourceReturns404() throws Exception {
		mvc.perform(get("/probe/items/99"))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "/probe/items/99"))
				.andExpect(jsonPath("$.message").value("Profesional no encontrado"));
	}

	@Test
	void unknownRouteReturns404() throws Exception {
		mvc.perform(get("/no-such-route"))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", "/no-such-route"));
	}

	@Test
	void unsupportedMethodReturns405WithAllowHeader() throws Exception {
		mvc.perform(delete("/probe/items/1").with(csrf()))
				.andExpectAll(apiError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "/probe/items/1"))
				.andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")));
	}

	@Test
	void conflictReturns409WithItsCode() throws Exception {
		mvc.perform(get("/probe/conflict"))
				.andExpectAll(apiError(HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE", "/probe/conflict"))
				.andExpect(jsonPath("$.message").value("El profesional ya tiene una cita en ese horario"));
	}

	@Test
	void businessRuleReturns422WithItsCode() throws Exception {
		mvc.perform(get("/probe/business-rule"))
				.andExpectAll(apiError(HttpStatus.UNPROCESSABLE_CONTENT, "TOO_SOON", "/probe/business-rule"))
				.andExpect(jsonPath("$.message").value("La cita requiere más anticipación"));
	}

	@Test
	void forbiddenOperationReturns403() throws Exception {
		mvc.perform(get("/probe/forbidden-operation"))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", "/probe/forbidden-operation"));
	}

	@Test
	void accessDeniedInsideControllerReturns403Not500() throws Exception {
		mvc.perform(get("/probe/access-denied"))
				.andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "/probe/access-denied"));
	}

	@Test
	void authenticationFailureReturns401() throws Exception {
		mvc.perform(get("/probe/bad-credentials"))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "/probe/bad-credentials"));
	}

	@Test
	void internalAuthenticationFailureReturns500NotCredentialsError() throws Exception {
		mvc.perform(get("/probe/auth-internal"))
				.andExpectAll(apiError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "/probe/auth-internal"));
	}

	@Test
	void otherSpringMvcClientErrorsKeepTheirStatus() throws Exception {
		mvc.perform(post("/probe/items").with(csrf())
						.contentType(MediaType.TEXT_PLAIN)
						.content("name=Ana"))
				.andExpectAll(apiError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "/probe/items"));
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void unexpectedErrorReturns500WithoutStacktrace(CapturedOutput output) throws Exception {
		String body = mvc.perform(get("/probe/unexpected"))
				.andExpectAll(apiError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "/probe/unexpected"))
				.andExpect(jsonPath("$.message").value("Error interno del servidor"))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(body).doesNotContain("IllegalStateException", "Detalle interno", "trace", "at com.q.q");
		assertThat(output).contains("Error no controlado en /probe/unexpected", "IllegalStateException");
	}

	private static ResultMatcher[] apiError(HttpStatus status, String code, String path) {
		return new ResultMatcher[] {
				status().is(status.value()),
				content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
				jsonPath("$.timestamp").value(EXPECTED_TIMESTAMP),
				jsonPath("$.status").value(status.value()),
				jsonPath("$.error").value(status.getReasonPhrase()),
				jsonPath("$.code").value(code),
				jsonPath("$.message").isNotEmpty(),
				jsonPath("$.path").value(path),
				jsonPath("$.details").isArray(),
				result -> {
					Map<String, Object> json = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$");
					assertThat(json).containsOnlyKeys("timestamp", "status", "error", "code", "message", "path", "details");
				} };
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-10-23T22:05:12.345Z"), ZoneId.of("America/Bogota"));
		}

		// Sin este bean, Spring Boot crearía un usuario en memoria y escribiría su contraseña generada en el log
		@Bean
		UserDetailsService userDetailsService() {
			return username -> {
				throw new UsernameNotFoundException("no hay usuarios en esta prueba");
			};
		}
	}

	record ProbeRequest(@NotBlank String name, @NotBlank @Email String email) {}

	@RestController
	@RequestMapping("/probe")
	static class ProbeController {

		@PostMapping("/items")
		String create(@Valid @RequestBody ProbeRequest request) {
			return "ok";
		}

		@GetMapping("/items/{id}")
		String find(@PathVariable Long id) {
			throw new ResourceNotFoundException("Profesional no encontrado");
		}

		@GetMapping("/search")
		String search(@RequestParam @Min(1) int size) {
			return "ok";
		}

		@GetMapping("/conflict")
		String conflict() {
			throw new ConflictException("SLOT_NOT_AVAILABLE", "El profesional ya tiene una cita en ese horario");
		}

		@GetMapping("/business-rule")
		String businessRule() {
			throw new BusinessRuleException("TOO_SOON", "La cita requiere más anticipación");
		}

		@GetMapping("/forbidden-operation")
		String forbiddenOperation() {
			throw new ForbiddenOperationException("No puedes modificar esta cita");
		}

		@GetMapping("/access-denied")
		String accessDenied() {
			throw new AccessDeniedException("Access Denied");
		}

		@GetMapping("/bad-credentials")
		String badCredentials() {
			throw new BadCredentialsException("Bad credentials");
		}

		@GetMapping("/auth-internal")
		String authInternal() {
			throw new InternalAuthenticationServiceException("La base de datos no responde");
		}

		@GetMapping("/unexpected")
		String unexpected() {
			throw new IllegalStateException("Detalle interno que nunca debe llegar al cliente");
		}
	}

}
