package com.q.q.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {                    // 422

	public BusinessRuleException(String code, String message) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
	}

}
