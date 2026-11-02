package com.q.q.validation;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.q.q.dto.request.WorkingHourBlockRequest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxBlocksPerDayValidator implements ConstraintValidator<MaxBlocksPerDay, List<WorkingHourBlockRequest>> {

	private int max;

	@Override
	public void initialize(MaxBlocksPerDay constraint) {
		this.max = constraint.value();
	}

	@Override
	public boolean isValid(List<WorkingHourBlockRequest> blocks, ConstraintValidatorContext context) {
		if (blocks == null) {
			return true;                         // la falta de la lista la reporta @NotNull
		}
		Map<DayOfWeek, Long> perDay = blocks.stream()
				.filter(Objects::nonNull)
				.map(WorkingHourBlockRequest::dayOfWeek)
				.filter(WorkingHourBlockRequest::isDay)          // un día inválido ya lo reporta @Pattern
				.collect(Collectors.groupingBy(DayOfWeek::valueOf, () -> new EnumMap<>(DayOfWeek.class),
						Collectors.counting()));
		List<String> exceeded = perDay.entrySet().stream()
				.filter(entry -> entry.getValue() > max)
				.map(entry -> entry.getKey().name())
				.toList();
		if (exceeded.isEmpty()) {
			return true;
		}
		// Solo nombres de día válidos (MONDAY…SUNDAY): es seguro añadirlos a la plantilla del mensaje
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(
				context.getDefaultConstraintMessageTemplate() + " (" + String.join(", ", exceeded) + ")")
				.addConstraintViolation();
		return false;
	}

}
