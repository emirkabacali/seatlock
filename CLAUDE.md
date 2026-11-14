# SeatLock — Project Instructions for Claude Code

> Personal, machine-specific notes live in `CLAUDE.local.md` (git-ignored).

## What this project is

SeatLock is a portfolio-grade **event ticket booking REST API**. Its core promise:
**a seat can never be sold twice, even under heavy concurrent traffic.**
The concurrency story (locking, seat holds, idempotency) is the heart of the project,
not an afterthought.

Repository: https://github.com/emirkabacali/seatlock (public, MIT license)

## Working rules

1. This is a learning project: work in small steps, explain the reasoning behind each change,
   and run / test after each meaningful step.
2. Everything that goes to GitHub is **in English and professional**: code, comments,
   commit messages, PR titles/descriptions, README, docs.
3. Never invent "production-ready" claims. The README must describe only what actually works.

## Git workflow

- `main` is always working. Each phase (or feature) = its own branch:
  `feat/phase-1-skeleton`, `feat/auth-jwt`, `feat/booking-locking`, etc.
- **Conventional Commits**: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`, `ci:`.
  Imperative mood, lowercase after the prefix: `feat: add seat hold endpoint`.
- One logical change per commit. Show `git diff` / explain before committing.
- Open a Pull Request with `gh pr create` for each branch (English title + description
  with a short summary and a "How to test" section), then merge with `gh pr merge --squash`.

## Tech stack & architecture decisions

- Java 17, Spring Boot 4.0.x (Web MVC, Data JPA, Validation, Security), Maven (via Maven Wrapper).
  Originally planned as 3.x; start.spring.io no longer offers 3.x. Boot 4 uses modular starters
  (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, per-module `*-test` starters)
- PostgreSQL (in Docker via `docker-compose.yml`, host port 5433 to avoid clashing with a local
  PostgreSQL install), **Flyway** for migrations
  (`spring.jpa.hibernate.ddl-auto=validate`, never `update`)
- Redis (Docker) for temporary seat holds with TTL
- Springdoc OpenAPI (Swagger UI)
- Testing: JUnit 5, Mockito, Spring Boot Test, **Testcontainers** (real PostgreSQL/Redis in tests)
- Layered architecture: `controller` → `service` → `repository` → `entity`, plus `dto`, `exception`, `config`, `security`
- Package root: `com.emirkabacali.seatlock`
- DTOs for all requests/responses; entities are never exposed directly
- Global error handling with `@RestControllerAdvice`, consistent JSON error body
- **No secrets in the repo**: DB/JWT secrets come from environment variables or a
  git-ignored `.env`; commit a `.env.example` instead
- Lombok: avoid for now (explicit constructors/getters for learning purposes); may be revisited later

## Domain model (initial design — refine together)

- **User**: id, email (unique), passwordHash, role (USER / ADMIN), createdAt
- **Venue**: id, name, city, address
- **Event**: id, venue, name, description, startsAt, status (DRAFT / ON_SALE / CANCELLED / FINISHED)
- **Seat**: id, event, section, row, number, price, status (AVAILABLE / HELD / BOOKED), `@Version`
  (unique constraint on event + section + row + number)
- **Booking**: id, user, event, seats, totalPrice,
  status (PENDING → CONFIRMED | EXPIRED | CANCELLED), expiresAt, idempotencyKey (unique), createdAt
- **Payment** (simulated): id, booking, amount, status (SUCCEEDED / FAILED), idempotencyKey

## Draft API

- `POST /api/v1/auth/register`, `POST /api/v1/auth/login` → JWT
- Venues (ADMIN write, public read): `/api/v1/venues`
- Events (ADMIN write, public read): `/api/v1/events`, `/api/v1/events/{id}/seats`
- Bookings (USER): `POST /api/v1/bookings` (hold seats), `GET /api/v1/bookings/me`,
  `POST /api/v1/bookings/{id}/pay` (requires `Idempotency-Key` header),
  `POST /api/v1/bookings/{id}/cancel`

## Progress so far

### Phase 0 — Setup & Git basics
- [x] Toolchain installed and verified (Git, Java 17, Docker Desktop on WSL 2, `gh`)
- [x] Repo created on GitHub with README, Java `.gitignore`, MIT `LICENSE`; cloned locally
- [x] README replaced with the project overview (`docs: add project overview and planned features`)
- [x] `CLAUDE.md` added to the repo; personal notes split into git-ignored `CLAUDE.local.md`

### Phase 1 — Skeleton + database
- [x] Spring Boot 4.0.9 project generated (Maven Wrapper in `only-script` mode, no wrapper jar)
- [x] `docker-compose.yml` with PostgreSQL 17 (+ named volume), `.env.example`
- [x] Flyway set up, `V1__init.sql` creates `venues`, `events`, `seats`
- [x] App starts, connects to DB, `/actuator/health` returns UP
- [x] Integration tests with Testcontainers (health + Flyway)

### Phase 2 — Venue / Event / Seat CRUD
- [x] Entities, repositories, services, controllers, DTO records, Bean Validation
- [x] Event lifecycle rules in `EventStatus.canTransitionTo` (DRAFT → ON_SALE | CANCELLED, ON_SALE → CANCELLED | FINISHED)
- [x] Bulk seat generation (DRAFT events only, once, max 10,000 seats); `seats.id` uses a pooled
      sequence (V2, allocation size 50) so Hibernate batches inserts (10k seats ≈ 1.3 s locally)
- [x] Global exception handling with RFC 9457 `ProblemDetail` (400 with field errors, 404, 409)
- [x] Unit tests (Mockito) + `CatalogApiIntegrationTest` (MockMvc + Testcontainers); `AbstractIntegrationTest` base class
- [x] Swagger UI at `/swagger-ui.html` (springdoc 3.0.3)

### Phase 3 — Security
- [x] Spring Security + JWT via `spring-boot-starter-security-oauth2-resource-server` (Nimbus, HS256);
      `TokenService` issues tokens (`sub` = user id, `roles`, `email` claims), `JwtDecoder` validates signature/expiry/issuer
- [x] `users` table (V3), BCrypt password hashes, emails normalized to lower case, constant-time-ish login (dummy hash)
- [x] Public: auth, GET catalog, health, API docs. ADMIN: catalog writes. Everything else: authenticated
- [x] First ADMIN created on startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD` (`AdminAccountInitializer`)
- [x] `JWT_SECRET` has no default; startup fails if missing or < 32 bytes. Local values come from `.env`
      via `spring.config.import: optional:file:.env[.properties]`
- [x] 401/403 delegated to `GlobalExceptionHandler` (same ProblemDetail format)
- [x] Tests: real-token `AuthSecurityIntegrationTest`; `asAdmin()` / `asUser(id)` mock-JWT helpers in `AbstractIntegrationTest`
- [x] Injectable `Clock` bean (`TimeConfig`) for time-dependent code

### Phase 4 — Booking & concurrency (the core)
- [x] `bookings` + `booking_seats` (V4); `Booking` has `@Version`; seats go AVAILABLE → HELD on booking
- [x] `SeatReserver` with three strategies (`LockingStrategy`): NAIVE (bulk update bypassing `@Version`,
      demo only, logs a warning), OPTIMISTIC (default, explicit flush → 409), PESSIMISTIC (`findForUpdate`, id order)
- [x] `BookingConcurrencyIntegrationTest`: 30 threads + `CountDownLatch`; NAIVE produced 10 bookings for one
      seat (= Hikari pool size), OPTIMISTIC/PESSIMISTIC exactly 1 success + 29 conflicts; overlapping multi-seat test
- [x] HTTP race test: exactly one 201, the rest 409
- [x] Endpoints: `POST /bookings`, `GET /bookings/me`, `GET /bookings/{id}`, `POST /bookings/{id}/cancel`;
      other users' bookings return 404; max 10 seats per booking; `@ConfigurationPropertiesScan` on the app
- [x] Test fixtures via `support/TestData` (`@TestComponent`, imported per test class)

## Remaining roadmap

### Phase 5 — Redis seat holds
- Redis in docker-compose; hold seats with TTL (e.g. 10 min) when booking starts
- Expired holds release seats automatically (TTL + scheduled cleanup job for DB state)
- Booking status PENDING → EXPIRED

### Phase 6 — Idempotent payment simulation
- `Idempotency-Key` header; same key → same response, no double charge
- Simulated payment success/failure; CONFIRMED on success

### Phase 7 — Production polish
- Testcontainers for all integration tests
- Multi-stage `Dockerfile`; whole stack runs with `docker compose up`
- GitHub Actions CI: build + test on every push/PR, status badge in README
- Professional README: what it is, architecture diagram, how concurrency is handled,
  how to run, API overview, test strategy
- Optional extras: rate limiting, structured logging, pagination, metrics (Actuator/Prometheus)

## Definition of done for each phase

- Code compiles, all tests pass (`.\mvnw.cmd test`)
- Merged to `main` via PR
- README updated if behaviour changed
