package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.q.q.IntegrationTest;
import com.q.q.entity.WorkingHour;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.WorkingHourRepository;

// WorkingHourRepository contra MySQL real: el reemplazo de bloques que hace el PUT del horario (borrar y guardar en
// una transacción) y la consulta por día que usa la disponibilidad. Cada catálogo nace con 10 bloques (12.3)
@IntegrationTest
class WorkingHourRepositoryIntegrationTest extends AppointmentTestSupport {

	@Autowired
	private WorkingHourRepository workingHours;

	@Autowired
	private ProfessionalRepository professionals;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void replacingTheBlocksOfOneProfessionalLeavesTheOthersAlone() throws Exception {
		Catalog mine = catalog(30);
		Catalog other = catalog(30);

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			workingHours.deleteByProfessionalId(mine.professionalId());
			workingHours.saveAll(List.of(block(mine, DayOfWeek.SATURDAY, "08:00", "12:00"),
					block(mine, DayOfWeek.SUNDAY, "09:00", "10:00")));
		});

		assertThat(workingHours.findByProfessionalId(mine.professionalId())).extracting(WorkingHour::getDayOfWeek)
				.containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
		assertThat(workingHours.findByProfessionalId(other.professionalId())).hasSize(10);
		assertThat(workingHours.findByProfessionalIdAndDayOfWeek(mine.professionalId(), DayOfWeek.SATURDAY))
				.singleElement().extracting(WorkingHour::getStartTime).isEqualTo(LocalTime.of(8, 0));
		assertThat(workingHours.findByProfessionalIdAndDayOfWeek(mine.professionalId(), DayOfWeek.MONDAY)).isEmpty();
		assertThat(workingHours.findByProfessionalIdAndDayOfWeek(other.professionalId(), DayOfWeek.MONDAY)).hasSize(2);
	}

	// ck_wh_range (fin > inicio) frena un bloque inválido y la transacción entera se deshace: el horario anterior sigue
	@Test
	void aFailedReplacementKeepsThePreviousBlocks() throws Exception {
		Catalog mine = catalog(30);

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			workingHours.deleteByProfessionalId(mine.professionalId());
			workingHours.saveAllAndFlush(List.of(block(mine, DayOfWeek.MONDAY, "12:00", "09:00")));
		})).isInstanceOf(DataIntegrityViolationException.class);

		assertThat(workingHours.findByProfessionalId(mine.professionalId())).hasSize(10);
	}

	private WorkingHour block(Catalog catalog, DayOfWeek day, String start, String end) {
		WorkingHour block = new WorkingHour();
		block.setProfessional(professionals.getReferenceById(catalog.professionalId()));
		block.setDayOfWeek(day);
		block.setStartTime(LocalTime.parse(start));
		block.setEndTime(LocalTime.parse(end));
		return block;
	}

}
