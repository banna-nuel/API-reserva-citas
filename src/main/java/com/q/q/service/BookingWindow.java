package com.q.q.service;

import java.time.LocalDateTime;

import com.q.q.config.AppProperties;

/**
 * RN-04: se puede reservar un horario que empiece entre ahora + min-notice-minutes y ahora + max-days-ahead días,
 * ambos incluidos. Lo comparten la disponibilidad y la creación de citas, así que usan los mismos límites.
 */
public record BookingWindow(LocalDateTime earliest, LocalDateTime latest) {

	public static BookingWindow at(LocalDateTime now, AppProperties.Booking booking) {
		return new BookingWindow(now.plusMinutes(booking.minNoticeMinutes()), now.plusDays(booking.maxDaysAhead()));
	}

	public boolean isTooSoon(LocalDateTime start) {
		return start.isBefore(earliest);
	}

	public boolean isTooFar(LocalDateTime start) {
		return start.isAfter(latest);
	}

	public boolean contains(LocalDateTime start) {
		return !isTooSoon(start) && !isTooFar(start);
	}

}
