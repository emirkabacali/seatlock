package com.emirkabacali.seatlock.repository;

import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

	// The entity graph loads the venue in the same query and avoids the N+1 problem.
	@EntityGraph(attributePaths = "venue")
	Page<Event> findAllBy(Pageable pageable);

	@EntityGraph(attributePaths = "venue")
	Page<Event> findAllByStatus(EventStatus status, Pageable pageable);

	@EntityGraph(attributePaths = "venue")
	Optional<Event> findWithVenueById(Long id);

	boolean existsByVenueId(Long venueId);

}
