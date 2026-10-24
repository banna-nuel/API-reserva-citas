package com.q.q.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {                // 404

	public ResourceNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
	}

}
