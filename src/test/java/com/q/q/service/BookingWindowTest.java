package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.q.q.config.AppProperties;

// BookingWindow (RN-04) y TimeSlot (RN-02): las piezas que comparten la disponibilidad y la reserva
class BookingWindowTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
	private static final BookingWindow WINDOW = BookingWindow.at(NOW, new AppProperties.Booking(30, 60, 2, 60));

	@Test
	void bothLimitsAreIncluded() {
		assertThat(WINDOW.earliest()).isEqualTo(NOW.plusMinutes(60));
		assertThat(WINDOW.latest()).isEqualTo(NOW.plusDays(60));
		assertThat(WINDOW.contains(NOW.plusMinutes(60))).isTrue();
		assertThat(WINDOW.contains(NOW.plusDays(60))).isTrue();
	}

	@Test
	void oneMinuteOutsideEitherLimitIsTooSoonOrTooFar() {
		assertThat(WINDOW.isTooSoon(NOW.plusMinutes(59))).isTrue();
		assertThat(WINDOW.isTooFar(NOW.plusMinutes(59))).isFalse();
		assertThat(WINDOW.isTooFar(NOW.plusDays(60).plusMinutes(1))).isTrue();
		assertThat(WINDOW.isTooSoon(NOW.plusDays(60).plusMinutes(1))).isFalse();
		assertThat(WINDOW.contains(LocalDateTime.MIN)).isFalse();
		assertThat(WINDOW.contains(LocalDateTime.MAX)).isFalse();
	}

	@Test
	void slotsOverlapOnlyWhenTheyShareTime() {
		TimeSlot slot = new TimeSlot(NOW.withHour(10), NOW.withHour(11));

		assertThat(slot.overlaps(NOW.withHour(10).plusMinutes(30), NOW.withHour(11).plusMinutes(30))).isTrue();
		assertThat(slot.overlaps(NOW.withHour(10).plusMinutes(15), NOW.withHour(10).plusMinutes(45))).isTrue();
		assertThat(slot.overlaps(NOW.withHour(9), NOW.withHour(12))).isTrue();
		// Solo tocarse no es solaparse: uno termina cuando empieza el otro
		assertThat(slot.overlaps(NOW.withHour(9), NOW.withHour(10))).isFalse();
		assertThat(slot.overlaps(NOW.withHour(11), NOW.withHour(12))).isFalse();
	}

}
