package com.q.q.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {                        // 409

	public ConflictException(String code, String message) {
		super(HttpStatus.CONFLICT, code, message);
	}

}
