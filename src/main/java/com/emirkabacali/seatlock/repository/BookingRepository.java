package com.emirkabacali.seatlock.repository;

import com.emirkabacali.seatlock.entity.Booking;
import com.emirkabacali.seatlock.entity.BookingStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	@EntityGraph(attributePaths = { "event", "seats" })
	Optional<Booking> findByIdAndUserId(Long id, Long userId);

	/**
	 * Only the event is fetched with the page. Join-fetching a collection (seats) would make
	 * Hibernate apply LIMIT/OFFSET in memory, so seats are loaded lazily in batches instead
	 * (see {@code hibernate.default_batch_fetch_size}).
	 */
	@EntityGraph(attributePaths = "event")
	@Query(value = "select b from Booking b where b.user.id = :userId",
			countQuery = "select count(b) from Booking b where b.user.id = :userId")
	Page<Booking> findByUserId(@Param("userId") Long userId, Pageable pageable);

	/** Number of bookings in the given states that contain the seat. */
	@Query("select count(b) from Booking b join b.seats s where s.id = :seatId and b.status in :statuses")
	long countBySeatIdAndStatusIn(@Param("seatId") Long seatId, @Param("statuses") Collection<BookingStatus> statuses);

}
