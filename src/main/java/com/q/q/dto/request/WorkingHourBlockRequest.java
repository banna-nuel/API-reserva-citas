package com.q.q.dto.request;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.q.q.validation.EndAfterStart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Un bloque del horario laboral. Día y horas llegan como texto para validar el formato exacto y devolver el
// campo con error en details: con LocalTime, Jackson aceptaría "09:00:30" y un "9:00" sería MALFORMED_REQUEST
@EndAfterStart
public record WorkingHourBlockRequest(
		@NotNull @Pattern(regexp = WorkingHourBlockRequest.DAYS,
				message = "debe ser un día de MONDAY a SUNDAY, en mayúsculas") String dayOfWeek,
		@NotNull @Pattern(regexp = WorkingHourBlockRequest.HH_MM,
				message = "debe tener el formato HH:mm, de 00:00 a 23:59") String startTime,
		@NotNull @Pattern(regexp = WorkingHourBlockRequest.HH_MM,
				message = "debe tener el formato HH:mm, de 00:00 a 23:59") String endTime) {

	public static final String DAYS = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
	public static final String HH_MM = "([01][0-9]|2[0-3]):[0-5][0-9]";            // 24 h, sin segundos

	public static boolean isDay(String value) {
		return value != null && value.matches(DAYS);
	}

	public static boolean isTime(String value) {
		return value != null && value.matches(HH_MM);
	}

	// Conversiones para usar después de validar el request
	public DayOfWeek day() {
		return DayOfWeek.valueOf(dayOfWeek);
	}

	public LocalTime start() {
		return LocalTime.parse(startTime);
	}

	public LocalTime end() {
		return LocalTime.parse(endTime);
	}

}
