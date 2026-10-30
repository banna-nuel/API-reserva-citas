package com.q.q.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.q.q.dto.response.AvailabilityResponse;
import com.q.q.service.AvailabilityService;

@WebMvcTest(AvailabilityController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerSliceSupport.SliceBeans.class)
class AvailabilityControllerTest extends ControllerSliceSupport {

	private static final String AVAILABILITY = "/api/v1/professionals/2/availability";
	private static final LocalDate DAY = LocalDate.of(2026, 11, 5);

	@MockitoBean
	private AvailabilityService availabilityService;

	@Test
	void theDateIsParsedAsIsoAndPassedWithTheIds() throws Exception {
		when(availabilityService.find(2L, DAY, 1L)).thenReturn(new AvailabilityResponse(2L, 1L, DAY, 30,
				List.of(new AvailabilityResponse.Slot(DAY.atTime(9, 0), DAY.atTime(9, 30)))));

		mvc.perform(get(AVAILABILITY).param("date", "2026-11-05").param("serviceId", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").value("2026-11-05"))
				.andExpect(jsonPath("$.slots[0].start").value("2026-11-05T09:00:00"));

		verify(availabilityService).find(2L, DAY, 1L);
	}

	@Test
	void missingOrMalformedParametersAre400() throws Exception {
		mvc.perform(get(AVAILABILITY).param("serviceId", "1"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER"));
		mvc.perform(get(AVAILABILITY).param("date", "05/11/2026").param("serviceId", "1"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER"));
		mvc.perform(get(AVAILABILITY).param("date", "2026-11-05").param("serviceId", "uno"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER"));
		verifyNoInteractions(availabilityService);
	}

}
