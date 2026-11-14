package com.emirkabacali.seatlock.controller;

import com.emirkabacali.seatlock.dto.common.PageResponse;
import com.emirkabacali.seatlock.dto.venue.VenueRequest;
import com.emirkabacali.seatlock.dto.venue.VenueResponse;
import com.emirkabacali.seatlock.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/venues")
@Tag(name = "Venues")
public class VenueController {

	private final VenueService venueService;

	public VenueController(VenueService venueService) {
		this.venueService = venueService;
	}

	@GetMapping
	@Operation(summary = "List venues (paginated)")
	public PageResponse<VenueResponse> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return venueService.findAll(pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a venue")
	public VenueResponse get(@PathVariable Long id) {
		return venueService.findById(id);
	}

	@PostMapping
	@Operation(summary = "Create a venue")
	public ResponseEntity<VenueResponse> create(@Valid @RequestBody VenueRequest request) {
		VenueResponse created = venueService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/venues/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a venue")
	public VenueResponse update(@PathVariable Long id, @Valid @RequestBody VenueRequest request) {
		return venueService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a venue without events")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		venueService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
