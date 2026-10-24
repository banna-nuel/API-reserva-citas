package com.q.q.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

	@Test
	void fromCopiesContentAndPageMetadata() {
		Page<String> page = new PageImpl<>(List.of("c", "d"), PageRequest.of(1, 2), 5);

		PageResponse<String> response = PageResponse.from(page);

		assertThat(response.content()).containsExactly("c", "d");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(5);
		assertThat(response.totalPages()).isEqualTo(3);
	}

}
