package com.emirkabacali.seatlock.dto.seat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SeatGenerationRequest(@NotEmpty @Size(max = 50) List<@Valid SectionLayout> sections) {
}
