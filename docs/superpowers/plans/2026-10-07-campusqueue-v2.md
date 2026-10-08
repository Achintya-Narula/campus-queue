# CampusQueue v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite CampusQueue as a tested Java 17 Spring Boot and PostgreSQL REST API with JWT security, transactional FIFO waitlisting, Docker Compose, and CI.

**Architecture:** A modular monolith uses Spring MVC controllers, Spring Security resource-server JWT authentication, transactional services, Spring Data JPA repositories, Flyway migrations, and PostgreSQL. Every capacity-changing operation acquires a pessimistic write lock on the workshop row; API DTOs remain separate from JPA entities.

**Tech Stack:** Java 17, Spring Boot 3.5.16, Maven Wrapper 3.9.11, Spring Security, Spring Data JPA, PostgreSQL 17, Flyway, springdoc-openapi 2.9.1, JUnit 5, MockMvc, Testcontainers 2.0.5, Docker Compose, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-07-campusqueue-v2-design.md`

## Global Constraints

- Use Java 17 and package root `com.achintya.campusqueue`.
- Use Spring Boot 3.5.16 and PostgreSQL as the only application and integration-test database; do not add H2.
- Keep the API under `/api/v1`; expose JPA entities only through DTO records.
- Use UUID identifiers, UTC `Instant` timestamps, lowercase-normalized unique emails, and BCrypt password hashes.
- Read the JWT secret, database credentials, and environment-dependent configuration from environment variables; commit only `.env.example`.
- Use Flyway for schema creation and `ddl-auto: validate`; never depend on Hibernate schema generation.
- All enrollment, student cancellation, workshop cancellation, and capacity-changing paths must lock the workshop row inside a transaction.
- Do not add notifications, Redis, payments, email, a frontend, or other non-goals from the specification.
- Do not publish an exact test count or resume claim until the final verification task passes.
- Do not use Lombok; keep constructors, state transitions, and domain methods visible for learning and interview explanation.

## Review Focus

- Email case and surrounding whitespace: ` Student@Example.com ` and `student@example.com` must resolve to one normalized identity; pin this in Task 2.
- Bad authentication material: missing, malformed, tampered, and expired bearer tokens must return `401` without stack traces; pin this in Task 2.
- Visibility boundaries: public search must never return draft/cancelled workshops, and one organizer must not view another organizer's roster; pin this in Tasks 3, 6, and 8.
- Capacity changes and races: reducing capacity below confirmed enrollment must return `409`, and concurrent registration/cancellation must never overbook; pin this in Tasks 6 and 7.
- Repeated lifecycle actions: duplicate registration, repeated cancellation, republishing, and registering for cancelled workshops must produce stable conflict errors without corrupting state; pin this in Tasks 4, 5, and 6.

---

## File Structure

The rewrite removes the old `dev.achu` source tree and shell scripts from the working tree; commit `da512f1d03b219c931a365fec956c8aa3de1e1c0` remains the recoverable v1 implementation.

- `pom.xml`, `.mvn/wrapper/*`, `mvnw`, `mvnw.cmd` — reproducible Java build.
- `src/main/java/com/achintya/campusqueue/CampusQueueApplication.java` — application entry point.
- `common/config`, `common/error`, `common/security` — shared configuration, problem responses, and JWT support.
- `user`, `auth`, `workshop`, `registration` — feature packages containing entities, repositories, DTOs, services, and controllers.
- `src/main/resources/application.yml` — environment-backed configuration.
- `src/main/resources/db/migration/V1__create_campusqueue_schema.sql` — PostgreSQL schema.
- `src/test/java/.../support/PostgresIntegrationTestSupport.java` — shared Testcontainers database setup.
- Feature-specific `*Test.java` and `*IT.java` files — unit and HTTP/PostgreSQL integration tests.
- `Dockerfile`, `compose.yaml`, `.env.example` — local runtime.
- `.github/workflows/test.yml` — CI verification.
- `README.md`, `docs/api-examples.http` — verified usage and explanation.

### Task 1: Spring Boot and PostgreSQL foundation

**Files:**
- Create: `pom.xml`
- Create: `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd`
- Create: `src/main/java/com/achintya/campusqueue/CampusQueueApplication.java`
- Create: `src/main/java/com/achintya/campusqueue/user/UserEntity.java`, `UserRole.java`, `UserRepository.java`
- Create: `src/main/java/com/achintya/campusqueue/workshop/WorkshopEntity.java`, `WorkshopStatus.java`, `WorkshopRepository.java`
- Create: `src/main/java/com/achintya/campusqueue/registration/RegistrationEntity.java`, `RegistrationStatus.java`, `RegistrationRepository.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/db/migration/V1__create_campusqueue_schema.sql`
- Create: `src/test/java/com/achintya/campusqueue/support/PostgresIntegrationTestSupport.java`
- Create: `src/test/java/com/achintya/campusqueue/DatabaseMigrationIT.java`
- Modify: `.gitignore`
- Delete: `scripts/`, `src/main/java/dev/`, `src/test/java/dev/`

**Interfaces:**
- Produces: `UserEntity(UUID id, String email, String passwordHash, UserRole role, Instant createdAt)` with explicit state accessors.
- Produces: `WorkshopEntity` domain methods `publish()`, `updateDetails(String title, String description, int capacity)`, `cancel()`, and `long allocateWaitlistSequence()`.
- Produces: `RegistrationEntity` domain methods `confirm(Instant now)`, `waitlist(long sequence, Instant now)`, and `cancel(Instant now)`.
- Produces: `WorkshopRepository.findByIdForUpdate(UUID): Optional<WorkshopEntity>` using `PESSIMISTIC_WRITE`.
- Produces: registration queries by workshop/student, status counts, earliest waitlist sequence, and ordered rosters for later tasks.

- [ ] **Step 1: Write the failing migration test**

  Add `DatabaseMigrationIT.migratesTablesConstraintsAndIndexes()` extending `PostgresIntegrationTestSupport`. Assert the `app_user`, `workshop`, and `registration` tables exist; inserting a second registration for the same workshop/student violates `uk_registration_workshop_student`; and a workshop capacity of zero violates `ck_workshop_capacity_positive`.

- [ ] **Step 2: Run the test and verify the existing project cannot execute it**

  Run: `./mvnw -Dtest=DatabaseMigrationIT test`

  Expected: FAIL because the Maven wrapper/Spring Boot application and migration do not exist.

- [ ] **Step 3: Create the minimal Spring Boot build and schema**

  Generate an `only-script` Maven wrapper for Maven 3.9.11. Configure `pom.xml` with Spring Boot 3.5.16 and starters for web, validation, security, OAuth2 resource server, data JPA, PostgreSQL, Flyway, springdoc 2.9.1, test, security-test, and Testcontainers 2.0.5 PostgreSQL/JUnit support. Configure Maven Failsafe to run `**/*IT.java` during `integration-test`/`verify`. Implement the six domain types, three repositories, application configuration, and the V1 migration with the exact names asserted above.

- [ ] **Step 4: Run foundation verification**

  Run: `./mvnw -Dtest=DatabaseMigrationIT test`

  Expected: PASS with a PostgreSQL Testcontainer and successful Flyway validation.

- [ ] **Step 5: Commit the foundation**

  Run:

  ```bash
  git add -A
  git commit -m "build: migrate CampusQueue to Spring Boot and PostgreSQL"
  ```

### Task 2: Registration, login, and JWT security

**Files:**
- Create: `src/main/java/com/achintya/campusqueue/auth/AuthController.java`, `AuthService.java`
- Create: `src/main/java/com/achintya/campusqueue/auth/dto/RegisterRequest.java`, `LoginRequest.java`, `AuthResponse.java`, `UserResponse.java`
- Create: `src/main/java/com/achintya/campusqueue/common/config/JwtProperties.java`
- Create: `src/main/java/com/achintya/campusqueue/common/security/JwtService.java`, `SecurityConfig.java`, `JsonAuthenticationEntryPoint.java`, `JsonAccessDeniedHandler.java`
- Create: `src/main/java/com/achintya/campusqueue/common/error/ApiError.java`, `FieldErrorDetail.java`, `GlobalExceptionHandler.java`, `ApiException.java`, `ConflictException.java`, `ForbiddenException.java`, `NotFoundException.java`
- Create: `src/test/java/com/achintya/campusqueue/auth/AuthApiIT.java`
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Produces: `AuthService.register(RegisterRequest): AuthResponse` and `AuthService.login(LoginRequest): AuthResponse`.
- Produces: `JwtService.issueToken(UserEntity): String` with subject=user UUID and claim `role`=`STUDENT|ORGANIZER`; its constructor consumes `Clock` so expiration tests are deterministic.
- Produces: `ApiError(Instant timestamp, int status, String code, String message, String path, List<FieldErrorDetail> fieldErrors)`.
- Consumes: `UserRepository` from Task 1.

- [ ] **Step 1: Write failing authentication API tests**

  In `AuthApiIT`, assert registration returns `201`, a lowercase email, a non-empty JWT, and never returns `passwordHash`; the database hash differs from the submitted password and matches BCrypt. Assert login succeeds with the normalized email. Assert a differently-cased duplicate email returns `409 EMAIL_ALREADY_EXISTS`.

- [ ] **Step 2: Add authentication failure tests from Review Focus**

  Assert missing, malformed, tampered, and expired tokens return `401` with a JSON problem body and no exception/stack-trace text. Assert invalid credentials return `401 INVALID_CREDENTIALS`.

- [ ] **Step 3: Run tests to verify failure**

  Run: `./mvnw -Dtest=AuthApiIT test`

  Expected: FAIL with missing auth endpoints/configuration.

- [ ] **Step 4: Implement authentication and global errors**

  Validate email with `@Email`, trim/lowercase using `Locale.ROOT`, require password length 8–72, encode with BCrypt, and issue HS256 JWTs through Spring Security's `JwtEncoder`. Inject `Clock` into `JwtService`. Configure stateless sessions and public access only to auth, public workshop GET routes, and OpenAPI paths; all other routes require authentication. Use `JsonAuthenticationEntryPoint` and `JsonAccessDeniedHandler` for security-filter failures, and map controller/service exceptions through `GlobalExceptionHandler`.

- [ ] **Step 5: Run authentication verification**

  Run: `./mvnw -Dtest=AuthApiIT test`

  Expected: PASS for success, normalization, hashing, duplicate, invalid-login, and all `401` cases.

- [ ] **Step 6: Commit authentication**

  ```bash
  git add src/main src/test pom.xml
  git commit -m "feat: add JWT authentication and API errors"
  ```

### Task 3: Organizer workshop lifecycle and public reads

**Files:**
- Create: `src/main/java/com/achintya/campusqueue/workshop/WorkshopController.java`, `OrganizerWorkshopController.java`, `WorkshopService.java`
- Create: `src/main/java/com/achintya/campusqueue/workshop/dto/CreateWorkshopRequest.java`, `UpdateWorkshopRequest.java`, `WorkshopResponse.java`
- Create: `src/test/java/com/achintya/campusqueue/workshop/WorkshopApiIT.java`
- Modify: `src/main/java/com/achintya/campusqueue/workshop/WorkshopRepository.java`

**Interfaces:**
- Produces: `WorkshopService.create(UUID organizerId, CreateWorkshopRequest): WorkshopResponse`.
- Produces: `WorkshopService.update(UUID organizerId, UUID workshopId, UpdateWorkshopRequest): WorkshopResponse`.
- Produces: `WorkshopService.publish(UUID organizerId, UUID workshopId): WorkshopResponse`.
- Produces: `WorkshopService.getPublished(UUID workshopId): WorkshopResponse`, `listPublished(String query, Pageable): Page<WorkshopResponse>`, and `listOwned(UUID organizerId, Pageable): Page<WorkshopResponse>`.
- Consumes: JWT subject and role from Task 2; workshop/user persistence from Task 1.

- [ ] **Step 1: Write failing organizer lifecycle tests**

  Assert an organizer can create a `DRAFT` workshop, edit it, publish it, and list owned workshops. Assert a student receives `403`, a different organizer receives `403 WORKSHOP_NOT_OWNED`, repeated publish returns `409 INVALID_WORKSHOP_TRANSITION`, and missing IDs return `404 WORKSHOP_NOT_FOUND`.

- [ ] **Step 2: Write failing public visibility and validation tests**

  Assert unauthenticated GET returns published workshops only; draft workshops are absent and direct draft lookup returns `404`. Assert blank/overlong title, overlong description, and capacity below 1 return `400` with named field errors.

- [ ] **Step 3: Run tests to verify failure**

  Run: `./mvnw -Dtest=WorkshopApiIT test`

  Expected: FAIL with missing workshop controllers/service.

- [ ] **Step 4: Implement the lifecycle endpoints**

  Map organizer endpoints under `/api/v1/organizer/workshops` and public endpoints under `/api/v1/workshops`. Resolve organizer identity only from the JWT subject. Keep ownership and transition checks in `WorkshopService`, not controllers.

- [ ] **Step 5: Run workshop verification**

  Run: `./mvnw -Dtest=WorkshopApiIT test`

  Expected: PASS for lifecycle, authorization, public visibility, and validation cases.

- [ ] **Step 6: Commit workshop lifecycle**

  ```bash
  git add src/main src/test
  git commit -m "feat: add organizer workshop lifecycle"
  ```

### Task 4: Capacity-aware enrollment and student views

**Files:**
- Create: `src/main/java/com/achintya/campusqueue/registration/RegistrationController.java`, `RegistrationService.java`
- Create: `src/main/java/com/achintya/campusqueue/registration/dto/RegistrationResponse.java`
- Create: `src/test/java/com/achintya/campusqueue/registration/RegistrationApiIT.java`
- Modify: `src/main/java/com/achintya/campusqueue/registration/RegistrationRepository.java`
- Modify: `src/main/java/com/achintya/campusqueue/workshop/WorkshopRepository.java`

**Interfaces:**
- Produces: `RegistrationService.register(UUID studentId, UUID workshopId): RegistrationResponse`.
- Produces: `RegistrationService.listForStudent(UUID studentId, Pageable): Page<RegistrationResponse>`.
- Produces: `RegistrationResponse(UUID id, UUID workshopId, RegistrationStatus status, Long queuePosition, Instant createdAt, Instant updatedAt)`.
- Consumes: `WorkshopRepository.findByIdForUpdate(UUID)` and registration domain methods from Task 1.

- [ ] **Step 1: Write failing enrollment tests**

  For a published capacity-one workshop, assert the first student receives `CONFIRMED`; the second receives `WAITLISTED` with queue position 1; `GET /api/v1/me/registrations` returns only the authenticated student's records; and no request accepts a client-supplied student ID.

- [ ] **Step 2: Write failing conflict and reactivation tests**

  Assert duplicate active registration returns `409 ALREADY_REGISTERED`, organizers cannot register, draft/cancelled workshops reject registration, and a previously cancelled row is reactivated without creating a second workshop/student row or reusing an old waitlist sequence.

- [ ] **Step 3: Run tests to verify failure**

  Run: `./mvnw -Dtest=RegistrationApiIT test`

  Expected: FAIL with missing registration service/endpoints.

- [ ] **Step 4: Implement transactional enrollment**

  Mark `register` transactional, lock the workshop before reading registration/capacity state, count confirmed registrations while locked, reactivate cancelled rows, and allocate a monotonically increasing workshop sequence only when the result is `WAITLISTED`. Calculate current queue position by counting active sequences up to the assigned sequence.

- [ ] **Step 5: Run enrollment verification**

  Run: `./mvnw -Dtest=RegistrationApiIT test`

  Expected: PASS for confirmed, waitlisted, isolation, duplicate, lifecycle, role, and reactivation cases.

- [ ] **Step 6: Commit enrollment**

  ```bash
  git add src/main src/test
  git commit -m "feat: add transactional workshop enrollment"
  ```

### Task 5: Student cancellation and FIFO promotion

**Files:**
- Create: `src/main/java/com/achintya/campusqueue/registration/dto/CancellationResponse.java`
- Create: `src/test/java/com/achintya/campusqueue/registration/CancellationApiIT.java`
- Modify: `src/main/java/com/achintya/campusqueue/registration/RegistrationController.java`, `RegistrationService.java`, `RegistrationRepository.java`

**Interfaces:**
- Produces: `RegistrationService.cancel(UUID studentId, UUID workshopId): CancellationResponse`.
- Produces: `CancellationResponse(UUID cancelledRegistrationId, RegistrationStatus previousStatus, UUID promotedRegistrationId)`; `promotedRegistrationId` is nullable.
- Consumes: earliest active waitlist query ordered by `waitlistSequence ASC`.

- [ ] **Step 1: Write failing FIFO promotion tests**

  Register one confirmed student and three waitlisted students. Assert cancelling the confirmed student marks that row `CANCELLED`, promotes exactly the sequence-1 student, clears the promoted row's sequence, and leaves later queue order stable.

- [ ] **Step 2: Write failing cancellation edge tests**

  Assert cancelling a waitlisted student promotes nobody; cancelling another student's registration is impossible because identity comes from the token; and a repeated cancellation returns `409 REGISTRATION_NOT_ACTIVE` without another promotion.

- [ ] **Step 3: Run tests to verify failure**

  Run: `./mvnw -Dtest=CancellationApiIT test`

  Expected: FAIL because cancellation/promotion is not implemented.

- [ ] **Step 4: Implement atomic cancellation and promotion**

  In one `@Transactional` method, lock the workshop, load the authenticated student's active registration, cancel it, and only for a previously confirmed row promote the smallest active waitlist sequence before commit.

- [ ] **Step 5: Run cancellation verification**

  Run: `./mvnw -Dtest=CancellationApiIT test`

  Expected: PASS for FIFO promotion, waitlisted cancellation, identity isolation, and repeat handling.

- [ ] **Step 6: Commit cancellation**

  ```bash
  git add src/main src/test
  git commit -m "feat: add atomic cancellation and FIFO promotion"
  ```

### Task 6: Organizer capacity rules, roster, and workshop cancellation

**Files:**
- Create: `src/main/java/com/achintya/campusqueue/registration/dto/RosterEntryResponse.java`, `WorkshopRosterResponse.java`
- Create: `src/test/java/com/achintya/campusqueue/workshop/OrganizerManagementApiIT.java`
- Modify: `src/main/java/com/achintya/campusqueue/workshop/OrganizerWorkshopController.java`, `WorkshopService.java`, `WorkshopRepository.java`
- Modify: `src/main/java/com/achintya/campusqueue/registration/RegistrationRepository.java`

**Interfaces:**
- Produces: `WorkshopService.cancel(UUID organizerId, UUID workshopId): WorkshopResponse`.
- Produces: `WorkshopService.getRoster(UUID organizerId, UUID workshopId): WorkshopRosterResponse`.
- Produces: `WorkshopRosterResponse(List<RosterEntryResponse> confirmed, List<RosterEntryResponse> waitlisted)` with waitlisted rows ordered by sequence.
- Extends: `WorkshopService.update(...)` to lock the workshop and reject `capacity < confirmedCount`.

- [ ] **Step 1: Write failing capacity and roster tests**

  Assert capacity can be increased, but reducing below confirmed count returns `409 CAPACITY_BELOW_CONFIRMED`. Assert the owner roster separates confirmed/waitlisted users and preserves FIFO order; a different organizer receives `403` and no student email data.

- [ ] **Step 2: Write failing workshop-cancellation tests**

  Assert owner cancellation changes the workshop and every active registration to `CANCELLED` in one transaction; public direct lookup becomes `404`; registration is rejected; repeated cancellation/republish return stable `409` errors.

- [ ] **Step 3: Run tests to verify failure**

  Run: `./mvnw -Dtest=OrganizerManagementApiIT test`

  Expected: FAIL with missing roster/cancellation behavior.

- [ ] **Step 4: Implement locked organizer management**

  Use the same workshop-row lock for capacity updates and cancellation. Verify ownership after loading the lock. Bulk-cancel active registrations within the transaction and return roster DTOs without password data.

- [ ] **Step 5: Run organizer verification**

  Run: `./mvnw -Dtest=OrganizerManagementApiIT test`

  Expected: PASS for capacity, roster ownership, cancellation atomicity, public hiding, and repeated lifecycle actions.

- [ ] **Step 6: Commit organizer management**

  ```bash
  git add src/main src/test
  git commit -m "feat: add roster and workshop cancellation rules"
  ```

### Task 7: Database concurrency verification

**Files:**
- Create: `src/test/java/com/achintya/campusqueue/registration/RegistrationConcurrencyIT.java`
- Modify if the red test exposes a defect: `WorkshopRepository.java`, `RegistrationRepository.java`, `RegistrationService.java`, `WorkshopService.java`

**Interfaces:**
- Verifies: the existing service interfaces from Tasks 4–6; no new public API is expected.

- [ ] **Step 1: Write the required 20-registration test**

  Publish a capacity-three workshop, create 20 distinct students, submit `RegistrationService.register` calls from an eight-thread executor released by one `CountDownLatch`, and assert 3 `CONFIRMED`, 17 `WAITLISTED`, 17 unique ordered sequences, 20 unique workshop/student rows, and no worker exception.

- [ ] **Step 2: Write the mixed race invariant test**

  Starting from a full capacity-three workshop with a waitlist, concurrently cancel one confirmed student and register one new student. Assert confirmed count never exceeds 3, exactly one active record exists for each student/workshop pair, and the earliest eligible waiter is not skipped.

- [ ] **Step 3: Run concurrency tests repeatedly**

  Run: `for i in 1 2 3 4 5; do ./mvnw -Dtest=RegistrationConcurrencyIT test || exit 1; done`

  Expected before any fix: either PASS if locking is correct or a reproducible invariant failure that identifies the missing lock/query behavior.

- [ ] **Step 4: Apply the smallest locking fix if a test is red**

  Keep the workshop-row `PESSIMISTIC_WRITE` lock as the single serialization point; do not add JVM locks, retries, Redis, or a second concurrency design.

- [ ] **Step 5: Re-run all persistence and concurrency tests**

  Run: `./mvnw -Dtest='*IT' test`

  Expected: PASS with the exact 3/17 invariant and mixed race invariants.

- [ ] **Step 6: Commit concurrency proof**

  ```bash
  git add src/main src/test
  git commit -m "test: verify database-backed enrollment concurrency"
  ```

### Task 8: Search, pagination, and OpenAPI contract

**Files:**
- Create: `src/main/java/com/achintya/campusqueue/common/api/PageResponse.java`
- Create: `src/main/java/com/achintya/campusqueue/common/config/OpenApiConfig.java`
- Create: `src/test/java/com/achintya/campusqueue/workshop/PublicWorkshopApiIT.java`
- Modify: `src/main/java/com/achintya/campusqueue/workshop/WorkshopController.java`, `WorkshopService.java`, `WorkshopRepository.java`
- Modify: `src/main/java/com/achintya/campusqueue/common/security/SecurityConfig.java`

**Interfaces:**
- Produces: `PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages)` and `PageResponse.from(Page<T>)`.
- Refines: `GET /api/v1/workshops?q=&page=0&size=20`, with size range 1–50 and case-insensitive title/description search restricted to `PUBLISHED`.
- Produces: Swagger UI at `/swagger-ui.html` and OpenAPI JSON at `/v3/api-docs`.

- [ ] **Step 1: Write failing search/pagination tests**

  Assert case-insensitive query matches title or description, never returns draft/cancelled workshops, page metadata is stable, negative page/zero size/size above 50 returns `400`, and an empty query behaves like no filter.

- [ ] **Step 2: Write failing OpenAPI tests**

  Assert unauthenticated `/v3/api-docs` returns `200`, contains the auth/workshop/registration paths and bearer security scheme, and `/swagger-ui.html` is reachable or redirects to the UI.

- [ ] **Step 3: Run tests to verify failure**

  Run: `./mvnw -Dtest=PublicWorkshopApiIT test`

  Expected: FAIL until page DTO, validated query handling, and OpenAPI configuration exist.

- [ ] **Step 4: Implement the public contract**

  Add the bounded page request, repository search restricted to `PUBLISHED`, generic page response, OpenAPI title/version/security scheme, and unauthenticated documentation rules.

- [ ] **Step 5: Run public API verification**

  Run: `./mvnw -Dtest=PublicWorkshopApiIT test`

  Expected: PASS for query, visibility, bounds, page metadata, and OpenAPI accessibility.

- [ ] **Step 6: Commit public API contract**

  ```bash
  git add src/main src/test
  git commit -m "feat: add workshop search and OpenAPI docs"
  ```

### Task 9: Docker and GitHub Actions

**Files:**
- Create: `Dockerfile`, `compose.yaml`, `.env.example`, `.dockerignore`
- Modify: `.github/workflows/test.yml`
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Produces: `docker compose up --build` with services `api` and `postgres`.
- Produces: environment keys `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `JWT_TTL`.
- Produces: CI execution of `./mvnw --batch-mode verify` on Java 17 with Docker available to Testcontainers.

- [ ] **Step 1: Validate the absent container configuration**

  Run: `docker compose config`

  Expected: FAIL because `compose.yaml` does not exist.

- [ ] **Step 2: Add container and environment files**

  Use a Java 17 multi-stage image, a non-root runtime user, PostgreSQL `17-alpine`, a database healthcheck, API dependency on healthy PostgreSQL, and named database storage. Keep real secrets out of all committed files.

- [ ] **Step 3: Replace the legacy CI workflow**

  Configure checkout, Temurin Java 17 with Maven cache, executable wrapper permission, and `./mvnw --batch-mode verify`; retain read-only repository permissions.

- [ ] **Step 4: Verify configuration, tests, and runtime**

  Run: `docker compose config && ./mvnw verify && docker compose up --build -d`

  Expected: valid Compose output, BUILD SUCCESS, healthy PostgreSQL, and API startup without Flyway/JPA validation errors.

- [ ] **Step 5: Smoke-test the container and clean up**

  Run: `curl --fail http://localhost:8080/v3/api-docs >/dev/null && docker compose down`

  Expected: curl exit 0; containers stop cleanly while the named volume remains.

- [ ] **Step 6: Commit operations support**

  ```bash
  git add Dockerfile compose.yaml .env.example .dockerignore .github src/main/resources
  git commit -m "ci: add Docker Compose and Maven verification"
  ```

### Task 10: Documentation and final evidence gate

**Files:**
- Rewrite: `README.md`
- Create: `docs/api-examples.http`
- Modify only if verification finds an error: implementation/test files from Tasks 1–9

**Interfaces:**
- Documents: architecture, schema, setup for Bash and Windows PowerShell, environment variables, sample auth/workshop/registration flow, concurrency transaction, test strategy, limitations, and v1 commit link.
- Produces: verified project facts for later resume/GitHub/portfolio alignment; no resume file changes occur in this task.

- [ ] **Step 1: Run the complete evidence suite before writing claims**

  Run: `./mvnw clean verify`

  Expected: BUILD SUCCESS with unit and Testcontainers integration tests passing.

- [ ] **Step 2: Count tests from generated reports**

  Run: `awk 'match($0, /tests="[0-9]+"/) {total += substr($0, RSTART+7, RLENGTH-8)} END {print total}' target/surefire-reports/TEST-*.xml target/failsafe-reports/TEST-*.xml`

  Expected: one numeric total matching the committed suite; use it only if the README needs an exact count.

- [ ] **Step 3: Rewrite documentation from verified behavior**

  Include both `./mvnw verify` and `.\mvnw.cmd verify`, Docker Compose setup, Swagger URL, an ER diagram, registration flow, the PostgreSQL row-lock decision, reproducible concurrency scenario, honest tradeoffs, future work, and the original v1 commit `da512f1d03b219c931a365fec956c8aa3de1e1c0`.

- [ ] **Step 4: Add copy-pasteable API examples**

  In `docs/api-examples.http`, provide registration/login, create/publish workshop, student registration, cancellation, roster, and public search requests using variables for tokens and UUIDs; do not embed credentials or a real JWT.

- [ ] **Step 5: Perform final repository verification**

  Run: `./mvnw clean verify && git diff --check && git status --short`

  Expected: BUILD SUCCESS, no whitespace errors, and only the intended README/API-example changes uncommitted.

- [ ] **Step 6: Commit verified documentation**

  ```bash
  git add README.md docs/api-examples.http
  git commit -m "docs: explain verified CampusQueue v2 design"
  ```

- [ ] **Step 7: Check remote CI after pushing the implementation branch**

  Expected: the GitHub Actions Maven verification workflow is green before any v2 feature or test-count claim is copied into the resume, profile README, portfolio, or LinkedIn draft.

