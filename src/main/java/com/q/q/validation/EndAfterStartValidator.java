package com.q.q.validation;

import com.q.q.dto.request.WorkingHourBlockRequest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EndAfterStartValidator implements ConstraintValidator<EndAfterStart, WorkingHourBlockRequest> {

	@Override
	public boolean isValid(WorkingHourBlockRequest block, ConstraintValidatorContext context) {
		if (block == null || !WorkingHourBlockRequest.isTime(block.startTime())
				|| !WorkingHourBlockRequest.isTime(block.endTime())) {
			return true;                         // un formato inválido ya lo reportan @NotNull y @Pattern
		}
		if (block.end().isAfter(block.start())) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				.addPropertyNode("endTime")
				.addConstraintViolation();
		return false;
	}

}
