package com.q.q.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {                    // 422

	public BusinessRuleException(String code, String message) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
	}

	public BusinessRuleException(String code, String message, List<ApiError.FieldIssue> details) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, code, message, details);
	}

}
