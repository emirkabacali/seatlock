# SeatLock

> **Status: Work in progress.** Venue, event and seat management and JWT authentication are
> implemented and tested. Booking and the concurrency features are still planned — see the
> [roadmap](#roadmap) for what is actually implemented.

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

# 2. Start PostgreSQL (exposed on host port 5433)
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

Integration tests use [Testcontainers](https://testcontainers.com/) to start a real PostgreSQL
instance in Docker, so Docker must be running.

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

### Planned

| Method | Endpoint                          | Description                         |
|--------|-----------------------------------|-------------------------------------|
| POST   | `/api/v1/bookings`                | Hold seats and create a booking     |
| GET    | `/api/v1/bookings/me`             | List the current user's bookings    |
| POST   | `/api/v1/bookings/{id}/pay`       | Pay for a booking (idempotent)      |
| POST   | `/api/v1/bookings/{id}/cancel`    | Cancel a booking                    |

## Roadmap

- [x] Project skeleton, PostgreSQL via Docker Compose, Flyway
- [x] Venue / event / seat CRUD with validation and error handling
- [x] JWT authentication and role-based access
- [ ] Booking with concurrency control (optimistic vs. pessimistic locking)
- [ ] Redis seat holds with expiration
- [ ] Idempotent payment simulation
- [ ] Testcontainers, Dockerfile, CI pipeline, full documentation

## License

This project is licensed under the [MIT License](LICENSE).
