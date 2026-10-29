package com.q.q.dto.request;

import java.util.List;

import com.q.q.validation.MaxBlocksPerDay;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body de PUT /professionals/{id}/working-hours: el horario completo; lo que no venga se borra ([] lo borra todo)
public record WorkingHoursRequest(
		@NotNull @Size(max = 35) @MaxBlocksPerDay(5) List<@NotNull @Valid WorkingHourBlockRequest> blocks) {}
