package com.q.q.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AppPropertiesTest {

	@Test
	void toStringNeverExposesSecrets() {
		AppProperties props = new AppProperties("America/Bogota",
				new AppProperties.Jwt("synthetic-jwt-secret", 60),
				new AppProperties.Booking(30, 60, 2, 60),
				new AppProperties.Admin("admin@example.com", "synthetic-admin-password"));

		assertThat(props.toString())
				.doesNotContain("synthetic-jwt-secret", "synthetic-admin-password")
				.contains("secret=****", "password=****");
	}

	// Con un paso de 0 el cálculo de disponibilidad no terminaría nunca: la aplicación no debe arrancar
	@Test
	void bookingRejectsAZeroStepAndNegativeLimits() {
		for (int[] values : new int[][] { { 0, 60, 60 }, { -30, 60, 60 }, { 30, -1, 60 }, { 30, 60, -1 } }) {
			assertThatThrownBy(() -> new AppProperties.Booking(values[0], values[1], 2, values[2]))
					.isInstanceOf(IllegalArgumentException.class);
		}
		assertThat(new AppProperties.Booking(1, 0, 0, 0).slotStepMinutes()).isEqualTo(1);
	}

}
