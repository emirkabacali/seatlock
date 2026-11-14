-- Token that owns the booking's seat holds in Redis. NULL when the booking was created
-- without Redis (e.g. Redis unavailable); the database remains the source of truth.
ALTER TABLE bookings ADD COLUMN hold_token VARCHAR(36);
