package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.q.q.IntegrationTest;
import com.q.q.entity.enums.AppointmentStatus;

// Las consultas de solape contra MySQL real: límites estrictos (RN-02), solo citas activas y excludeId (Fase 6b)
@IntegrationTest
class AppointmentRepositoryIntegrationTest extends AppointmentTestSupport {

	@Test
	void countOverlappingUsesStrictBoundsOnlyActiveStatusesAndTheExcludedId() throws Exception {
		Catalog catalog = catalog(30);
		Catalog other = catalog(30);
		Long ana = newClient().id();
		Long bea = newClient().id();
		Long morning = insert(catalog, ana, at(10, 0), 60, AppointmentStatus.PENDING);           // 10:00-11:00
		insert(catalog, bea, at(14, 0), 30, AppointmentStatus.CONFIRMED);                        // 14:00-14:30
		for (AppointmentStatus inactive : new AppointmentStatus[] { AppointmentStatus.CANCELLED,
				AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW }) {
			insert(catalog, bea, at(11, 0), 30, inactive);                                       // 11:00-11:30
		}
		insert(other, bea, at(9, 0), 60, AppointmentStatus.CONFIRMED);                            // de otro profesional
		Long pro = catalog.professionalId();

		assertThat(appointments.countOverlapping(pro, at(9, 0), at(10, 0), null)).isZero();      // termina cuando empieza
		assertThat(appointments.countOverlapping(pro, at(11, 0), at(11, 30), null)).isZero();    // solo inactivas
		assertThat(appointments.countOverlapping(pro, at(10, 30), at(11, 30), null)).isEqualTo(1);
		assertThat(appointments.countOverlapping(pro, at(9, 30), at(10, 15), null)).isEqualTo(1);
		assertThat(appointments.countOverlapping(pro, at(10, 15), at(10, 45), null)).isEqualTo(1);  // dentro de ella
		assertThat(appointments.countOverlapping(pro, at(9, 0), at(15, 0), null)).isEqualTo(2);
		assertThat(appointments.countOverlapping(pro, at(14, 30), at(15, 0), null)).isZero();   // empieza cuando termina
		// Al reprogramar, la propia cita no cuenta
		assertThat(appointments.countOverlapping(pro, at(10, 0), at(11, 0), morning)).isZero();
		assertThat(appointments.countOverlapping(pro, at(9, 0), at(15, 0), morning)).isEqualTo(1);
	}

	@Test
	void countOverlappingForClientCoversEveryProfessional() throws Exception {
		Catalog first = catalog(30);
		Catalog second = catalog(30);
		Long ana = newClient().id();
		Long bea = newClient().id();
		Long withFirst = insert(first, ana, at(10, 0), 60, AppointmentStatus.PENDING);           // 10:00-11:00
		insert(second, ana, at(12, 0), 30, AppointmentStatus.CONFIRMED);                         // 12:00-12:30
		insert(second, ana, at(11, 0), 60, AppointmentStatus.CANCELLED);                         // no ocupa
		insert(first, bea, at(11, 0), 60, AppointmentStatus.CONFIRMED);                          // de otra clienta

		assertThat(appointments.countOverlappingForClient(ana, at(10, 30), at(12, 15), null)).isEqualTo(2);
		assertThat(appointments.countOverlappingForClient(ana, at(11, 0), at(12, 0), null)).isZero();
		assertThat(appointments.countOverlappingForClient(ana, at(10, 30), at(12, 15), withFirst)).isEqualTo(1);
		assertThat(appointments.countOverlappingForClient(bea, at(10, 0), at(11, 0), null)).isZero();
	}

	// ---------- utilidades ----------

	private static LocalDateTime at(int hour, int minute) {
		return THURSDAY.atTime(hour, minute);
	}

}
