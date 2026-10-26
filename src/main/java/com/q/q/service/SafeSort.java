package com.q.q.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.q.q.exception.BadRequestException;

/** Orden de los listados paginados: solo campos de una lista blanca y siempre "id" como desempate. */
final class SafeSort {

	private SafeSort() {
	}

	static Pageable apply(Pageable pageable, List<String> allowedFields) {
		for (Sort.Order order : pageable.getSort()) {
			if (!allowedFields.contains(order.getProperty())) {
				throw new BadRequestException("INVALID_SORT", "No se puede ordenar por '" + order.getProperty()
						+ "'. Campos permitidos: " + String.join(", ", allowedFields));
			}
		}
		// Con "id" al final el orden es estable entre páginas aunque haya valores repetidos
		Sort sort = pageable.getSort().getOrderFor("id") == null
				? pageable.getSort().and(Sort.by("id"))
				: pageable.getSort();
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
	}

}
