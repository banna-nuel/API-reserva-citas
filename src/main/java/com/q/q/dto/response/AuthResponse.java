package com.q.q.dto.response;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {

	@Override
	public String toString() {                  // el token nunca debe llegar a un log
		return "AuthResponse[accessToken=****, tokenType=" + tokenType + ", expiresInSeconds=" + expiresInSeconds + "]";
	}

}
