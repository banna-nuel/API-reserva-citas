package com.q.q.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Limita cuántos bloques del horario laboral puede tener un mismo día de la semana. */
@Documented
@Constraint(validatedBy = MaxBlocksPerDayValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBlocksPerDay {

	int value();

	String message() default "como máximo {value} bloques por día";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
