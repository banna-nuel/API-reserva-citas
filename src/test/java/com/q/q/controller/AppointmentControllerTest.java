package com.q.q.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.q.q.dto.request.CreateAppointmentRequest;
import com.q.q.dto.request.RescheduleAppointmentRequest;
import com.q.q.dto.response.AppointmentResponse;
import com.q.q.dto.response.PageResponse;
import com.q.q.entity.enums.AppointmentStatus;
import com.q.q.service.AppointmentService;

@WebMvcTest(AppointmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerSliceSupport.SliceBeans.class)
class AppointmentControllerTest extends ControllerSliceSupport {

	private static final String APPOINTMENTS = "/api/v1/appointments";
	private static final LocalDateTime START = LocalDateTime.of(2026, 11, 5, 10, 0);
	private static final AppointmentResponse APPOINTMENT = new AppointmentResponse(41L,
			new AppointmentResponse.ClientSummary(7L, "Ana Pérez"),
			new AppointmentResponse.ProfessionalSummary(2L, "Carlos Ruiz"),
			new AppointmentResponse.ServiceSummary(1L, "Consulta general", 30), START, START.plusMinutes(30),
			AppointmentStatus.PENDING, new BigDecimal("80000.00"), null, null, LocalDateTime.of(2026, 11, 2, 8, 0));

	@MockitoBean
	private AppointmentService appointmentService;

	@Test
	void createReturns201WithLocationAndTheParsedBody() throws Exception {
		when(appointmentService.create(any())).thenReturn(APPOINTMENT);

		mvc.perform(json(post(APPOINTMENTS), """
				{ "professionalId": 2, "serviceId": 1, "startTime": "2026-11-05T10:00:00", "notes": "  Primera visita  ",
				  "clientId": 9, "price": 1 }
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/appointments/41")))
				.andExpect(jsonPath("$.price").value(80000.0));

		// notes llega recortada; price no forma parte del body; clientId llega y el servicio decide si lo usa
		verify(appointmentService).create(new CreateAppointmentRequest(2L, 1L, START, "Primera visita", 9L));
	}

	@Test
	void invalidBookingBodiesAre400() throws Exception {
		mvc.perform(json(post(APPOINTMENTS), "{ \"notes\": \"" + "x".repeat(501) + "\" }"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "professionalId", "serviceId",
						"startTime", "notes"));
		// Con zona: Jackson quitaría la Z y tomaría una hora UTC como local
		mvc.perform(json(post(APPOINTMENTS), "{ \"professionalId\": 2, \"serviceId\": 1, \"startTime\": \"2026-11-05T10:00:00Z\" }"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST"));
		verifyNoInteractions(appointmentService);
	}

	@Test
	void theListBindsEveryFilterAndPaging() throws Exception {
		when(appointmentService.list(any(), any(), any(), any(), any()))
				.thenReturn(new PageResponse<>(List.of(APPOINTMENT), 0, 10, 1, 1));

		mvc.perform(get(APPOINTMENTS).param("status", "CONFIRMED").param("from", "2026-11-01").param("to", "2026-11-30")
				.param("professionalId", "2").param("size", "10").param("sort", "startTime,desc"))
				.andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(appointmentService).list(eq(AppointmentStatus.CONFIRMED), eq(LocalDate.of(2026, 11, 1)),
				eq(LocalDate.of(2026, 11, 30)), eq(2L), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "startTime"));
	}

	@Test
	void malformedFiltersAre400() throws Exception {
		for (String[] param : new String[][] { { "status", "confirmed" }, { "from", "2026-13-01" }, { "professionalId", "dos" } }) {
			mvc.perform(get(APPOINTMENTS).param(param[0], param[1]))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER"));
		}
		verifyNoInteractions(appointmentService);
	}

	@Test
	void eachStatusChangeCallsItsServiceMethodWithThePathId() throws Exception {
		when(appointmentService.get(41L)).thenReturn(APPOINTMENT);
		when(appointmentService.confirm(41L)).thenReturn(APPOINTMENT);
		when(appointmentService.complete(41L)).thenReturn(APPOINTMENT);
		when(appointmentService.markNoShow(41L)).thenReturn(APPOINTMENT);

		mvc.perform(get(APPOINTMENTS + "/41")).andExpect(status().isOk());
		mvc.perform(patch(APPOINTMENTS + "/41/confirm")).andExpect(status().isOk());
		mvc.perform(patch(APPOINTMENTS + "/41/complete")).andExpect(status().isOk());
		mvc.perform(patch(APPOINTMENTS + "/41/no-show")).andExpect(status().isOk());

		verify(appointmentService).get(41L);
		verify(appointmentService).confirm(41L);
		verify(appointmentService).complete(41L);
		verify(appointmentService).markNoShow(41L);
	}

	@Test
	void cancelTakesAnOptionalTrimmedReason() throws Exception {
		when(appointmentService.cancel(eq(41L), any())).thenReturn(APPOINTMENT);
		when(appointmentService.cancel(eq(42L), any())).thenReturn(APPOINTMENT);
		when(appointmentService.cancel(eq(43L), any())).thenReturn(APPOINTMENT);

		mvc.perform(patch(APPOINTMENTS + "/41/cancel")).andExpect(status().isOk());
		mvc.perform(json(patch(APPOINTMENTS + "/42/cancel"), "{ \"reason\": \"  No puedo asistir  \" }")).andExpect(status().isOk());
		mvc.perform(json(patch(APPOINTMENTS + "/43/cancel"), "{ \"reason\": \"   \" }")).andExpect(status().isOk());
		mvc.perform(json(patch(APPOINTMENTS + "/44/cancel"), "{ \"reason\": \"" + "x".repeat(256) + "\" }"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reason"));

		verify(appointmentService).cancel(41L, null);
		verify(appointmentService).cancel(42L, "No puedo asistir");
		verify(appointmentService).cancel(43L, null);
	}

	@Test
	void rescheduleNeedsABodyWithTheNewStart() throws Exception {
		when(appointmentService.reschedule(eq(41L), any())).thenReturn(APPOINTMENT);

		mvc.perform(json(patch(APPOINTMENTS + "/41/reschedule"), "{ \"startTime\": \"2026-11-06T09:30:00\" }"))
				.andExpect(status().isOk());
		mvc.perform(patch(APPOINTMENTS + "/41/reschedule")).andExpectAll(apiError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST"));
		mvc.perform(json(patch(APPOINTMENTS + "/41/reschedule"), "{}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "startTime"));

		verify(appointmentService).reschedule(41L, new RescheduleAppointmentRequest(LocalDateTime.of(2026, 11, 6, 9, 30)));
	}

}
