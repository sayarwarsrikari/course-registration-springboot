# Course Registration Service (Spring Boot)

A Spring Boot port of the FastAPI course-registration service: bounded course enrollment with a
FIFO waitlist, prerequisite validation, and automatic seat promotion. The HTTP contract
(URLs, status codes, JSON field names, error bodies) is unchanged from the Python version.

---

## Project Structure

```
src/main/java/com/example/courseregistration/
├── CourseRegistrationApplication.java   # Entry point (replaces create_app() / main.py)
├── config/OpenApiConfig.java            # Title/version/description for Swagger UI (was core/config.py)
├── controller/EnrollmentController.java # HTTP route handlers (was api/routes/enrollment.py)
├── dto/                                 # Request/response records (was schemas/enrollment.py)
├── exception/                           # Domain exceptions + GlobalExceptionHandler (HTTP mapping)
├── model/                               # Learner, CourseOffering, Enrollment, WaitlistEntry
├── repository/CourseRepository.java     # In-memory store + thread-safe atomic operations
└── service/EnrollmentService.java       # All business logic
src/main/resources/application.properties
src/test/java/com/example/courseregistration/
├── EnrollmentApiTests.java              # MockMvc tests, ported 1:1 from test_enrollment.py
└── EnrollmentServiceTests.java          # Service-level tests (ineligible-skip + concurrency)
prompts/prompts.txt                      # AI prompts used during development (unchanged)
pom.xml
```

## Requirements

- JDK 21+
- Maven 3.9+ (no wrapper is included; run `mvn -N wrapper:wrapper` once if you want one)

## Run

```bash
mvn spring-boot:run
```

Server: http://localhost:8000

- Swagger UI: http://localhost:8000/docs
- OpenAPI JSON: http://localhost:8000/openapi.json

(FastAPI's ReDoc page has no built-in equivalent here; Swagger UI covers the same purpose.)

## Run Tests

```bash
mvn test
```

Run one test class or method:

```bash
mvn test -Dtest=EnrollmentServiceTests
mvn test -Dtest='EnrollmentApiTests$DropAndPromotion#fifoPromotionPromotesFirstEligibleWaitlistedLearner'
```

## API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| POST | /api/v1/courses | Create a course (201) |
| POST | /api/v1/learners | Register a learner (201) |
| POST | /api/v1/courses/{course_id}/enroll | Enroll or waitlist a learner (200; body says which) |
| DELETE | /api/v1/courses/{course_id}/enrollments/{learner_id} | Drop a learner, auto-promote next eligible |
| GET | /api/v1/courses/{course_id}/status | View enrollment and waitlist |

Error mapping: 404 unknown course/learner or drop of a non-enrolled learner; 409 duplicate enroll
or duplicate waitlist; 422 prerequisites not met (`detail.missing_prerequisites`) or invalid request
body. All error bodies have the shape `{"detail": ...}`.

Example:

```bash
curl -X POST localhost:8000/api/v1/courses -H 'Content-Type: application/json' \
  -d '{"id":"C1","name":"Advanced Python","capacity":1,"prerequisites":[]}'
curl -X POST localhost:8000/api/v1/learners -H 'Content-Type: application/json' \
  -d '{"id":"L1","name":"Alice","completed_courses":[]}'
curl -X POST localhost:8000/api/v1/courses/C1/enroll -H 'Content-Type: application/json' \
  -d '{"learner_id":"L1"}'
```

## Business Rules

- Enrolled count never exceeds course capacity.
- A learner cannot be enrolled and waitlisted at the same time.
- Duplicate enrollment and duplicate waitlist requests return 409.
- Prerequisites are checked at enrollment time and again at promotion time.
- If the first waitlisted learner is ineligible at promotion time, they are skipped but stay in their queue position.
- Drop requests for non-enrolled learners return 404 with no state change.

## Concurrency

Spring MVC serves each request on its own thread, so every `CourseRepository` method is
`synchronized` on the repository instance. The capacity check and the enrollment insert happen under
a single lock acquisition (`atomicEnrollOrWaitlist`), preventing the check-then-act race that could
over-enroll the last seat. `EnrollmentServiceTests.concurrentEnrollmentsNeverExceedCapacity` exercises
this with 60 simultaneous requests for 5 seats.

In a production database deployment this would become `SELECT ... FOR UPDATE` inside a transaction.

## Python -> Java mapping

| FastAPI | Spring Boot |
|---------|-------------|
| `create_app()` + `dependency_overrides` | Component scan + constructor injection |
| Pydantic schemas | Java records + Jakarta Bean Validation (`@NotNull`, `@Positive`) |
| `HTTPException` in route handlers | `@RestControllerAdvice` (`GlobalExceptionHandler`) |
| `threading.Lock` | `synchronized` methods |
| `TestClient` + fixture per test | `MockMvc` + `repository.clearAll()` in `@BeforeEach` |
| `/docs` (Swagger UI) | springdoc-openapi, configured to serve at `/docs` |
