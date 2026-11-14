package com.emirkabacali.seatlock.entity;

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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

@Entity
@Table(name = "seats")
public class Seat {

	/**
	 * Uses a pooled sequence (allocation size 50, see V2 migration) instead of IDENTITY so that
	 * Hibernate can batch inserts when thousands of seats are generated at once.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seat_id_seq")
	@SequenceGenerator(name = "seat_id_seq", sequenceName = "seats_id_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	@Column(nullable = false, length = 20)
	private String section;

	@Column(name = "row_label", nullable = false, length = 10)
	private String rowLabel;

	@Column(name = "seat_number", nullable = false)
	private int seatNumber;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SeatStatus status;

	/** Incremented by Hibernate on every update; used for optimistic locking. */
	@Version
	@Column(nullable = false)
	private long version;

	/** Required by JPA. */
	protected Seat() {
	}

	public Seat(Event event, String section, String rowLabel, int seatNumber, BigDecimal price) {
		this.event = event;
		this.section = section;
		this.rowLabel = rowLabel;
		this.seatNumber = seatNumber;
		this.price = price;
		this.status = SeatStatus.AVAILABLE;
	}

	public boolean isAvailable() {
		return status == SeatStatus.AVAILABLE;
	}

	/** AVAILABLE → HELD while a booking is pending payment. */
	public void hold() {
		if (!isAvailable()) {
			throw new IllegalStateException("Seat " + id + " is " + status + " and cannot be held");
		}
		this.status = SeatStatus.HELD;
	}

	/** HELD → AVAILABLE when a booking is cancelled or expires. */
	public void release() {
		if (status != SeatStatus.HELD) {
			throw new IllegalStateException("Seat " + id + " is " + status + " and cannot be released");
		}
		this.status = SeatStatus.AVAILABLE;
	}

	public Long getId() {
		return id;
	}

	public Event getEvent() {
		return event;
	}

	public String getSection() {
		return section;
	}

	public String getRowLabel() {
		return rowLabel;
	}

	public int getSeatNumber() {
		return seatNumber;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public SeatStatus getStatus() {
		return status;
	}

	public long getVersion() {
		return version;
	}

}
