package com.emirkabacali.seatlock.repository;

import com.emirkabacali.seatlock.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, Long> {
}
