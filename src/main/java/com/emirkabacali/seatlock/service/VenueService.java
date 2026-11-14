package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.dto.common.PageResponse;
import com.emirkabacali.seatlock.dto.venue.VenueRequest;
import com.emirkabacali.seatlock.dto.venue.VenueResponse;
import com.emirkabacali.seatlock.entity.Venue;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.exception.ResourceNotFoundException;
import com.emirkabacali.seatlock.repository.EventRepository;
import com.emirkabacali.seatlock.repository.VenueRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VenueService {

	private final VenueRepository venueRepository;
	private final EventRepository eventRepository;

	public VenueService(VenueRepository venueRepository, EventRepository eventRepository) {
		this.venueRepository = venueRepository;
		this.eventRepository = eventRepository;
	}

	public PageResponse<VenueResponse> findAll(Pageable pageable) {
		return PageResponse.from(venueRepository.findAll(pageable), VenueResponse::from);
	}

	public VenueResponse findById(Long id) {
		return VenueResponse.from(getVenue(id));
	}

	@Transactional
	public VenueResponse create(VenueRequest request) {
		Venue venue = new Venue(request.name(), request.city(), request.address());
		return VenueResponse.from(venueRepository.save(venue));
	}

	@Transactional
	public VenueResponse update(Long id, VenueRequest request) {
		Venue venue = getVenue(id);
		// No explicit save needed: the entity is managed, so changes are flushed on commit (dirty checking).
		venue.update(request.name(), request.city(), request.address());
		return VenueResponse.from(venue);
	}

	@Transactional
	public void delete(Long id) {
		Venue venue = getVenue(id);
		if (eventRepository.existsByVenueId(id)) {
			throw new ConflictException("Venue " + id + " has events and cannot be deleted");
		}
		venueRepository.delete(venue);
	}

	Venue getVenue(Long id) {
		return venueRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venue", id));
	}

}
