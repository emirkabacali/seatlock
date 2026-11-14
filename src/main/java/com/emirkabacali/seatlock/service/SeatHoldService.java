package com.emirkabacali.seatlock.service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Temporary seat holds in Redis: one key per seat, valued with the booking's hold token and
 * expiring after the hold duration.
 *
 * <p>Redis is a fast first gate in front of the database: when a seat is already held, the
 * request is rejected without opening a database transaction. It is <b>not</b> the source of
 * truth — the database locking in {@link SeatReserver} still guarantees correctness, so the
 * application keeps working (more slowly) if Redis is unavailable.
 */
@Service
public class SeatHoldService {

	private static final Logger log = LoggerFactory.getLogger(SeatHoldService.class);

	/**
	 * All-or-nothing: if any key already exists, set nothing. Lua scripts run atomically in
	 * Redis, so no other command can run between the checks and the writes.
	 */
	private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
			for _, key in ipairs(KEYS) do
			  if redis.call('EXISTS', key) == 1 then
			    return 0
			  end
			end
			for _, key in ipairs(KEYS) do
			  redis.call('SET', key, ARGV[1], 'PX', ARGV[2])
			end
			return 1
			""", Long.class);

	/** Deletes only keys still owned by this token, never another booking's hold. */
	private static final RedisScript<Long> RELEASE = RedisScript.of("""
			local released = 0
			for _, key in ipairs(KEYS) do
			  if redis.call('GET', key) == ARGV[1] then
			    redis.call('DEL', key)
			    released = released + 1
			  end
			end
			return released
			""", Long.class);

	public enum HoldResult {
		/** All seats are now held by the given token. */
		ACQUIRED,
		/** At least one seat is already held by someone else; nothing was held. */
		REJECTED,
		/** Redis could not be reached; the caller should fall back to database-only protection. */
		UNAVAILABLE
	}

	private final StringRedisTemplate redis;

	public SeatHoldService(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public HoldResult tryHold(Long eventId, Collection<Long> seatIds, String token, Duration ttl) {
		try {
			Long result = redis.execute(ACQUIRE, keys(eventId, seatIds), token, String.valueOf(ttl.toMillis()));
			return Long.valueOf(1).equals(result) ? HoldResult.ACQUIRED : HoldResult.REJECTED;
		}
		catch (DataAccessException ex) {
			log.warn("Redis unavailable, continuing with database locking only: {}", ex.getMessage());
			return HoldResult.UNAVAILABLE;
		}
	}

	/** Best effort: a hold that cannot be released simply expires with its TTL. */
	public void release(Long eventId, Collection<Long> seatIds, String token) {
		if (token == null) {
			return;
		}
		try {
			redis.execute(RELEASE, keys(eventId, seatIds), token);
		}
		catch (DataAccessException ex) {
			log.warn("Could not release seat holds in Redis (they will expire): {}", ex.getMessage());
		}
	}

	/**
	 * The {@code {event:ID}} hash tag puts all seat keys of an event in the same Redis Cluster slot,
	 * which multi-key scripts require.
	 */
	static List<String> keys(Long eventId, Collection<Long> seatIds) {
		return seatIds.stream().sorted().map(seatId -> "seatlock:hold:{event:" + eventId + "}:seat:" + seatId).toList();
	}

}
