package com.q.q.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * En un bloque del horario laboral, endTime debe ser posterior a startTime (un bloque no cruza la medianoche).
 * El error se reporta en el campo endTime.
 */
@Documented
@Constraint(validatedBy = EndAfterStartValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EndAfterStart {

	String message() default "debe ser posterior a startTime";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
