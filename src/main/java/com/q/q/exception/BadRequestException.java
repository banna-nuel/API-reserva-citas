package com.q.q.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {                      // 400

	public BadRequestException(String code, String message) {
		super(HttpStatus.BAD_REQUEST, code, message);
	}

}
