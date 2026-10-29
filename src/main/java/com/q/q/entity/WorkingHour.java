package com.q.q.entity;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un bloque del horario laboral semanal de un profesional; puede haber varios por día (p. ej. pausa de almuerzo)
@Entity
@Table(name = "working_hours")
@Getter @Setter @NoArgsConstructor          // evita @Data en entidades JPA
public class WorkingHour {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "professional_id")
	private Professional professional;

	@Enumerated(EnumType.STRING)                // MONDAY … SUNDAY, nunca ORDINAL
	@Column(name = "day_of_week", nullable = false, length = 10)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Override
	public String toString() {
		return "WorkingHour[id=" + id + ", " + dayOfWeek + " " + startTime + "-" + endTime + "]";
	}

}
