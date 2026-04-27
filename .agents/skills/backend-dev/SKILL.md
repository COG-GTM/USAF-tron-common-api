# Backend Development Reference

Reference knowledge for working with TRON Common API.

## 1. Local Dev Loop

### Clone & Build
```bash
git clone <repo-url>
cd USAF-tron-common-api
./mvnw clean install
```

### Run in Development Profile (default)
```bash
./mvnw spring-boot:run
```

- Port: `http://localhost:8088/api`
- Database: H2 in-memory
- Security: Disabled by default (set `security.enabled=true` in `src/main/resources/application-development.properties` or env var `SECURITY_ENABLED=true` to enable)
- H2 Console: `http://localhost:8088/api/h2-console/` (JDBC: `jdbc:h2:mem:testdb`, user: `sa`, no password)

### Run in Production Profile
```bash
./mvnw spring-boot:run -Pproduction
```

- Port: `http://localhost:8080/api`
- Database: PostgreSQL (requires env vars: `PGHOST`, `PGPORT`, `PG_DATABASE`, `PG_USER`, `APP_DB_ADMIN_PASSWORD`)
- Security: Enabled

### Hot Reload
Spring Boot DevTools is enabled. Changes to classes trigger automatic restart. Ensure IDE auto-compile is enabled.

### Swagger Docs
Navigate to root: `http://localhost:8088/api/` redirects to Swagger UI.

## 2. Running a Single Test

### Run specific test class
```bash
./mvnw test -Dtest=PrivilegeControllerTest
```

### Run specific test method
```bash
./mvnw test -Dtest=PrivilegeControllerTest#getAll
```

### Run all tests
```bash
./mvnw test
```

Tests use:
- `@SpringBootTest`
- `@TestPropertySource(locations = "classpath:application-test.properties")`
- H2 database with test profile

## 3. Adding a New Endpoint

Reference example: `src/main/java/mil/tron/commonapi/controller/PrivilegeController.java`

### Pattern
```java
@RestController
@PreAuthorizeDashboardAdmin  // or other auth annotation
public class PrivilegeController {
    
    private PrivilegeService privilegeService;
    
    public PrivilegeController(PrivilegeService privilegeService) {
        this.privilegeService = privilegeService;
    }
    
    @Operation(summary = "Retrieves all Privilege information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", 
            description = "Successful operation", 
            content = @Content(schema = @Schema(implementation = PrivilegeDtoResponseWrapper.class)))
    })
    @WrappedEnvelopeResponse  // for v2 endpoints
    @GetMapping({"${api-prefix.v2}/privilege"})
    public ResponseEntity<Object> getPrivilegesWrapped() {
        return new ResponseEntity<>(privilegeService.getPrivileges(), HttpStatus.OK);
    }
}
```

### Key Components
- **Controller**: `@RestController`, inject service via constructor
- **Auth**: Use `@PreAuthorizeDashboardAdmin`, `@PreAuthorizeDashboardUser`, or custom annotations
- **Swagger**: `@Operation`, `@ApiResponses` for documentation
- **Versioning**: Use `${api-prefix.v1}` or `${api-prefix.v2}` from properties
- **Response**: v2 endpoints use `@WrappedEnvelopeResponse` wrapper

### Test
Create `src/test/java/mil/tron/commonapi/controller/PrivilegeControllerTest.java`:
```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
class PrivilegeControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private PrivilegeService service;
    
    @Test
    void getAll() throws Exception {
        Mockito.when(service.getPrivileges()).thenReturn(privileges);
        
        mockMvc.perform(get("/v2/privilege/"))
            .andExpect(status().isOk());
    }
}
```

## 4. Adding a New Entity

Reference: Privilege entity end-to-end flow.

### 1. Entity (`src/main/java/mil/tron/commonapi/entity/Privilege.java`)
```java
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Privilege {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long id;
    
    @Getter
    @Setter
    private String name;
}
```

### 2. Repository (`src/main/java/mil/tron/commonapi/repository/PrivilegeRepository.java`)
```java
@Repository
public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {
    Optional<Privilege> findByName(String name);
}
```

### 3. DTO (`src/main/java/mil/tron/commonapi/dto/PrivilegeDto.java`)
```java
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrivilegeDto {
    @Getter
    @Setter
    private Long id;
    
    @Getter
    @Setter
    @NotBlank
    private String name;
}
```

### 4. Service Interface (`src/main/java/mil/tron/commonapi/service/PrivilegeService.java`)
```java
public interface PrivilegeService {
    Iterable<PrivilegeDto> getPrivileges();
    void deletePrivilege(Privilege privilege);
}
```

### 5. Service Implementation (`src/main/java/mil/tron/commonapi/service/PrivilegeServiceImpl.java`)
```java
@Service
public class PrivilegeServiceImpl implements PrivilegeService {
    private static final DtoMapper MODEL_MAPPER = new DtoMapper();
    
    private PrivilegeRepository privilegeRepo;
    
    public PrivilegeServiceImpl(PrivilegeRepository privilegeRepo) {
        this.privilegeRepo = privilegeRepo;
    }
    
    @Override
    public Iterable<PrivilegeDto> getPrivileges() {
        return StreamSupport.stream(privilegeRepo.findAll().spliterator(), false)
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }
    
    private PrivilegeDto convertToDto(Privilege privilege) {
        return MODEL_MAPPER.map(privilege, PrivilegeDto.class);
    }
}
```

### 6. Controller (see section 3)

### 7. Liquibase Migration
Create changeset in `src/main/resources/db/` following existing pattern. Added automatically to `db.changelog-master.xml`.

For complex migrations, generate diff:
```bash
# Run current master with production profile
./mvnw spring-boot:run -Pproduction

# Checkout your branch, then:
./mvnw -Dliquibase.url=jdbc:postgresql://localhost:5432/<db_name> \
       -Dliquibase.username=<db_username> \
       -Dliquibase.password=<db_password> \
       liquibase:diff
```

## 5. Common Gotchas

### Security / Auth
- Development profile has security **disabled** by default
- Auth is based on `x-forwarded-client-cert` header (provided by ISTIO in prod)
- For local auth testing:
  - Use jwt-cli-utility proxy: `node proxy.js 9000 8088`
  - Set admin email in `admin.jwt` to `ckumabe.ctr@revacomm.com`
- Env var `SECURITY_ENABLED` overrides profile default

### Profiles
- **development**: H2, port 8088, security off (default)
- **production**: PostgreSQL, port 8080, security on
- **local**: Custom overrides in `application-local.properties` (gitignored)
- Set via `-Pproduction` or env var `spring_profiles_active=production`

### Required Environment Variables (production)
- `PGHOST`, `PGPORT`, `PG_DATABASE`, `PG_USER`, `APP_DB_ADMIN_PASSWORD` (database)
- `CONTEXTS` (liquibase contexts)
- Optional: `SECURITY_ENABLED`, `ENCLAVE_LEVEL`

### App Source Config
Create `src/main/resources/appsourceapis/appSourceConfig.local.json` (gitignored) for local app source testing. Copy from `appSourceConfig.example.json`.

### MinIO (Document Storage)
Not enabled by default in dev. To enable:
```bash
docker run --name common-api-minio -p 9002:9002 -p 9003:9003 \
  -e "MINIO_ROOT_USER=admin" -e "MINIO_ROOT_PASSWORD=adminpass" \
  quay.io/minio/minio server /data --console-address ":9003" --address :9002
```
Set `minio.enabled=true` in `application-development.properties`. Create bucket `testbucket` at `http://localhost:9003`.

### Lombok
Ensure Lombok annotation processing is enabled in your IDE. Config: `lombok.config`.

### Spring Boot DevTools
Triggers restart on class changes. Can interfere with debugging. Exclude with `spring.devtools.restart.enabled=false`.

### Port Conflicts
Check for existing dev servers before starting. Default ports: 8088 (dev), 8080 (prod).

## 6. Useful One-Liners

### Find all controllers
```bash
find src/main/java -name "*Controller.java"
```

### Find all entities
```bash
find src/main/java/mil/tron/commonapi/entity -name "*.java"
```

### Find all repositories
```bash
find src/main/java -name "*Repository.java"
```

### Grep for specific annotation
```bash
grep -r "@RestController" src/main/java
```

### List all REST endpoints
```bash
grep -r "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping" src/main/java
```

### Run tests with coverage
```bash
./mvnw clean test jacoco:report
# Report: target/site/jacoco/index.html
```

### Skip tests during build
```bash
./mvnw clean install -DskipTests
```

### Run with specific Postgres DB
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="\
--PGHOST=localhost \
--PGPORT=5432 \
--PG_DATABASE=commonapi \
--PG_USER=admin \
--APP_DB_ADMIN_PASSWORD=password" -Pproduction
```

### Clean build artifacts
```bash
./mvnw clean
rm -rf target/
```

### Generate Liquibase diff (see section 4.7)
```bash
./mvnw liquibase:diff -Dliquibase.url=<url> -Dliquibase.username=<user> -Dliquibase.password=<pass>
```

### Check dependency tree
```bash
./mvnw dependency:tree
```

### Find Spring component by name
```bash
grep -r "class YourClassName" src/main/java
```
