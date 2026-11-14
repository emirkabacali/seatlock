package com.emirkabacali.seatlock.dto.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateBookingRequest(@NotNull Long eventId, @NotEmpty List<@NotNull Long> seatIds) {
}
