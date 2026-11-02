package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.q.q.entity.WorkingHour;

class TimeRangeTest {

	@Test
	void contiguousBlocksBecomeOneRangeWhateverTheirOrder() {
		assertThat(merge("11:00-11:30", "09:00-10:00", "10:00-11:00"))
				.containsExactly(range("09:00", "11:30"));
	}

	@Test
	void aGapKeepsTheBlocksApart() {
		assertThat(merge("14:00-18:00", "09:00-12:00", "12:00-13:00"))
				.containsExactly(range("09:00", "13:00"), range("14:00", "18:00"));
	}

	@Test
	void separateBlocksComeBackSortedAndUnchanged() {
		assertThat(merge("14:00-18:00", "09:00-12:00"))
				.containsExactly(range("09:00", "12:00"), range("14:00", "18:00"));
	}

	// El PUT del horario no los admite, pero si llegaran solapados el resultado sigue siendo su unión
	@Test
	void overlappingOrContainedBlocksAreJoined() {
		assertThat(merge("09:00-12:00", "11:00-13:00", "09:30-10:00"))
				.containsExactly(range("09:00", "13:00"));
	}

	@Test
	void noBlocksMeansNoRanges() {
		assertThat(TimeRange.merge(List.of())).isEmpty();
	}

	@Test
	void fitsOnlyWhatStaysCompletelyInsideTheRangeTheSameDay() {
		TimeRange range = range("09:00", "12:00");
		LocalDate day = LocalDate.of(2026, 11, 5);

		assertThat(range.fits(day.atTime(9, 0), day.atTime(12, 0))).isTrue();               // bordes incluidos
		assertThat(range.fits(day.atTime(8, 59), day.atTime(9, 29))).isFalse();
		assertThat(range.fits(day.atTime(11, 45), day.atTime(12, 15))).isFalse();
		// Una cita que pasa de medianoche nunca cabe, aunque el tramo llegue hasta las 23:59
		assertThat(range("22:00", "23:59").fits(day.atTime(23, 30), day.plusDays(1).atTime(0, 30))).isFalse();
	}

	@Test
	void candidateSlotsAreAnchoredToTheRangeStartAndFitCompletely() {
		LocalDate day = LocalDate.of(2026, 11, 5);

		assertThat(range("09:15", "11:00").candidateSlots(day, 45, 30))
				.extracting(slot -> slot.start().toLocalTime() + "-" + slot.end().toLocalTime())
				.containsExactly("09:15-10:00", "09:45-10:30", "10:15-11:00");
		// 23:30 + 60 min no "da la vuelta" al reloj
		assertThat(range("22:00", "23:59").candidateSlots(day, 60, 30))
				.extracting(slot -> slot.start().toLocalTime().toString())
				.containsExactly("22:00", "22:30");
	}

	// ---------- utilidades ----------

	private static List<TimeRange> merge(String... blocks) {
		return TimeRange.merge(List.of(blocks).stream().map(TimeRangeTest::block).toList());
	}

	private static WorkingHour block(String range) {
		String[] parts = range.split("-");
		WorkingHour block = new WorkingHour();
		block.setDayOfWeek(DayOfWeek.MONDAY);
		block.setStartTime(LocalTime.parse(parts[0]));
		block.setEndTime(LocalTime.parse(parts[1]));
		return block;
	}

	private static TimeRange range(String start, String end) {
		return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
	}

}
