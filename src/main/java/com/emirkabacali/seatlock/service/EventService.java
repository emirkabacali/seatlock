package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.dto.common.PageResponse;
import com.emirkabacali.seatlock.dto.event.EventRequest;
import com.emirkabacali.seatlock.dto.event.EventResponse;
import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import com.emirkabacali.seatlock.entity.Venue;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.exception.ResourceNotFoundException;
import com.emirkabacali.seatlock.repository.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventService {

	private final EventRepository eventRepository;
	private final VenueService venueService;

	public EventService(EventRepository eventRepository, VenueService venueService) {
		this.eventRepository = eventRepository;
		this.venueService = venueService;
	}

	public PageResponse<EventResponse> findAll(EventStatus status, Pageable pageable) {
		Page<Event> events = status == null
				? eventRepository.findAllBy(pageable)
				: eventRepository.findAllByStatus(status, pageable);
		return PageResponse.from(events, EventResponse::from);
	}

	public EventResponse findById(Long id) {
		return EventResponse.from(getEvent(id));
	}

	@Transactional
	public EventResponse create(EventRequest request) {
		Venue venue = venueService.getVenue(request.venueId());
		Event event = new Event(venue, request.name(), request.description(), request.startsAt());
		return EventResponse.from(eventRepository.save(event));
	}

	@Transactional
	public EventResponse update(Long id, EventRequest request) {
		Event event = getEvent(id);
		if (!event.isEditable()) {
			throw new ConflictException("Event " + id + " is " + event.getStatus() + " and cannot be edited");
		}
		Venue venue = venueService.getVenue(request.venueId());
		event.update(venue, request.name(), request.description(), request.startsAt());
		return EventResponse.from(event);
	}

	@Transactional
	public EventResponse changeStatus(Long id, EventStatus newStatus) {
		Event event = getEvent(id);
		if (!event.getStatus().canTransitionTo(newStatus)) {
			throw new ConflictException(
					"Event " + id + " cannot change status from " + event.getStatus() + " to " + newStatus);
		}
		event.changeStatus(newStatus);
		return EventResponse.from(event);
	}

	Event getEvent(Long id) {
		return eventRepository.findWithVenueById(id).orElseThrow(() -> new ResourceNotFoundException("Event", id));
	}

}
