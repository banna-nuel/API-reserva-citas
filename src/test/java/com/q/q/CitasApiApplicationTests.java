package com.q.q;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.q.q.config.AppProperties;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CitasApiApplicationTests {

	@Autowired
	private Clock clock;

	@Autowired
	private AppProperties props;

	@Test
	void contextLoads() {
	}

	@Test
	void bindsAppPropertiesAndUsesBusinessTimezone() {
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Bogota"));
		assertThat(props.booking()).isEqualTo(new AppProperties.Booking(30, 60, 2, 60));
		assertThat(props.jwt().expirationMinutes()).isEqualTo(60);
	}

}
