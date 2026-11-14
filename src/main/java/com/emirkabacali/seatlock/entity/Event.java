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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "venue_id", nullable = false)
	private Venue venue;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EventStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Required by JPA. */
	protected Event() {
	}

	public Event(Venue venue, String name, String description, Instant startsAt) {
		this.venue = venue;
		this.name = name;
		this.description = description;
		this.startsAt = startsAt;
		this.status = EventStatus.DRAFT;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public void update(Venue venue, String name, String description, Instant startsAt) {
		this.venue = venue;
		this.name = name;
		this.description = description;
		this.startsAt = startsAt;
	}

	public void changeStatus(EventStatus newStatus) {
		this.status = newStatus;
	}

	public boolean isEditable() {
		return status == EventStatus.DRAFT || status == EventStatus.ON_SALE;
	}

	public Long getId() {
		return id;
	}

	public Venue getVenue() {
		return venue;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public EventStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
