package com.q.q.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.q.q.exception.BadRequestException;

/** Orden de los listados paginados: solo campos de una lista blanca y siempre "id" como desempate. */
final class SafeSort {

	private SafeSort() {
	}

	static Pageable apply(Pageable pageable, List<String> allowedFields) {
		return apply(pageable, allowedFields, Map.of());
	}

	// entityPaths traduce el nombre que recibe la API a la propiedad de la entidad (p. ej. "fullName" -> "user.fullName");
	// los campos que no están ahí se llaman igual en los dos lados
	static Pageable apply(Pageable pageable, List<String> allowedFields, Map<String, String> entityPaths) {
		List<Sort.Order> orders = new ArrayList<>();
		for (Sort.Order order : pageable.getSort()) {
			if (!allowedFields.contains(order.getProperty())) {
				throw new BadRequestException("INVALID_SORT", "No se puede ordenar por '" + order.getProperty()
						+ "'. Campos permitidos: " + String.join(", ", allowedFields));
			}
			orders.add(order.withProperty(entityPaths.getOrDefault(order.getProperty(), order.getProperty())));
		}
		// Con "id" al final el orden es estable entre páginas aunque haya valores repetidos
		Sort sort = Sort.by(orders);
		if (sort.getOrderFor("id") == null) {
			sort = sort.and(Sort.by("id"));
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
	}

}
