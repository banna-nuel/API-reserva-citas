package com.q.q.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.q.q.dto.request.WorkingHourBlockRequest;
import com.q.q.dto.request.WorkingHoursRequest;
import com.q.q.dto.response.WorkingHoursResponse;
import com.q.q.service.WorkingHourService;

@WebMvcTest(WorkingHourController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerSliceSupport.SliceBeans.class)
class WorkingHourControllerTest extends ControllerSliceSupport {

	private static final String HOURS = "/api/v1/professionals/2/working-hours";
	private static final WorkingHoursResponse SCHEDULE = new WorkingHoursResponse(2L,
			List.of(new WorkingHoursResponse.Block(DayOfWeek.MONDAY, "09:00", "12:00")));

	@MockitoBean
	private WorkingHourService workingHourService;

	@Test
	void getAndPutUseThePathIdAndTheBlocksOfTheBody() throws Exception {
		when(workingHourService.get(2L)).thenReturn(SCHEDULE);
		when(workingHourService.replace(eq(2L), any())).thenReturn(SCHEDULE);

		mvc.perform(get(HOURS)).andExpect(status().isOk()).andExpect(jsonPath("$.blocks[0].startTime").value("09:00"));
		mvc.perform(json(put(HOURS), """
				{ "blocks": [ { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "12:00" } ] }
				""")).andExpect(status().isOk());

		verify(workingHourService).replace(2L,
				new WorkingHoursRequest(List.of(new WorkingHourBlockRequest("MONDAY", "09:00", "12:00"))));
	}

	// Las reglas de cada bloque se prueban en WorkingHoursRequestValidationTest: aquí, que llegan al 400 con su ruta
	@Test
	void blockErrorsCarryTheirIndexedFieldPath() throws Exception {
		mvc.perform(json(put(HOURS), """
				{ "blocks": [ { "dayOfWeek": "FUNDAY", "startTime": "9:00", "endTime": "12:00" } ] }
				"""))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "blocks[0].dayOfWeek", "blocks[0].startTime"));
		mvc.perform(json(put(HOURS), "{ \"blocks\": 5 }")).andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST"));
		verifyNoInteractions(workingHourService);
	}

}
