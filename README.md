# SeatLock

> **Status: Work in progress.** The project is in its initial setup phase. No application
> code exists yet — everything below describes the planned scope, not current functionality.

SeatLock is an event ticket booking REST API built with Spring Boot. Its core goal is simple
to state and hard to get right:

**A seat must never be sold twice, even under heavy concurrent traffic.**

## Why this project

Ticket sales are a classic concurrency problem: when thousands of users try to buy the same
seats at the same moment, a naive implementation will happily sell one seat to several people.
SeatLock focuses on solving this correctly and demonstrating it with tests — first reproducing
the double-booking bug, then fixing it.

## Planned features

- **Authentication & authorization** — register/login with JWT, BCrypt password hashing,
  role-based access (`USER`, `ADMIN`)
- **Venue, event and seat management** — admins create venues and events and generate seat maps
  in bulk (sections × rows × seats)
- **Concurrency-safe booking** — optimistic locking (`@Version`) compared with pessimistic
  locking (`SELECT ... FOR UPDATE`), backed by tests that fire concurrent requests at the same seat
- **Temporary seat holds** — seats are held in Redis with a TTL while the user completes payment;
  expired holds release seats automatically
- **Idempotent payments (simulated)** — `Idempotency-Key` header ensures a retried request never
  charges twice
- **Consistent error handling** — a single JSON error format for validation, not-found and
  conflict errors
- **API documentation** — OpenAPI / Swagger UI

## Planned tech stack

| Area           | Technology                                          |
|----------------|-----------------------------------------------------|
| Language       | Java 17                                             |
| Framework      | Spring Boot 3 (Web, Data JPA, Validation, Security) |
| Database       | PostgreSQL, Flyway migrations                       |
| Cache / holds  | Redis                                               |
| Testing        | JUnit 5, Mockito, Spring Boot Test, Testcontainers  |
| Infrastructure | Docker, Docker Compose, GitHub Actions              |
| Docs           | Springdoc OpenAPI                                   |

## Draft API

| Method | Endpoint                          | Description                         |
|--------|-----------------------------------|-------------------------------------|
| POST   | `/api/v1/auth/register`           | Create a user account               |
| POST   | `/api/v1/auth/login`              | Obtain a JWT                        |
| GET    | `/api/v1/venues`                  | List venues                         |
| GET    | `/api/v1/events`                  | List events                         |
| GET    | `/api/v1/events/{id}/seats`       | List seats and their availability   |
| POST   | `/api/v1/bookings`                | Hold seats and create a booking     |
| GET    | `/api/v1/bookings/me`             | List the current user's bookings    |
| POST   | `/api/v1/bookings/{id}/pay`       | Pay for a booking (idempotent)      |
| POST   | `/api/v1/bookings/{id}/cancel`    | Cancel a booking                    |

The API is a draft and may change during development.

## Roadmap

- [ ] Project skeleton, PostgreSQL via Docker Compose, Flyway
- [ ] Venue / event / seat CRUD with validation and error handling
- [ ] JWT authentication and role-based access
- [ ] Booking with concurrency control (optimistic vs. pessimistic locking)
- [ ] Redis seat holds with expiration
- [ ] Idempotent payment simulation
- [ ] Testcontainers, Dockerfile, CI pipeline, full documentation

## License

This project is licensed under the [MIT License](LICENSE).
