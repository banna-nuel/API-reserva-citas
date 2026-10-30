package com.q.q.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.q.q.dto.request.ServiceOfferingRequest;
import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.ServiceOfferingResponse;
import com.q.q.service.ServiceOfferingService;

@WebMvcTest(ServiceOfferingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerSliceSupport.SliceBeans.class)
class ServiceOfferingControllerTest extends ControllerSliceSupport {

	private static final String SERVICES = "/api/v1/services";
	private static final ServiceOfferingResponse CONSULTA = new ServiceOfferingResponse(1L, "Consulta general", null, 30,
			new BigDecimal("80000.00"), true, LocalDateTime.of(2026, 11, 2, 8, 0));

	@MockitoBean
	private ServiceOfferingService serviceOfferingService;

	@Test
	void theListBindsActiveQueryAndPaging() throws Exception {
		when(serviceOfferingService.list(any(), any(), any())).thenReturn(new PageResponse<>(List.of(CONSULTA), 0, 20, 1, 1));

		mvc.perform(get(SERVICES).param("active", "false").param("q", "consulta").param("sort", "price,asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].price").value(80000.0));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(serviceOfferingService).list(eq(false), eq("consulta"), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(20);                 // por defecto
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("price"));
	}

	@Test
	void nonBooleanActiveOrNonNumericIdAre400() throws Exception {
		mvc.perform(get(SERVICES).param("active", "quizas")).andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER"));
		mvc.perform(get(SERVICES + "/abc")).andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER"));
		verifyNoInteractions(serviceOfferingService);
	}

	@Test
	void createReturns201WithLocationAndTheTrimmedBody() throws Exception {
		when(serviceOfferingService.create(any())).thenReturn(CONSULTA);

		mvc.perform(json(post(SERVICES), """
				{ "name": "  Consulta general  ", "description": "   ", "durationMinutes": 30, "price": 80000.00, "active": false }
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/services/1")));

		verify(serviceOfferingService).create(
				new ServiceOfferingRequest("Consulta general", null, 30, new BigDecimal("80000.00")));
	}

	@Test
	void invalidServiceBodiesAre400() throws Exception {
		mvc.perform(json(post(SERVICES), "{ \"name\": \"\", \"durationMinutes\": 0, \"price\": 10.999 }"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "name", "durationMinutes", "price"));
		// Un entero con decimales no se redondea (spring.jackson.deserialization.accept-float-as-int: false)
		mvc.perform(json(put(SERVICES + "/1"), "{ \"name\": \"Consulta\", \"durationMinutes\": 30.5, \"price\": 1 }"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST"));
		verifyNoInteractions(serviceOfferingService);
	}

	@Test
	void updateDeleteAndActivationCallTheServiceWithThePathId() throws Exception {
		when(serviceOfferingService.update(eq(1L), any())).thenReturn(CONSULTA);
		when(serviceOfferingService.setActive(1L, true)).thenReturn(CONSULTA);

		mvc.perform(json(put(SERVICES + "/1"), "{ \"name\": \"Consulta\", \"durationMinutes\": 45, \"price\": 1 }"))
				.andExpect(status().isOk());
		mvc.perform(delete(SERVICES + "/1")).andExpect(status().isNoContent());
		mvc.perform(json(patch(SERVICES + "/1/active"), "{ \"active\": true }")).andExpect(status().isOk());

		verify(serviceOfferingService).update(1L, new ServiceOfferingRequest("Consulta", null, 45, BigDecimal.ONE));
		verify(serviceOfferingService).deactivate(1L);
		verify(serviceOfferingService).setActive(1L, true);
	}

}
