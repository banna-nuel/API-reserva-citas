package com.q.q.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
		@Schema(description = "JWT para la cabecera Authorization: Bearer", example = "<jwt>") String accessToken,
		@Schema(example = "Bearer") String tokenType,
		@Schema(description = "Vigencia del token en segundos", example = "3600") long expiresInSeconds) {

	@Override
	public String toString() {                  // el token nunca debe llegar a un log
		return "AuthResponse[accessToken=****, tokenType=" + tokenType + ", expiresInSeconds=" + expiresInSeconds + "]";
	}

}
