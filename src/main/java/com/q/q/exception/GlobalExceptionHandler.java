package com.q.q.exception;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

	private final Clock clock;

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
		return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req, List.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
		List<ApiError.FieldIssue> details = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> new ApiError.FieldIssue(e.getField(), e.getDefaultMessage()))
				.toList();
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Hay campos inválidos", req, details);
	}

	// Restricciones directas sobre @RequestParam / @PathVariable (p. ej. @Min): validación nativa de Spring MVC
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
		List<ApiError.FieldIssue> details = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(e -> new ApiError.FieldIssue(
								e instanceof FieldError fe ? fe.getField() : result.getMethodParameter().getParameterName(),
								e.getDefaultMessage())))
				.toList();
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Hay campos inválidos", req, details);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleNotReadable(HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
				"El cuerpo de la petición falta o no es un JSON válido", req, List.of());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
				"El parámetro '" + ex.getName() + "' tiene un formato inválido", req, List.of());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
			HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
				"Falta el parámetro obligatorio '" + ex.getParameterName() + "'", req, List.of());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest req) {
		return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
				"El método " + ex.getMethod() + " no está permitido en esta ruta", req, List.of(), ex.getHeaders());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNoResource(HttpServletRequest req) {
		return build(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", "La ruta solicitada no existe", req, List.of());
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiError> handleAuth(HttpServletRequest req) {
		return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenciales inválidas", req, List.of());
	}

	// Imprescindible: @PreAuthorize lanza esta excepción DENTRO del controlador y,
	// sin este handler, el genérico de abajo la convertiría en un 500.
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleDenied(HttpServletRequest req) {
		return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tienes permisos para esta acción", req, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
		// Cualquier otra excepción 4xx de Spring MVC (415, 406...) conserva su código HTTP en lugar de volverse 500
		if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
			HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
			return build(status, status.name(), "La petición no se puede procesar", req, List.of(),
					errorResponse.getHeaders());
		}
		log.error("Error no controlado en {}", req.getRequestURI(), ex);   // el stacktrace solo va al log
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor", req, List.of());
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
			HttpServletRequest req, List<ApiError.FieldIssue> details) {
		return build(status, code, message, req, details, HttpHeaders.EMPTY);
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
			HttpServletRequest req, List<ApiError.FieldIssue> details, HttpHeaders headers) {
		ApiError body = ApiError.of(LocalDateTime.now(clock), status, code, message, req.getRequestURI(), details);
		return ResponseEntity.status(status).headers(headers).body(body);
	}

}
