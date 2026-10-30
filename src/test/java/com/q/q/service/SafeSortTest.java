package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.q.q.exception.BadRequestException;

class SafeSortTest {

	private static final List<String> ALLOWED = List.of("id", "fullName", "createdAt");

	@Test
	void allowedFieldsKeepTheirDirectionAndGetAnIdTieBreaker() {
		Pageable safe = SafeSort.apply(PageRequest.of(2, 15, Sort.by(Sort.Direction.DESC, "createdAt")), ALLOWED);

		assertThat(safe.getPageNumber()).isEqualTo(2);
		assertThat(safe.getPageSize()).isEqualTo(15);
		assertThat(safe.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
	}

	@Test
	void withoutSortTheOrderIsByIdAndAnExplicitIdIsNotRepeated() {
		assertThat(SafeSort.apply(PageRequest.of(0, 20), ALLOWED).getSort()).isEqualTo(Sort.by("id"));
		assertThat(SafeSort.apply(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")), ALLOWED).getSort())
				.isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
	}

	@Test
	void apiNamesAreTranslatedToEntityPaths() {
		Pageable safe = SafeSort.apply(PageRequest.of(0, 20, Sort.by("fullName")), ALLOWED,
				Map.of("fullName", "user.fullName"));

		assertThat(safe.getSort()).isEqualTo(Sort.by("user.fullName").and(Sort.by("id")));
	}

	@Test
	void anyFieldOutsideTheWhiteListIsRejected() {
		for (String field : new String[] { "passwordHash", "user.fullName", "email", "fullName;drop table users" }) {
			assertThatThrownBy(() -> SafeSort.apply(PageRequest.of(0, 20, Sort.by(field)), ALLOWED))
					.isInstanceOfSatisfying(BadRequestException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("INVALID_SORT"));
		}
	}

}
