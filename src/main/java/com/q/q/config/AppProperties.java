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

	public record Booking(int slotStepMinutes, int minNoticeMinutes, int cancelLimitHours, int maxDaysAhead) {

		public Booking {                               // falla al arrancar: con un paso de 0 la disponibilidad no terminaría
			if (slotStepMinutes <= 0 || minNoticeMinutes < 0 || maxDaysAhead < 0) {
				throw new IllegalArgumentException("app.booking: slot-step-minutes debe ser mayor que 0, "
						+ "y min-notice-minutes y max-days-ahead no pueden ser negativos");
			}
		}
	}

	public record Admin(String email, String password) {

		@Override
		public String toString() {
			return "Admin[email=" + email + ", password=****]";
		}
	}

}
