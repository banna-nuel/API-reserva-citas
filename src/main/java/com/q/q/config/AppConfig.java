package com.q.q.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

	@Bean
	Clock clock(AppProperties props) {                     // inyéctalo en vez de usar LocalDateTime.now():
		return Clock.system(ZoneId.of(props.timezone()));  // así el tiempo es controlable en los tests
	}

}
