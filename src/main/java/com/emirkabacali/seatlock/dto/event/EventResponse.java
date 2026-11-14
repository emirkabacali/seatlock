package com.emirkabacali.seatlock.dto.event;

import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import java.time.Instant;

public record EventResponse(
		Long id,
		Long venueId,
		String venueName,
		String name,
		String description,
		Instant startsAt,
		EventStatus status,
		Instant createdAt) {

	/** The venue must be loaded (inside a transaction or via an entity graph). */
	public static EventResponse from(Event event) {
		return new EventResponse(event.getId(), event.getVenue().getId(), event.getVenue().getName(),
				event.getName(), event.getDescription(), event.getStartsAt(), event.getStatus(),
				event.getCreatedAt());
	}

}
