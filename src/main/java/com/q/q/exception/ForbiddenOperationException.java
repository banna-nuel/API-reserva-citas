package com.q.q.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends ApiException {              // 403

	public ForbiddenOperationException(String message) {
		super(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", message);
	}

}
