# SeatLock

> **Status: Work in progress.** The booking flow is implemented and tested end to end:
> catalog management, JWT authentication, concurrency-safe seat booking, Redis seat holds with
> expiry and idempotent (simulated) payments. Packaging and CI are still planned — see the
> [roadmap](#roadmap).

SeatLock is an event ticket booking REST API built with Spring Boot. Its core goal is simple
to state and hard to get right:

**A seat must never be sold twice, even under heavy concurrent traffic.**

## Why this project

Ticket sales are a classic concurrency problem: when thousands of users try to buy the same
seats at the same moment, a naive implementation will happily sell one seat to several people.
SeatLock focuses on solving this correctly and demonstrating it with tests — first reproducing
the double-booking bug, then fixing it.

## How double booking is prevented

Booking a seat is a *check-then-act* operation: read the seat, check that it is `AVAILABLE`,
mark it `HELD`. If two requests run this at the same time, both can pass the check before
either writes, and both believe they got the seat.

SeatLock implements three strategies side by side (`SeatReserver`) so the difference can be
measured:

| Strategy | How it works | Outcome under contention |
|----------|--------------|--------------------------|
| `NAIVE` (demo only) | Read, check, then a bulk `UPDATE` that ignores the version column | **Double booking** |
| `OPTIMISTIC` (default) | `@Version` column; Hibernate updates with `WHERE id = ? AND version = ?`. A stale version updates 0 rows and the transaction fails | One winner, others fail fast with `409` |
| `PESSIMISTIC` | `SELECT ... FOR UPDATE` locks the seat rows (in id order to avoid deadlocks); other transactions wait, then see `HELD` | One winner, others wait and then get `409` |

`BookingConcurrencyIntegrationTest` releases 30 threads at the same moment (`CountDownLatch`)
against the same seat on a real PostgreSQL. A typical run:

```text
NAIVE        10 successful bookings for the same seat
OPTIMISTIC    1 success, 29 conflicts
PESSIMISTIC   1 success, 29 conflicts
```

(With `NAIVE`, the number of "winners" matches the connection pool size: every transaction
that reaches the database before the first commit sees the seat as available.)

Further tests cover overlapping multi-seat requests (`{1,2}`, `{2,3}`, … — no seat ever ends up
in two bookings) and the same race over HTTP (exactly one `201`, all others `409`).

### Seat holds in Redis

Before the database step, a booking request tries to hold all requested seats in Redis
(`SeatHoldService`): one key per seat, owned by a random hold token, expiring after the hold
duration (10 minutes by default). A Lua script makes this all-or-nothing and atomic. If any
seat is already held, the request is rejected with `409` **without opening a database
transaction**, which keeps load off PostgreSQL when many users hit the same seats.

Redis is an optimization, not the source of truth:

- If the database step fails, the Redis holds are released again.
- Cancelling a booking releases its holds immediately (only keys owned by its token).
- If Redis is unreachable, booking falls back to database locking only (correct, but each
  request waits for the Redis timeout of 2 s first).

### Hold expiry

Redis forgets expired holds by itself, but the database must be updated too. A scheduled job
(`BookingExpiryJob`, every 30 s) marks overdue `PENDING` bookings as `EXPIRED` and releases
their seats. It selects bookings with `FOR UPDATE SKIP LOCKED`, so rows that are being changed
by another transaction — or by the same job on another application instance — are skipped
instead of waited for. Between the end of a hold and the next job run (≤ 30 s), the seat is
still `HELD` in the database and new bookings for it get `409`.

### Idempotent payments

`POST /api/v1/bookings/{id}/pay` requires an `Idempotency-Key` header. A double-clicked button
or an automatic client retry must not charge twice:

- The booking row is locked (`SELECT ... FOR UPDATE`), so all attempts for one booking —
  including concurrent retries — run one after another.
- If the user already sent this key, the stored result is returned (same status and body) with
  `Idempotent-Replayed: true`, and nothing is charged.
- Reusing a key for a different request (other booking or payment method, detected via a
  SHA-256 request hash) returns `422`.
- A partial unique index (`UNIQUE (booking_id) WHERE status = 'SUCCEEDED'`) guarantees at the
  database level that a booking is never paid twice.

Payments are simulated (`SimulatedPaymentGateway`, similar to Stripe test cards):
`pm_card_declined` and `pm_card_insufficient_funds` fail, any other payment method succeeds.
A successful payment confirms the booking and marks its seats `BOOKED`; a failed one leaves it
`PENDING` so the user can retry with a new key until the hold expires.

`PaymentConcurrencyIntegrationTest` sends 10 simultaneous requests with the same key (one
payment row, identical responses) and with different keys (exactly one success, nine `409`s).

**Trade-offs.** Optimistic locking holds no database locks and rejects losers immediately,
which suits short transactions and moderate contention. Pessimistic locking serializes access
to hot rows, which avoids wasted work under heavy contention but holds row locks and database
connections while others wait. The strategy is configurable via `BOOKING_LOCKING_STRATEGY`.

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

## Tech stack

| Area           | Technology                                          |
|----------------|-----------------------------------------------------|
| Language       | Java 17                                             |
| Framework      | Spring Boot 4 (Web MVC, Data JPA, Validation, Security) |
| Database       | PostgreSQL, Flyway migrations                       |
| Cache / holds  | Redis                                               |
| Testing        | JUnit 5, Mockito, Spring Boot Test, Testcontainers  |
| Infrastructure | Docker, Docker Compose, GitHub Actions              |
| Docs           | Springdoc OpenAPI                                   |

## Getting started

**Prerequisites:** Java 17 and Docker.

```bash
# 1. Create your local configuration and set JWT_SECRET (at least 32 bytes)
cp .env.example .env

# 2. Start PostgreSQL (host port 5433) and Redis (host port 6379)
docker compose up -d

# 3. Run the application (Windows: .\mvnw.cmd spring-boot:run)
./mvnw spring-boot:run

# 4. Check that it is up
curl http://localhost:8080/actuator/health
```

Docker Compose reads `.env` automatically and the application imports it on startup; real
environment variables take precedence. The application refuses to start without a valid
`JWT_SECRET`. If `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set, an admin account is created on
first startup.

### Running the tests

```bash
./mvnw test
```

Integration tests use [Testcontainers](https://testcontainers.com/) to start real PostgreSQL
and Redis instances in Docker, so Docker must be running.

## API

Interactive documentation is available at `http://localhost:8080/swagger-ui.html` while the
application is running.

### Authentication

Authentication uses stateless JWT bearer tokens (HS256, 1 hour by default). Passwords are
hashed with BCrypt. Public registration creates `USER` accounts; `ADMIN` accounts are created
from configuration.

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' -d '{"email":"jane@example.com","password":"secret-password"}'

curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"jane@example.com","password":"secret-password"}'
# → {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":3600}

curl http://localhost:8080/api/v1/... -H 'Authorization: Bearer eyJ...'
```

| Access     | Endpoints                                                              |
|------------|------------------------------------------------------------------------|
| Public     | `POST /api/v1/auth/*`, all `GET` venue/event/seat endpoints, health, API docs |
| `ADMIN`    | Creating and changing venues, events and seat maps                     |
| Any user   | Everything else (requires a valid token)                               |

Missing or invalid tokens return `401`; insufficient roles return `403`.

### Implemented

| Method | Endpoint                          | Description                                              |
|--------|-----------------------------------|----------------------------------------------------------|
| POST   | `/api/v1/auth/register`           | Create a `USER` account                                  |
| POST   | `/api/v1/auth/login`              | Obtain a JWT access token                                |
| GET    | `/api/v1/venues`                  | List venues (paginated: `page`, `size`, `sort`)          |
| GET    | `/api/v1/venues/{id}`             | Get a venue                                              |
| POST   | `/api/v1/venues`                  | Create a venue                                           |
| PUT    | `/api/v1/venues/{id}`             | Update a venue                                           |
| DELETE | `/api/v1/venues/{id}`             | Delete a venue (409 if it has events)                    |
| GET    | `/api/v1/events`                  | List events (paginated, optional `status` filter)        |
| GET    | `/api/v1/events/{id}`             | Get an event                                             |
| POST   | `/api/v1/events`                  | Create an event (starts as `DRAFT`)                      |
| PUT    | `/api/v1/events/{id}`             | Update an event                                          |
| PATCH  | `/api/v1/events/{id}/status`      | Change status (`DRAFT → ON_SALE → FINISHED`, or `CANCELLED`) |
| POST   | `/api/v1/events/{id}/seats`       | Generate the seat map of a `DRAFT` event (sections × rows × seats, max 10,000) |
| GET    | `/api/v1/events/{id}/seats`       | List seats, optional `status` filter                     |
| POST   | `/api/v1/bookings`                | Hold up to 10 seats of an `ON_SALE` event (`PENDING` booking, 409 if a seat is taken) |
| GET    | `/api/v1/bookings/me`             | List the current user's bookings (paginated)             |
| GET    | `/api/v1/bookings/{id}`           | Get one of the current user's bookings                   |
| POST   | `/api/v1/bookings/{id}/pay`       | Pay for a `PENDING` booking (simulated, requires `Idempotency-Key`) |
| POST   | `/api/v1/bookings/{id}/cancel`    | Cancel a `PENDING` booking and release its seats         |

Errors use the [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem detail format.
Validation errors list every invalid field:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/venues",
  "errors": [{ "field": "name", "message": "must not be blank" }]
}
```

## Roadmap

- [x] Project skeleton, PostgreSQL via Docker Compose, Flyway
- [x] Venue / event / seat CRUD with validation and error handling
- [x] JWT authentication and role-based access
- [x] Booking with concurrency control (optimistic vs. pessimistic locking)
- [x] Redis seat holds with expiration
- [x] Idempotent payment simulation
- [ ] Testcontainers, Dockerfile, CI pipeline, full documentation

## License

This project is licensed under the [MIT License](LICENSE).
