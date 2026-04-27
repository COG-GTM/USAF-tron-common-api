# AGENTS.md

## What this service is

TRON Common API is a centralized RESTful backend service for the airmencoders ecosystem, providing a shared data layer and API gateway for personnel and organizational data management. It acts as the authoritative source for person records, organization structures, and application authentication/authorization within the TRON platform. The service integrates with multiple TRON applications, offering document storage capabilities (via S3/MinIO), pub-sub event notifications, and a scratch storage system for application-specific data persistence. It exposes both v1 and v2 versioned APIs with OpenAPI/Swagger documentation.

## Tech stack

- **Spring Boot**: 2.5.12
- **Java**: 11
- **PostgreSQL**: Runtime dependency (production database)
- **H2 Database**: 1.4.200 (in-memory, for development/testing)
- **Hibernate ORM**: 5.4.32.Final
- **Liquibase**: 4.3.1 (database migrations)
- **Spring Security**: 5.5.4
- **SpringDoc OpenAPI**: 1.5.0 (Swagger UI)
- **Jackson**: 2.13.2.1 (JSON processing)
- **Lombok**: (compile-time code generation)
- **Apache Camel**: 3.5.0 (integration/routing)
- **AWS SDK S3**: 1.12.68
- **ModelMapper**: 2.3.0 (entity-DTO mapping)
- **Logback**: 1.2.10 / Log4j2: 2.17.1
- **JUnit / Mockito**: (testing)
- **Maven**: Build tool

## Repo layout

```
src/main/java/mil/tron/commonapi/
├── annotation/          Custom annotations for security, response wrapping, and JSON patching
├── appgateway/          App source gateway logic and routing for external applications
├── controller/          REST controllers exposing API endpoints
│   ├── appsource/       App source management endpoints
│   ├── dashboard/       Dashboard analytics and KPI endpoints
│   ├── documentspace/   Document storage endpoints (S3/MinIO backed)
│   ├── kpi/             Key performance indicator endpoints
│   ├── pubsub/          Pub-sub subscription and event log endpoints
│   ├── puckboard/       Puckboard integration endpoints
│   ├── ranks/           Military rank reference data endpoints
│   └── scratch/         Scratch storage (JSON database) endpoints
├── dto/                 Data Transfer Objects for API requests/responses
├── entity/              JPA entities representing database tables
│   ├── appsource/       App source entities
│   ├── documentspace/   Document space entities
│   ├── kpi/             KPI tracking entities
│   ├── pubsub/          Event subscription entities
│   ├── ranks/           Rank entities
│   └── scratch/         Scratch storage entities
├── exception/           Custom exceptions and error handling
├── health/              Health check components for app sources
├── logging/             Custom logging infrastructure
├── pubsub/              Pub-sub event management and notification system
├── repository/          Spring Data JPA repositories
├── security/            Spring Security configuration and JWT/authentication handling
├── service/             Business logic layer
└── validations/         Custom validation annotations and validators
```

## Key entry points

| Controller | Base Path | Purpose |
|------------|-----------|---------|
| `AppClientController` | `/v1/app-client`, `/v2/app-client` | Manage application client credentials and privileges |
| `AppSourceController` | `/v1/app-source`, `/v2/app-source` | Manage external app sources and their endpoints |
| `AppVersionController` | `/v1/version`, `/v2/version` | Get application version, environment, and enclave info |
| `DashboardController` | `/v2/dashboard` | Dashboard analytics for personnel/org access metrics |
| `DashboardUserController` | `/v1/dashboard-users`, `/v2/dashboard-users` | Manage dashboard user accounts and privileges |
| `DocumentSpaceController` | `/v2/document-space` | File upload/download/management in S3/MinIO storage |
| `DocumentSpaceMobileController` | `/v2/document-space/mobile` | Mobile-optimized document space endpoints |
| `DocumentSpaceWebDavController` | `/v2/dav` | WebDAV interface for document space |
| `EventRequestLogController` | `/v1/pubsub/logs`, `/v2/pubsub/logs` | View pub-sub event delivery logs |
| `HttpLogsController` | `/v1/logs`, `/v2/logs` | Query HTTP request logs (dashboard admin only) |
| `KpiController` | `/v2/kpi` | Retrieve key performance indicators and time series data |
| `LogfileController` | `/v1/logfile`, `/v2/logfile` | Download application log files (dashboard admin only) |
| `MetricsController` | `/v1/metrics`, `/v2/metrics` | Retrieve app source and endpoint usage metrics |
| `OrganizationController` | `/v1/organization`, `/v2/organization` | CRUD operations on organization entities |
| `PersonController` | `/v1/person`, `/v2/person` | CRUD operations on person entities |
| `PrivilegeController` | `/v1/privilege`, `/v2/privilege` | List available privilege types |
| `PuckboardEtlController` | `/v2/puckboard` | ETL operations for Puckboard integration |
| `RankController` | `/v1/rank`, `/v2/rank` | Query military ranks by branch |
| `ScratchStorageController` | `/v1/scratch`, `/v2/scratch` | JSON-based key-value storage per app source |
| `SubscriberController` | `/v1/subscriptions`, `/v2/subscriptions` | Manage event subscriptions for pub-sub |
| `UserInfoController` | `/v1/userinfo`, `/v2/userinfo` | Extract user info from JWT tokens |

## How to run it locally

### Development profile (default)
```bash
mvn spring-boot:run
```
- **URL**: http://localhost:8088/api
- **Database**: H2 in-memory
- **Security**: Disabled by default (configurable via `SECURITY_ENABLED` env var or `security.enabled` property)
- **Swagger UI**: http://localhost:8088/api/
- **H2 Console**: http://localhost:8088/api/h2-console/ (when security disabled)
  - JDBC URL: `jdbc:h2:mem:testdb`
  - Username: `sa`
  - Password: (empty)

### Development profile with security enabled
```bash
SECURITY_ENABLED=true mvn spring-boot:run
```

### Production profile
```bash
mvn spring-boot:run -Pproduction
```
- **URL**: http://localhost:8080/api
- **Database**: PostgreSQL (requires env vars)
- **Security**: Enabled

### Required environment variables (production profile)
- `PGHOST` - PostgreSQL hostname
- `PGPORT` - PostgreSQL port (typically 5432)
- `PG_DATABASE` - Database name
- `PG_USER` - Database username
- `APP_DB_ADMIN_PASSWORD` - Database password

### Local profile
```bash
mvn spring-boot:run -Plocal
```
Create `application-local.properties` in `src/main/resources/` to override specific properties.

### Default ports
- **Development**: 8088
- **Production**: 8080

### Swagger documentation
Navigate to the API root (`/api`) to be redirected to Swagger UI for interactive API documentation.

## How to run the tests

### Run all tests
```bash
mvn test
```

### Run tests with coverage report
```bash
mvn clean test
```
- Coverage reports generated by JaCoCo plugin in `target/site/jacoco/`

### Test location
- Unit/Integration tests: `src/test/java/mil/tron/commonapi/`
- Test resources: `src/test/resources/`

### Test frameworks used
- **JUnit** - Test runner
- **Mockito** / **Mockito Inline**: 3.9.0 - Mocking framework
- **PowerMock**: 2.0.9 - Static/final method mocking
- **Spring Boot Test** - Integration testing support
- **Spring Security Test** - Security context testing
- **H2** - In-memory database for tests
- **S3Mock**: 0.2.6 - S3 service mocking

### Test types
- Controller tests: `src/test/java/mil/tron/commonapi/controller/`
- Service tests: `src/test/java/mil/tron/commonapi/service/`
- Integration tests: `src/test/java/mil/tron/commonapi/integration/`
- Entity tests: `src/test/java/mil/tron/commonapi/entity/`

## Conventions an agent should follow

### DTO naming
- DTOs are suffixed with `Dto` (e.g., `PersonDto`, `OrganizationDto`)
- Response wrappers for v2 endpoints use `DtoResponseWrapper` suffix (e.g., `PersonDtoResponseWrapper`)
- Pagination response wrappers use `DtoPaginationResponseWrapper` suffix
- Request DTOs may use specific suffixes like `RequestDto` or `CreateDto`
- DTOs live in `src/main/java/mil/tron/commonapi/dto/` and subdirectories by domain

### Exception handling
- Custom exceptions extend base exceptions in `src/main/java/mil/tron/commonapi/exception/`
- Common exceptions: `BadRequestException`, `RecordNotFoundException`, `ResourceAlreadyExistsException`, `InvalidAppSourcePermissions`, `InvalidScratchSpacePermissions`
- Exception responses use `ExceptionResponse` class with consistent structure
- No centralized `@ControllerAdvice` or `@RestControllerAdvice` visible in exception package - likely handled by Spring Boot defaults or in individual controllers

### Logging
- Custom logger class: `CommonApiLogger` in `mil.tron.commonapi.logging`
- Log level for custom logger: `INFO` (configured in `application.properties`)
- Root logging level: `WARN` (production)
- Use `CommonApiLogger` for application-specific logging
- Log files rotate with 7-day max history

### OpenAPI/Swagger annotations
- All controllers use `@Operation` annotation with `summary` and `description`
- All endpoints use `@ApiResponses` with specific response codes
- DTOs use `@Schema` annotations for field documentation
- Parameters use `@Parameter` annotation with descriptions
- Use `@Content(schema = @Schema(implementation = ...))` for response/request body documentation
- Discriminator mappings on polymorphic types (e.g., `PersonDto` with branch-specific subtypes)
- Mark read-only fields with `@Schema(accessMode = Schema.AccessMode.READ_ONLY)`

### Security patterns
- Custom security annotations in `mil.tron.commonapi.annotation.security`:
  - `@PreAuthorizeDashboardAdmin` - Dashboard admin only
  - `@PreAuthorizeDashboardUser` - Dashboard user or admin
  - `@PreAuthorizePersonRead/Write/Edit/Delete` - Person entity operations
  - `@PreAuthorizeOrganizationRead/Create/Edit/Delete` - Organization entity operations
  - `@PreAuthorizeOnlySSO` - SSO/web users only, no app clients
- JWT-based authentication via `x-forwarded-client-cert` header (Istio service mesh)
- Pre-authenticated filter: `AppClientPreAuthFilter`
- Privilege-based authorization (e.g., `READ`, `WRITE`, `DASHBOARD_ADMIN`, `DASHBOARD_USER`)
- Entity Field Authorization (EFA) system for field-level access control (enabled via `efa-enabled` property)

### REST conventions
- Use HTTP verbs correctly: GET (read), POST (create), PUT (update), PATCH (partial update), DELETE (remove)
- JSON Patch supported for PATCH operations (content-type: `application/json-patch+json`)
- v2 endpoints support wrapped responses using `@WrappedEnvelopeResponse` annotation
- Pagination support via `Pageable` parameter (Spring Data)
- Filtering endpoints use `FilterDto` with query criteria
- API versioning via path prefix: `/v1/` and `/v2/`
- Context path: `/api` (all endpoints prefixed)

### Code style
- Use Lombok annotations: `@Getter`, `@Setter`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@EqualsAndHashCode`, `@Data`
- Entities use JPA annotations: `@Entity`, `@Table`, `@Id`, `@Column`, etc.
- Repositories extend `JpaRepository` or `CrudRepository`
- Services use `@Service` annotation
- Controllers use `@RestController` and `@RequestMapping`
- Validation uses JSR-303 annotations: `@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Email`
- Custom validators in `mil.tron.commonapi.validations` (e.g., `@ValidDodId`, `@ValidPhoneNumber`, `@NullOrNotBlankValidation`)

### Database conventions
- Use Liquibase changesets in `src/main/resources/db/` for schema migrations
- Generate diffs using `mvn liquibase:diff` when making complex changes
- Entities in `mil.tron.commonapi.entity` map to database tables
- UUID primary keys for most entities
- Timestamp fields typically use database-generated values
- PostgreSQL in production, H2 in development/test

### API response conventions
- Return appropriate HTTP status codes (200, 201, 204, 400, 403, 404, 409)
- Use `ResponseEntity<T>` for controller methods
- Include pagination links in `Link` header when applicable
- v2 endpoints wrap collections in envelope with metadata
- Error responses use consistent `ExceptionResponse` structure

### Testing conventions
- Test classes suffixed with `Test` (e.g., `PersonControllerTest`)
- Integration tests in `integration/` subdirectory
- Use `@SpringBootTest` for integration tests
- Use `@WebMvcTest` for controller unit tests
- Mock external dependencies with `@MockBean`
- H2 database for test data persistence
- Test data setup in `@BeforeEach` or test-specific methods
