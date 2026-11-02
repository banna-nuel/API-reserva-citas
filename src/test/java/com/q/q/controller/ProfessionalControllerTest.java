package com.q.q.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.q.q.dto.request.AssignServicesRequest;
import com.q.q.dto.request.CreateProfessionalRequest;
import com.q.q.dto.request.UpdateProfessionalRequest;
import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.ProfessionalAdminResponse;
import com.q.q.dto.response.ProfessionalResponse;
import com.q.q.service.ProfessionalService;

@WebMvcTest(ProfessionalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerSliceSupport.SliceBeans.class)
class ProfessionalControllerTest extends ControllerSliceSupport {

	private static final String PROFESSIONALS = "/api/v1/professionals";
	private static final ProfessionalAdminResponse CARLOS = new ProfessionalAdminResponse(2L, 8L, "Carlos Ruiz",
			"carlos@example.com", null, "Fisioterapia", null, true, true, List.of(), LocalDateTime.of(2026, 11, 2, 8, 0));

	@MockitoBean
	private ProfessionalService professionalService;

	@Test
	void theListBindsActiveAndServiceIdAndTheDetailThePathId() throws Exception {
		when(professionalService.list(any(), any(), any())).thenReturn(new PageResponse<>(
				List.of(new ProfessionalResponse(2L, "Carlos Ruiz", "Fisioterapia", null, List.of())), 0, 20, 1, 1));
		when(professionalService.get(2L)).thenReturn(CARLOS);

		mvc.perform(get(PROFESSIONALS).param("active", "true").param("serviceId", "3"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].fullName").value("Carlos Ruiz"));
		mvc.perform(get(PROFESSIONALS + "/2")).andExpect(status().isOk()).andExpect(jsonPath("$.userActive").value(true));

		verify(professionalService).list(eq(true), eq(3L), any());
		verify(professionalService).get(2L);
	}

	@Test
	void nonNumericFiltersAre400() throws Exception {
		mvc.perform(get(PROFESSIONALS).param("serviceId", "abc"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER"));
		verifyNoInteractions(professionalService);
	}

	@Test
	void createReturns201WithLocation() throws Exception {
		when(professionalService.create(any())).thenReturn(CARLOS);

		mvc.perform(json(post(PROFESSIONALS), """
				{ "fullName": "Carlos Ruiz", "email": "carlos@example.com", "password": "una-clave-segura",
				  "specialty": "Fisioterapia", "serviceIds": [1, 2] }
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/professionals/2")));

		verify(professionalService).create(new CreateProfessionalRequest("Carlos Ruiz", "carlos@example.com",
				"una-clave-segura", null, "Fisioterapia", null, List.of(1L, 2L)));
	}

	@Test
	void invalidProfessionalBodiesAre400() throws Exception {
		mvc.perform(json(post(PROFESSIONALS), "{ \"email\": \"no-es-email\", \"password\": \"corta\" }"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "fullName", "email", "password",
						"specialty"));
		mvc.perform(json(put(PROFESSIONALS + "/2/services"), "{ \"serviceIds\": [1, null] }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(professionalService);
	}

	@Test
	void updateActivationAndServicesUseThePathId() throws Exception {
		when(professionalService.update(eq(2L), any())).thenReturn(CARLOS);
		when(professionalService.setActive(2L, false)).thenReturn(CARLOS);
		when(professionalService.replaceServices(eq(2L), any())).thenReturn(CARLOS);

		mvc.perform(json(put(PROFESSIONALS + "/2"), "{ \"specialty\": \"Fisioterapia deportiva\" }")).andExpect(status().isOk());
		mvc.perform(json(patch(PROFESSIONALS + "/2/active"), "{ \"active\": false }")).andExpect(status().isOk());
		mvc.perform(json(put(PROFESSIONALS + "/2/services"), "{ \"serviceIds\": [3] }")).andExpect(status().isOk());

		verify(professionalService).update(2L, new UpdateProfessionalRequest("Fisioterapia deportiva", null));
		verify(professionalService).setActive(2L, false);
		verify(professionalService).replaceServices(2L, new AssignServicesRequest(List.of(3L)));
	}

}
