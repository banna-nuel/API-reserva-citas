package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.q.q.dto.request.WorkingHourBlockRequest;
import com.q.q.exception.ApiError;

class WorkingHourOverlapsTest {

	@Test
	void blocksThatShareTimeOnTheSameDayOverlap() {
		assertThat(messages(block("MONDAY", "09:00", "12:00"), block("MONDAY", "11:00", "13:00")))
				.containsExactly("MONDAY: 09:00-12:00 se solapa con 11:00-13:00");
		assertThat(messages(block("MONDAY", "08:00", "17:00"), block("MONDAY", "09:00", "10:00")))   // uno dentro de otro
				.containsExactly("MONDAY: 08:00-17:00 se solapa con 09:00-10:00");
		assertThat(messages(block("MONDAY", "09:00", "10:00"), block("MONDAY", "09:00", "11:00")))   // mismo inicio
				.containsExactly("MONDAY: 09:00-10:00 se solapa con 09:00-11:00");
	}

	@Test
	void aRepeatedBlockCountsAsAnOverlap() {
		assertThat(messages(block("TUESDAY", "10:00", "11:00"), block("TUESDAY", "10:00", "11:00")))
				.containsExactly("TUESDAY: 10:00-11:00 se solapa con 10:00-11:00");
	}

	@Test
	void contiguousBlocksAreAllowed() {
		assertThat(messages(block("MONDAY", "12:00", "14:00"), block("MONDAY", "09:00", "12:00"),
				block("MONDAY", "14:00", "18:00"))).isEmpty();
	}

	@Test
	void theSameHoursOnDifferentDaysAreAllowed() {
		assertThat(messages(block("MONDAY", "09:00", "12:00"), block("TUESDAY", "09:00", "12:00"),
				block("SUNDAY", "09:00", "12:00"))).isEmpty();
	}

	@Test
	void everyConflictingPairIsReportedFromMondayToSunday() {
		List<ApiError.FieldIssue> conflicts = WorkingHourOverlaps.find(List.of(
				block("SUNDAY", "10:00", "11:00"), block("SUNDAY", "10:30", "12:00"),
				block("MONDAY", "09:00", "13:00"), block("MONDAY", "10:00", "11:00"), block("MONDAY", "12:00", "14:00")));

		assertThat(conflicts).extracting(ApiError.FieldIssue::field).containsOnly("blocks");
		assertThat(conflicts).extracting(ApiError.FieldIssue::message).containsExactly(
				"MONDAY: 09:00-13:00 se solapa con 10:00-11:00",
				"MONDAY: 09:00-13:00 se solapa con 12:00-14:00",
				"SUNDAY: 10:00-11:00 se solapa con 10:30-12:00");
	}

	@Test
	void anEmptyScheduleHasNoConflicts() {
		assertThat(WorkingHourOverlaps.find(List.of())).isEmpty();
	}

	private static List<String> messages(WorkingHourBlockRequest... blocks) {
		return WorkingHourOverlaps.find(List.of(blocks)).stream().map(ApiError.FieldIssue::message).toList();
	}

	private static WorkingHourBlockRequest block(String day, String start, String end) {
		return new WorkingHourBlockRequest(day, start, end);
	}

}
