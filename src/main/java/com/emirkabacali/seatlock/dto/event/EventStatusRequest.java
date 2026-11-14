package com.emirkabacali.seatlock.dto.event;

import com.emirkabacali.seatlock.entity.EventStatus;
import jakarta.validation.constraints.NotNull;

public record EventStatusRequest(@NotNull EventStatus status) {
}
