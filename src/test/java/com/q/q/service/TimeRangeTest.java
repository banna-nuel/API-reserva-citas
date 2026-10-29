package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
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
