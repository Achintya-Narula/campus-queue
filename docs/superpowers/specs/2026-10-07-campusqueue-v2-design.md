# CampusQueue v2 Design Specification

**Date:** 2026-10-07  
**Status:** Proposed for implementation  
**Repository:** `Achintya-Narula/campus-queue`  
**Primary target:** Entry-level software engineering and Java backend roles

## 1. Purpose

CampusQueue v2 upgrades the existing in-memory Java 17 workshop-registration API into a production-style Spring Boot and PostgreSQL backend. The project must remain small enough to understand completely while demonstrating the backend skills commonly expected in internship and graduate hiring: REST API design, relational data modelling, authentication, authorization, transactions, concurrency control, validation, testing, containerization, and CI.

The existing JDK `HttpServer` version remains available through Git history. Version 2 is an intentional rewrite and learning exercise, not a claim that the original implementation used Spring Boot or PostgreSQL.

## 2. Goals

- Build a Java 17 Spring Boot REST API backed by PostgreSQL.
- Support student and organizer accounts with stateless JWT authentication.
- Allow organizers to create, edit, publish, and cancel workshops.
- Allow students to browse published workshops, register, cancel, and view their registrations.
- Enforce workshop capacity under concurrent registration requests.
- Maintain a deterministic FIFO waitlist and promote the earliest waiting student after a confirmed cancellation.
- Prevent duplicate active registrations and cross-user data access.
- Provide database migrations, validation, structured errors, API documentation, automated tests, Docker Compose, and GitHub Actions.
- Produce only resume claims that are supported by code and repeatable verification.

## 3. Non-goals

The first release will not include:

- A web or mobile frontend.
- Email, SMS, or push notifications.
- Payments, attendance scanning, certificates, or calendar integrations.
- Redis, message queues, microservices, or distributed locking.
- Social login, email verification, password reset, or administrator accounts.
- Cloud deployment or production monitoring.
- AI features.

These are possible later enhancements, not MVP claims.

## 4. Technology decisions

- Java 17.
- Spring Boot 3.x; the exact supported patch version will be selected during implementation.
- Maven Wrapper for reproducible builds.
- Spring Web for REST endpoints.
- Spring Security for authentication and role-based authorization.
- BCrypt for password hashing.
- Signed, expiring JWT access tokens; the signing secret is supplied through environment configuration.
- Spring Data JPA/Hibernate for persistence.
- PostgreSQL as the only application database.
- Flyway for versioned schema migrations.
- Bean Validation for request validation.
- Springdoc OpenAPI for interactive API documentation.
- JUnit 5, Spring Boot Test, MockMvc, and Testcontainers PostgreSQL for testing.
- Dockerfile and Docker Compose for local application/database startup.
- GitHub Actions for build and test verification.

H2 will not substitute for PostgreSQL in integration tests because database locking and constraint behaviour are part of the project.

## 5. Architecture

The application uses a conventional layered structure:

1. Controllers accept and validate HTTP requests and map domain results to responses.
2. Spring Security authenticates JWTs and enforces coarse role rules.
3. Services enforce ownership, lifecycle, registration, capacity, and waitlist rules.
4. Spring Data repositories provide persistence and explicit locking queries.
5. PostgreSQL enforces durable constraints and transaction boundaries.

Suggested package layout:

```text
com.achintya.campusqueue
├── auth
├── user
├── workshop
├── registration
├── common
│   ├── config
│   ├── error
│   └── security
└── CampusQueueApplication
```

Controllers must not contain registration or promotion logic. Entities must not be returned directly from API endpoints; request and response DTOs define the public contract.

## 6. Domain model

### User

- `id: UUID`
- `email: String`, normalized to lowercase and unique
- `passwordHash: String`
- `role: STUDENT | ORGANIZER`
- `createdAt: Instant`

Rules:

- Email and password are required and validated.
- Public responses never expose `passwordHash`.
- The first version permits self-registration as either student or organizer and documents that this is a demo simplification.

### Workshop

- `id: UUID`
- `organizerId: UUID`
- `title: String`
- `description: String`
- `capacity: int`
- `status: DRAFT | PUBLISHED | CANCELLED`
- `nextWaitlistSequence: long`
- `createdAt: Instant`
- `updatedAt: Instant`

Rules:

- Capacity must be positive.
- Only the owning organizer can edit, publish, cancel, or inspect the roster.
- Students can register only for published workshops.
- Capacity cannot be reduced below the number of currently confirmed registrations.
- A cancelled workshop cannot be republished or accept registrations.

### Registration

- `id: UUID`
- `workshopId: UUID`
- `studentId: UUID`
- `status: CONFIRMED | WAITLISTED | CANCELLED`
- `waitlistSequence: Long`, present only when waitlisted
- `createdAt: Instant`
- `updatedAt: Instant`

Database constraints and indexes:

- Unique constraint on `(workshop_id, student_id)`.
- Index on `(workshop_id, status)`.
- Index on `(workshop_id, status, waitlist_sequence)`.
- Foreign keys from workshops to organizers and registrations to workshops/users.
- Check constraints for positive capacity and valid waitlist sequence where practical.

A student who cancelled may register again by reactivating the existing workshop/student record. Reactivation receives a new status, timestamp, and waitlist sequence when applicable. The v2 model keeps one current registration record per student/workshop pair; a full audit-event history is out of scope.

## 7. Workshop lifecycle

- New workshops start as `DRAFT`.
- The owning organizer may edit a draft.
- Publishing changes `DRAFT` to `PUBLISHED`.
- The owning organizer may update allowed descriptive fields on a published workshop, subject to capacity rules.
- Cancelling changes the workshop to `CANCELLED` and marks its active registrations `CANCELLED` in the same transaction.
- Invalid lifecycle transitions return a conflict response.

## 8. Registration and concurrency algorithm

Every operation that can alter capacity or queue order must run inside a database transaction and acquire a pessimistic write lock on the relevant workshop row.

### Register

1. Authenticate a student.
2. Load and lock the workshop row using `SELECT ... FOR UPDATE` semantics.
3. Confirm that the workshop is published.
4. Load any existing registration for the student/workshop pair.
5. Reject an existing `CONFIRMED` or `WAITLISTED` registration as a duplicate.
6. Count confirmed registrations while the workshop lock is held.
7. If confirmed count is below capacity, create or reactivate the registration as `CONFIRMED`.
8. Otherwise increment `nextWaitlistSequence` and create or reactivate it as `WAITLISTED` with that sequence.
9. Commit the transaction and return the assigned status.

### Cancel student registration

1. Authenticate the student.
2. Load and lock the workshop row.
3. Find the student's active registration.
4. If it is `WAITLISTED`, mark it `CANCELLED`; no promotion occurs.
5. If it is `CONFIRMED`, mark it `CANCELLED`, then select the active waitlisted registration with the smallest sequence.
6. Promote that record to `CONFIRMED` and clear its waitlist sequence.
7. Commit the cancellation and promotion atomically.

### Why the workshop row is locked

All registration, cancellation, promotion, and capacity-changing paths serialize on one workshop row. This prevents two requests from simultaneously observing the same free seat or assigning conflicting queue order. It is intentionally simple and correct for a single-database portfolio application. Distributed scaling is outside scope and will be documented as a limitation.

## 9. API contract

Base path: `/api/v1`

### Authentication

- `POST /auth/register`
- `POST /auth/login`

Successful login returns a bearer token and basic user information. Tokens expire; refresh tokens are out of scope.

### Public workshop access

- `GET /workshops` — list published workshops with optional text/status-safe filters and pagination
- `GET /workshops/{workshopId}` — view a published workshop

Public responses may include capacity and availability summaries but not student identities.

### Organizer operations

- `POST /organizer/workshops`
- `PUT /organizer/workshops/{workshopId}`
- `POST /organizer/workshops/{workshopId}/publish`
- `POST /organizer/workshops/{workshopId}/cancel`
- `GET /organizer/workshops`
- `GET /organizer/workshops/{workshopId}/registrations`

The roster response orders confirmed registrations first and waitlisted registrations by FIFO sequence.

### Student operations

- `POST /workshops/{workshopId}/registrations`
- `DELETE /workshops/{workshopId}/registrations/me`
- `GET /me/registrations`

Registration responses clearly state `CONFIRMED` or `WAITLISTED`; waitlisted responses include their current queue position when it can be calculated safely.

## 10. Security and authorization

- Passwords are stored only as BCrypt hashes.
- JWT signing secrets and database credentials are never committed.
- JWTs include the user identifier and role and use a configured expiration.
- Endpoint rules enforce student and organizer roles.
- Service-layer ownership checks ensure an organizer cannot edit or view another organizer's private roster.
- Student identity comes from the authenticated principal, never from a client-supplied student ID.
- Validation limits string lengths and rejects malformed input.
- CORS is configured explicitly for documented local origins only if needed.
- Logs must not include passwords, raw tokens, or secrets.

## 11. Error handling

A global exception handler returns a stable JSON problem format containing:

- HTTP status
- machine-readable error code
- human-readable message
- request path
- timestamp
- field validation errors when relevant

Expected mappings include:

- `400 Bad Request` — malformed or invalid input
- `401 Unauthorized` — missing or invalid authentication
- `403 Forbidden` — incorrect role or ownership
- `404 Not Found` — missing resource
- `409 Conflict` — duplicate registration, invalid lifecycle transition, or forbidden capacity reduction

Stack traces and internal exception messages are not exposed to clients.

## 12. Database migrations

Flyway migrations create tables, constraints, and indexes. Hibernate schema generation is disabled outside test-only validation; the application validates that entities match the migrated schema.

Seed data is optional and must be isolated to a local development profile. Tests create their own data and cannot depend on seed order.

## 13. Testing strategy

### Unit tests

- Workshop lifecycle rules.
- Registration decision when capacity is available or full.
- Duplicate and reactivation behaviour.
- Cancellation and promotion selection.
- Ownership and role failures.
- Validation and error mapping where isolated testing adds value.

### PostgreSQL integration tests

Testcontainers provides a real PostgreSQL database for:

- Flyway migration startup.
- Repository constraints and locking queries.
- Authentication and owner-scoped API flows.
- Duplicate-registration conflicts.
- Capacity reduction rejection.
- Workshop cancellation.
- Confirmed cancellation with FIFO promotion.
- Waitlisted cancellation without promotion.

### Concurrency acceptance test

A repeatable test creates a published workshop with capacity 3, then submits 20 distinct student registrations from 8 worker threads. After all transactions complete:

- exactly 3 registrations are `CONFIRMED`;
- exactly 17 are `WAITLISTED`;
- waitlist sequences are unique and ordered;
- no request creates a duplicate student/workshop record;
- cancelling one confirmed registration promotes exactly the earliest active waitlisted registration;
- confirmed registrations never exceed capacity.

The precise resume test count is recorded only after `./mvnw verify` passes. The README may describe the scenario, but no benchmark or scalability claim will be inferred from it.

## 14. Developer experience and documentation

The repository will contain:

- Maven Wrapper.
- `.env.example` without secrets.
- Dockerfile and `compose.yaml`.
- OpenAPI/Swagger endpoint.
- README with architecture, entity relationships, setup, commands, sample API flow, concurrency explanation, design tradeoffs, limitations, and future work.
- GitHub Actions workflow running `./mvnw verify`.
- Incremental commits grouped by coherent features.

Local target workflow:

```bash
docker compose up --build
./mvnw verify
```

Exact commands will be verified on the implemented repository before publication.

## 15. Migration from the existing version

- The existing in-memory JDK `HttpServer` implementation remains recoverable in Git history.
- The v2 README explicitly identifies the rewrite and links the original commit when helpful.
- Existing test ideas and domain behaviour may be retained, but code is migrated incrementally so each commit can be explained.
- No v2 resume claim is published until the corresponding code and tests exist on the default branch.

## 16. Resume evidence produced by this project

After verification, CampusQueue may support truthful claims about:

- Java 17 and Spring Boot REST API development.
- PostgreSQL schema design with JPA and Flyway.
- JWT authentication and role/ownership authorization.
- Transactional FIFO waitlisting using database row locking.
- Automated unit, integration, HTTP, and concurrent-registration tests.
- Docker Compose and GitHub Actions.

Exact numbers and wording will be derived from the final verified implementation. The specification itself is not evidence that a feature has been built.

## 17. Acceptance criteria

The MVP is complete only when:

- A fresh clone can build with the Maven Wrapper.
- `./mvnw verify` passes against Testcontainers PostgreSQL.
- Docker Compose starts the API and PostgreSQL from documented commands.
- Registration cannot overbook a workshop under the defined parallel test.
- Cancellation promotes the correct waitlisted student atomically.
- Authentication, role rules, and organizer ownership are tested.
- Flyway migrations initialize an empty database.
- OpenAPI documentation exposes the supported endpoints.
- No secrets or generated build artifacts are committed.
- CI passes on the repository branch.
- README claims match verified behaviour.
- The author can explain the main data model, transaction boundary, locking choice, test strategy, and limitations.

## 18. Known tradeoffs

- Pessimistic workshop-level locking favors correctness and clarity over high write throughput for a single workshop.
- One registration row per student/workshop simplifies current-state queries but does not preserve a complete status-event audit trail.
- Self-registration as an organizer is convenient for a portfolio demo but would require administrative approval in a real campus system.
- Access-token-only JWT authentication is intentionally smaller than a production token rotation and revocation system.
- A modular monolith is appropriate for the scope; microservices would add complexity without demonstrating better engineering judgment.
