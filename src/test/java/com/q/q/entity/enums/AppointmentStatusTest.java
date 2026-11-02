package com.q.q.entity.enums;

import static com.q.q.entity.enums.AppointmentStatus.CANCELLED;
import static com.q.q.entity.enums.AppointmentStatus.COMPLETED;
import static com.q.q.entity.enums.AppointmentStatus.CONFIRMED;
import static com.q.q.entity.enums.AppointmentStatus.NO_SHOW;
import static com.q.q.entity.enums.AppointmentStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppointmentStatusTest {

	// El diagrama de la sección 4.4 (RN-07)
	private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED = Map.of(
			PENDING, EnumSet.of(CONFIRMED, CANCELLED),
			CONFIRMED, EnumSet.of(COMPLETED, CANCELLED, NO_SHOW),
			CANCELLED, EnumSet.noneOf(AppointmentStatus.class),
			COMPLETED, EnumSet.noneOf(AppointmentStatus.class),
			NO_SHOW, EnumSet.noneOf(AppointmentStatus.class));

	// Las 25 combinaciones, incluidas las de un estado consigo mismo
	static Stream<Arguments> everyPair() {
		return Stream.of(AppointmentStatus.values())
				.flatMap(from -> Stream.of(AppointmentStatus.values()).map(to -> Arguments.of(from, to)));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("everyPair")
	void followsTheLifecycleDiagram(AppointmentStatus from, AppointmentStatus to) {
		assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.get(from).contains(to));
	}

	@Test
	void thereAreExactlyFiveValidTransitionsAndThreeFinalStates() {
		assertThat(everyPair().filter(pair -> ((AppointmentStatus) pair.get()[0])
				.canTransitionTo((AppointmentStatus) pair.get()[1]))).hasSize(5);
		assertThat(Stream.of(AppointmentStatus.values())
				.filter(status -> Stream.of(AppointmentStatus.values()).noneMatch(status::canTransitionTo)))
				.containsExactlyInAnyOrder(CANCELLED, COMPLETED, NO_SHOW);
	}

	@Test
	void onlyPendingAndConfirmedAreActive() {
		assertThat(Stream.of(AppointmentStatus.values()).filter(AppointmentStatus::isActive))
				.containsExactlyInAnyOrder(PENDING, CONFIRMED);
	}

}
