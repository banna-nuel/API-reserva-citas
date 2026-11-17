package com.q.q;

import java.util.List;

import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import com.q.q.config.AdminBootstrap;

/**
 * Las pruebas de integración comparten un único MySQL (el contexto de Spring queda en caché con su contenedor), así
 * que lo que deja una clase lo ve la siguiente. reset() vuelve la base al estado del arranque: sin datos y solo con el
 * ADMIN inicial. Cada clase lo llama al empezar y al terminar, así el resultado no depende del orden de las clases.
 */
public class TestDatabase {

	// Hijas antes que madres, según las llaves foráneas de V1. flyway_schema_history no se toca
	private static final List<String> TABLES = List.of("appointments", "working_hours", "professional_services",
			"professionals", "service_offerings", "users");

	private final JdbcTemplate jdbc;
	private final AdminBootstrap adminBootstrap;

	public TestDatabase(JdbcTemplate jdbc, AdminBootstrap adminBootstrap) {
		this.jdbc = jdbc;
		this.adminBootstrap = adminBootstrap;
	}

	/** Borra todas las filas de la aplicación, también al ADMIN. */
	public void clean() {
		TABLES.forEach(table -> jdbc.update("delete from " + table));
	}

	/** Base limpia con solo el ADMIN inicial, creado por AdminBootstrap igual que al arrancar la aplicación. */
	public void reset() {
		clean();
		adminBootstrap.run(new DefaultApplicationArguments());
	}

}
