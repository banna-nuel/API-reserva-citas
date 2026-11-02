package com.q.q.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Limita la longitud en bytes UTF-8 (no en caracteres). BCrypt solo usa los primeros 72 bytes:
 * "ñ" ocupa 2, así que 40 eñes pasan @Size(max = 72) pero no esta validación (RN-11).
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

	int value();

	String message() default "no puede ocupar más de {value} bytes";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
