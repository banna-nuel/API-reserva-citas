package com.q.q.dto.response;

import java.time.DayOfWeek;
import java.util.List;

// Horario laboral de un profesional; el mismo formato sirve de body para el PUT
public record WorkingHoursResponse(Long professionalId, List<Block> blocks) {

	public record Block(DayOfWeek dayOfWeek, String startTime, String endTime) {}       // horas en HH:mm

}
