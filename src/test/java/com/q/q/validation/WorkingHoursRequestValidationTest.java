package com.q.q.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.q.q.dto.request.WorkingHourBlockRequest;
import com.q.q.dto.request.WorkingHoursRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** Reglas de formato y límites del body de PUT /professionals/{id}/working-hours (todas dan 400). */
class WorkingHoursRequestValidationTest {

	private static final List<String> WEEK = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY",
			"SUNDAY");

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	@Test
	void theSampleScheduleAndTheEmptyScheduleAreValid() {
		List<WorkingHourBlockRequest> sample = new ArrayList<>();
		for (String day : WEEK.subList(0, 5)) {
			sample.add(block(day, "09:00", "12:00"));
			sample.add(block(day, "14:00", "18:00"));
		}
		assertThat(paths(new WorkingHoursRequest(sample))).isEmpty();
		assertThat(paths(new WorkingHoursRequest(List.of()))).isEmpty();
	}

	@Test
	void limitsAreInclusive() {
		// 5 bloques en cada uno de los 7 días = 35, de 00:00 a 23:59
		List<WorkingHourBlockRequest> full = new ArrayList<>();
		for (String day : WEEK) {
			full.addAll(fiveBlocks(day));
		}
		full.set(0, block("MONDAY", "00:00", "01:00"));
		full.set(4, block("MONDAY", "23:00", "23:59"));

		assertThat(full).hasSize(35);
		assertThat(paths(new WorkingHoursRequest(full))).isEmpty();
	}

	@Test
	void moreThanFiveBlocksOnOneDayIsRejected() {
		List<WorkingHourBlockRequest> blocks = new ArrayList<>(fiveBlocks("WEDNESDAY"));
		blocks.add(block("WEDNESDAY", "21:00", "22:00"));
		blocks.addAll(fiveBlocks("THURSDAY"));

		Set<ConstraintViolation<WorkingHoursRequest>> violations = validator.validate(new WorkingHoursRequest(blocks));
		assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("blocks");
		assertThat(violations.iterator().next().getMessage()).contains("5", "WEDNESDAY").doesNotContain("THURSDAY");
	}

	@Test
	void moreThanThirtyFiveBlocksIsRejected() {
		List<WorkingHourBlockRequest> blocks = new ArrayList<>();
		for (String day : WEEK) {
			blocks.addAll(fiveBlocks(day));
		}
		blocks.add(block("MONDAY", "23:00", "23:30"));                 // el 36: también son 6 el lunes

		assertThat(paths(new WorkingHoursRequest(blocks))).containsOnly("blocks");
	}

	@Test
	void theListAndItsElementsAreRequired() {
		assertThat(paths(new WorkingHoursRequest(null))).containsExactly("blocks");
		assertThat(paths(new WorkingHoursRequest(Arrays.asList(block("MONDAY", "09:00", "10:00"), null))))
				.hasSize(1)
				.allSatisfy(path -> assertThat(path).startsWith("blocks[1]"));
	}

	@ParameterizedTest(name = "[{index}] {0} {1}-{2} -> {3}")
	@CsvSource(nullValues = "null", value = {
			"monday,    09:00,    10:00, dayOfWeek",       // en minúsculas
			"FUNDAY,    09:00,    10:00, dayOfWeek",
			"null,      09:00,    10:00, dayOfWeek",
			"MONDAY,    9:00,     10:00, startTime",       // hora de un dígito
			"MONDAY,    09:00:30, 10:00, startTime",       // con segundos
			"MONDAY,    '',       10:00, startTime",
			"MONDAY,    null,     10:00, startTime",
			"MONDAY,    09:00,    25:00, endTime",
			"MONDAY,    09:00,    24:00, endTime",         // el máximo es 23:59
			"MONDAY,    09:00,    09:60, endTime",
			"MONDAY,    09:00,    null,  endTime",
			"MONDAY,    09:00,    09:00, endTime",         // fin igual al inicio
			"MONDAY,    18:00,    09:00, endTime" })       // fin antes del inicio
	void invalidBlockIsReportedOnItsField(String day, String start, String end, String field) {
		WorkingHoursRequest request = new WorkingHoursRequest(List.of(block("TUESDAY", "09:00", "10:00"),
				block(day, start, end)));

		assertThat(paths(request)).containsExactly("blocks[1]." + field);
	}

	private static Set<String> paths(WorkingHoursRequest request) {
		return validator.validate(request).stream()
				.map(violation -> violation.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	private static List<WorkingHourBlockRequest> fiveBlocks(String day) {
		return List.of(block(day, "08:00", "09:00"), block(day, "10:00", "11:00"), block(day, "12:00", "13:00"),
				block(day, "14:00", "15:00"), block(day, "16:00", "17:00"));
	}

	private static WorkingHourBlockRequest block(String day, String start, String end) {
		return new WorkingHourBlockRequest(day, start, end);
	}

}
