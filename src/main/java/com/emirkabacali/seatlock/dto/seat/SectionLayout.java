package com.emirkabacali.seatlock.dto.seat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** One section of the seat map, e.g. "A" with 10 rows of 20 seats at 150.00 each. */
public record SectionLayout(
		@NotBlank @Size(max = 20) String name,
		@Min(1) @Max(100) int rows,
		@Min(1) @Max(200) int seatsPerRow,
		@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price) {
}
