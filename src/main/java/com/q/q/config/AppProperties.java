package com.q.q.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String timezone, Jwt jwt, Booking booking, Admin admin) {

	public record Jwt(String secret, long expirationMinutes) {

		@Override
		public String toString() {                     // el secreto nunca debe llegar a un log
			return "Jwt[secret=****, expirationMinutes=" + expirationMinutes + "]";
		}
	}

	public record Booking(int slotStepMinutes, int minNoticeMinutes, int cancelLimitHours, int maxDaysAhead) {}

	public record Admin(String email, String password) {

		@Override
		public String toString() {
			return "Admin[email=" + email + ", password=****]";
		}
	}

}
