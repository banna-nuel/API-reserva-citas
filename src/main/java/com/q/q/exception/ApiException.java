package com.q.q.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public abstract class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final List<ApiError.FieldIssue> details;     // va tal cual al "details" del JSON de error

	protected ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, List.of());
	}

	protected ApiException(HttpStatus status, String code, String message, List<ApiError.FieldIssue> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = List.copyOf(details);
	}

}
