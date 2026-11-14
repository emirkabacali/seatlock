package com.emirkabacali.seatlock.dto.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EventRequest(
		@NotNull Long venueId,
		@NotBlank @Size(max = 200) String name,
		@Size(max = 5000) String description,
		@NotNull @Future Instant startsAt) {
}
