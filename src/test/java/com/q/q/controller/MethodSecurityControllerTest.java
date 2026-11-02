package com.q.q.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.WorkingHoursResponse;
import com.q.q.entity.enums.Role;
import com.q.q.service.AppointmentService;
import com.q.q.service.ProfessionalService;
import com.q.q.service.ServiceOfferingService;
import com.q.q.service.UserService;
import com.q.q.service.WorkingHourService;

/**
 * La capa @PreAuthorize por sí sola. Las pruebas de integración no la distinguen: la regla por URL de SecurityConfig
 * corta antes, así que quitar un @PreAuthorize no rompía nada (lo destapó un sabotaje en la Fase 7). Aquí no hay
 * filtros ni reglas por URL: solo la seguridad de métodos puede responder 403, y el servicio no se llega a tocar.
 */
@WebMvcTest({ UserController.class, ServiceOfferingController.class, ProfessionalController.class,
		WorkingHourController.class, AppointmentController.class })
@AutoConfigureMockMvc(addFilters = false)
@Import({ ControllerSliceSupport.SliceBeans.class, MethodSecurityControllerTest.MethodSecurity.class })
class MethodSecurityControllerTest extends ControllerSliceSupport {

	private static final String SERVICE = "{ \"name\": \"Consulta\", \"durationMinutes\": 30, \"price\": 1 }";
	private static final String PROFESSIONAL = """
			{ "fullName": "Carlos Ruiz", "email": "carlos@example.com", "password": "una-clave-segura", "specialty": "Fisio" }
			""";
	private static final String BOOKING = "{ \"professionalId\": 2, \"serviceId\": 1, \"startTime\": \"2026-11-05T10:00:00\" }";
	private static final String HOURS = "{ \"blocks\": [] }";

	@TestConfiguration(proxyBeanMethods = false)
	@EnableMethodSecurity
	static class MethodSecurity {
	}

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private ServiceOfferingService serviceOfferingService;

	@MockitoBean
	private ProfessionalService professionalService;

	@MockitoBean
	private WorkingHourService workingHourService;

	@MockitoBean
	private AppointmentService appointmentService;

	// Cada endpoint con @PreAuthorize y un rol que no admite, con un body válido para que nada más lo frene
	static Stream<Arguments> forbidden() {
		return Stream.of(
				Arguments.of(Role.CLIENT, get("/api/v1/users")),
				Arguments.of(Role.PROFESSIONAL, json(patch("/api/v1/users/7/active"), "{ \"active\": false }")),
				Arguments.of(Role.CLIENT, json(post("/api/v1/services"), SERVICE)),
				Arguments.of(Role.PROFESSIONAL, json(put("/api/v1/services/1"), SERVICE)),
				Arguments.of(Role.CLIENT, delete("/api/v1/services/1")),
				Arguments.of(Role.CLIENT, json(patch("/api/v1/services/1/active"), "{ \"active\": true }")),
				Arguments.of(Role.CLIENT, json(post("/api/v1/professionals"), PROFESSIONAL)),
				Arguments.of(Role.PROFESSIONAL, json(put("/api/v1/professionals/2"), "{ \"specialty\": \"Fisio\" }")),
				Arguments.of(Role.PROFESSIONAL, json(patch("/api/v1/professionals/2/active"), "{ \"active\": false }")),
				Arguments.of(Role.CLIENT, json(put("/api/v1/professionals/2/services"), "{ \"serviceIds\": [] }")),
				Arguments.of(Role.CLIENT, json(put("/api/v1/professionals/2/working-hours"), HOURS)),
				Arguments.of(Role.PROFESSIONAL, json(post("/api/v1/appointments"), BOOKING)),
				Arguments.of(Role.CLIENT, patch("/api/v1/appointments/41/confirm")),
				Arguments.of(Role.CLIENT, patch("/api/v1/appointments/41/complete")),
				Arguments.of(Role.CLIENT, patch("/api/v1/appointments/41/no-show")),
				Arguments.of(Role.PROFESSIONAL,
						json(patch("/api/v1/appointments/41/reschedule"), "{ \"startTime\": \"2026-11-06T09:30:00\" }")));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("forbidden")
	void aRoleThatThePreAuthorizeDoesNotAdmitGets403AndNeverReachesTheService(Role role,
			MockHttpServletRequestBuilder request) throws Exception {
		authenticateAs(5L, role);

		mvc.perform(request).andExpectAll(apiError(HttpStatus.FORBIDDEN, "ACCESS_DENIED"));

		verifyNoInteractions(userService, serviceOfferingService, professionalService, workingHourService,
				appointmentService);
	}

	// Control: los roles admitidos sí pasan, así que el 403 de arriba lo pone cada @PreAuthorize y no la configuración
	@Test
	void theAdmittedRolesGoThrough() throws Exception {
		when(userService.list(any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
		when(workingHourService.replace(eq(2L), any())).thenReturn(new WorkingHoursResponse(2L, List.of()));

		authenticateAs(1L, Role.ADMIN);
		mvc.perform(get("/api/v1/users")).andExpect(status().isOk());
		authenticateAs(9L, Role.PROFESSIONAL);
		mvc.perform(json(put("/api/v1/professionals/2/working-hours"), HOURS)).andExpect(status().isOk());

		verify(userService).list(any(), any(), any());
		verify(workingHourService).replace(eq(2L), any());
	}

}
