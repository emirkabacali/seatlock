package com.emirkabacali.seatlock.controller;

import com.emirkabacali.seatlock.dto.common.PageResponse;
import com.emirkabacali.seatlock.dto.event.EventRequest;
import com.emirkabacali.seatlock.dto.event.EventResponse;
import com.emirkabacali.seatlock.dto.event.EventStatusRequest;
import com.emirkabacali.seatlock.dto.seat.SeatGenerationRequest;
import com.emirkabacali.seatlock.dto.seat.SeatGenerationResponse;
import com.emirkabacali.seatlock.dto.seat.SeatResponse;
import com.emirkabacali.seatlock.entity.EventStatus;
import com.emirkabacali.seatlock.entity.SeatStatus;
import com.emirkabacali.seatlock.service.EventService;
import com.emirkabacali.seatlock.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events")
public class EventController {

	private final EventService eventService;
	private final SeatService seatService;

	public EventController(EventService eventService, SeatService seatService) {
		this.eventService = eventService;
		this.seatService = seatService;
	}

	@GetMapping
	@Operation(summary = "List events (paginated, optionally filtered by status)")
	public PageResponse<EventResponse> list(@RequestParam(required = false) EventStatus status,
			@PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
		return eventService.findAll(status, pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get an event")
	public EventResponse get(@PathVariable Long id) {
		return eventService.findById(id);
	}

	@PostMapping
	@Operation(summary = "Create an event (starts as DRAFT)")
	public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
		EventResponse created = eventService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/events/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update an event")
	public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
		return eventService.update(id, request);
	}

	@PatchMapping("/{id}/status")
	@Operation(summary = "Change event status (DRAFT → ON_SALE → FINISHED, or CANCELLED)")
	public EventResponse changeStatus(@PathVariable Long id, @Valid @RequestBody EventStatusRequest request) {
		return eventService.changeStatus(id, request.status());
	}

	@GetMapping("/{id}/seats")
	@Operation(summary = "List the seats of an event, optionally filtered by status")
	public List<SeatResponse> seats(@PathVariable Long id, @RequestParam(required = false) SeatStatus status) {
		return seatService.findByEvent(id, status);
	}

	@PostMapping("/{id}/seats")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Generate the seat map of a DRAFT event (sections × rows × seats)")
	public SeatGenerationResponse generateSeats(@PathVariable Long id,
			@Valid @RequestBody SeatGenerationRequest request) {
		return seatService.generate(id, request);
	}

}
