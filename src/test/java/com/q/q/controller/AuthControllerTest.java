package com.q.q.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.q.q.dto.request.LoginRequest;
import com.q.q.dto.request.RegisterRequest;
import com.q.q.dto.response.AuthResponse;
import com.q.q.dto.response.UserResponse;
import com.q.q.entity.enums.Role;
import com.q.q.service.AuthService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerSliceSupport.SliceBeans.class)
class AuthControllerTest extends ControllerSliceSupport {

	@MockitoBean
	private AuthService authService;

	@Test
	void registerReturns201WithTheLocationOfTheProfileAndIgnoresTheRole() throws Exception {
		when(authService.register(any())).thenReturn(new UserResponse(7L, "Ana Pérez", "ana@example.com", null,
				Role.CLIENT, true, LocalDateTime.of(2026, 11, 2, 8, 0)));

		mvc.perform(json(post("/api/v1/auth/register"), """
				{ "fullName": "Ana Pérez", "email": "Ana@Example.com", "password": "una-clave-segura", "role": "ADMIN" }
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/users/me")))
				.andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.role").value("CLIENT"));

		// El email llega tal cual (lo normaliza el servicio); "role" no forma parte del body
		verify(authService).register(new RegisterRequest("Ana Pérez", "Ana@Example.com", "una-clave-segura", null));
	}

	@Test
	void anInvalidRegistrationIs400WithEveryFieldAndNeverReachesTheService() throws Exception {
		mvc.perform(json(post("/api/v1/auth/register"), """
				{ "fullName": " ", "email": "no-es-un-email", "password": "corta" }
				"""))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "fullName", "email", "password"));
		mvc.perform(json(post("/api/v1/auth/register"), "{ no es json"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST"));
		verifyNoInteractions(authService);
	}

	@Test
	void loginReturnsTheTokenFromTheService() throws Exception {
		when(authService.login(any())).thenReturn(new AuthResponse("token-sintetico", "Bearer", 3600));

		mvc.perform(json(post("/api/v1/auth/login"), """
				{ "email": "ana@example.com", "password": "una-clave-segura" }
				"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("token-sintetico"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresInSeconds").value(3600));

		verify(authService).login(new LoginRequest("ana@example.com", "una-clave-segura"));
	}

	@Test
	void anIncompleteLoginIs400() throws Exception {
		mvc.perform(json(post("/api/v1/auth/login"), "{ \"email\": \"ana@example.com\" }"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "password"));
		verifyNoInteractions(authService);
	}

}
