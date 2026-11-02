package com.q.q.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.q.q.exception.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe el JSON de la sección 8.4 para los errores que ocurren en los filtros de seguridad,
 * antes de llegar a Spring MVC (y por tanto fuera del alcance de GlobalExceptionHandler).
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

	private final JsonMapper jsonMapper;
	private final Clock clock;

	public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
			String code, String message) throws IOException {
		ApiError body = ApiError.of(LocalDateTime.now(clock), status, code, message, request.getRequestURI(), List.of());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
