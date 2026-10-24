package com.q.q.config;

import static org.assertj.core.api.Assertions.assertThat;

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

}
