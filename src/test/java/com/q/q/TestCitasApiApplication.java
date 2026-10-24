package com.q.q;

import org.springframework.boot.SpringApplication;

public class TestCitasApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CitasApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
