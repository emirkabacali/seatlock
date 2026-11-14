package com.emirkabacali.seatlock.controller;

import com.emirkabacali.seatlock.dto.booking.BookingResponse;
import com.emirkabacali.seatlock.dto.booking.CreateBookingRequest;
import com.emirkabacali.seatlock.dto.common.PageResponse;
import com.emirkabacali.seatlock.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings")
public class BookingController {

	private final BookingService bookingService;

	public BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@PostMapping
	@Operation(summary = "Hold seats and create a PENDING booking")
	public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateBookingRequest request) {
		BookingResponse created = bookingService.create(userId(jwt), request);
		return ResponseEntity.created(URI.create("/api/v1/bookings/" + created.id())).body(created);
	}

	@GetMapping("/me")
	@Operation(summary = "List the current user's bookings (newest first)")
	public PageResponse<BookingResponse> mine(@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return bookingService.findMine(userId(jwt), pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get one of the current user's bookings")
	public BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.findMine(userId(jwt), id);
	}

	@PostMapping("/{id}/cancel")
	@Operation(summary = "Cancel a PENDING booking and release its seats")
	public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return bookingService.cancel(userId(jwt), id);
	}

	/** The token's subject is the user id (see TokenService). */
	private static Long userId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

}
