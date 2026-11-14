package com.emirkabacali.seatlock.dto.venue;

import com.emirkabacali.seatlock.entity.Venue;
import java.time.Instant;

public record VenueResponse(Long id, String name, String city, String address, Instant createdAt) {

	public static VenueResponse from(Venue venue) {
		return new VenueResponse(venue.getId(), venue.getName(), venue.getCity(), venue.getAddress(),
				venue.getCreatedAt());
	}

}
