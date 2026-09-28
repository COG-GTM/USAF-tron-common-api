# 00 — Inventory (current state)

**Inspected head commit:** `7b22ec561c2625ff1200156d058fcdd0ff022e69` (`master`).
All `path:line` references below point at this commit. Line numbers were taken with `grep -n` / `sed -n` on a clean checkout; nothing in the repository was modified to produce this document.

Conventions used in every document of this set:

- **Evidence** — every factual claim carries `path:line` or `path:line-line`. Counts derived by a command state the command.
- **Confidence** — **High** = read directly from source/build files or produced by a reproducible command on this checkout; **Medium** = derived by heuristic (regex over source, external release pages) or depends on environment; **Low** = inference. Each finding says what would raise it.

Headline numbers (derivation in §3, §5 and §7):

| Metric | Value | Confidence |
|---|---|---|
| Java source files | **530** (426 main, 103 test, 1 other) | High (`cloc`) |
| Total code LOC, all languages | **72,741** (Java 48,501) | High (`cloc`) |
| REST endpoints (statically mapped handler methods) | **193** handler methods / 255 declared path variants (v1+v2), plus a runtime-generated gateway surface | Medium (regex; see §5.1) |
| Declared third-party dependencies past end-of-support | **34** of 48 declared artifacts (15 upstream product lines) | Medium (see §7) |

---

## 1. Build

### 1.1 Maven layout

| Item | Evidence | Finding | Confidence |
|---|---|---|---|
| Single-module Maven project, no `<modules>` element | `pom.xml:1-416` (no `<modules>`), artifact `mil.tron:common-api` `pom.xml:12-15` | One module; the artifact version is `${CI_COMMIT_SHORT_SHA}` (`pom.xml:14`), so any local build must pass `-DCI_COMMIT_SHORT_SHA=<value>` | High |
| Parent | `pom.xml:5-9` | `spring-boot-starter-parent` **2.5.12** | High |
| Java level | `pom.xml:18` (`<java.version>11</java.version>`) | Source/target **11** via the Boot parent's compiler defaults; no explicit `maven-compiler-plugin` block | High |
| Version properties | `pom.xml:17-25` | logback 1.2.10, log4j2 2.17.1, camel 3.5.0, spring-security 5.5.4, tomcat 9.0.62, aws.sdk 1.12.68 | High |
| Maven wrapper | `.mvn/wrapper/maven-wrapper.properties:1` | Maven **3.6.3** distribution | High |
| Profiles | `pom.xml:388-414` | `development` (default, active by default), `production`, `local`; the profile id is injected into `spring.profiles.active` via resource filtering (`src/main/resources/application.properties:40`) | High |
| Final jar name | `pom.xml:280` | `common-api.jar`, consumed by all three Dockerfiles | High |

### 1.2 Build plugins

| Plugin | Version | Evidence | Note |
|---|---|---|---|
| `spring-boot-maven-plugin` | Boot-managed (2.5.12) | `pom.xml:289-292` | repackage |
| `jacoco-maven-plugin` | 0.8.6 | `pom.xml:293-311` | `prepare-agent` + `report` at `test` phase |
| `maven-dependency-plugin` | Boot-managed | `pom.xml:312-330` | `copy-dependencies` into `target/classes/lib` at `package` |
| `liquibase-maven-plugin` | 4.3.1 | `pom.xml:331-355` | plugin-scoped deps: `liquibase-hibernate5` 4.3.1 (`pom.xml:339-343`), `spring-boot-starter-data-jpa` **2.3.12.RELEASE** (`pom.xml:344-348`), `javax.validation:validation-api` 2.0.1.Final (`pom.xml:349-353`) — the plugin runs against an older Boot line than the application |
| `maven-checkstyle-plugin` | 3.1.2 with `checkstyle` 8.30 | `pom.xml:356-384` | `check` goal at `process-classes` (`pom.xml:370-373`); inline `checkstyleRules` containing only an empty `Checker` module at severity `warning` (`pom.xml:376-383`), i.e. **no style rules are actually enforced** |

Confidence: High (read from `pom.xml`).

### 1.3 Container images

| File | Base image(s) | Evidence | Behaviour |
|---|---|---|---|
| `Dockerfile` | hardened-registry `harden-openjdk11-jre:11.0.11` | `Dockerfile:2` | runtime only; copies `target/common-api.jar` (`Dockerfile:11`); JVM flags `-Xms512m -Xmx1024m` (`Dockerfile:13`); `ENV CONTEXTS DEV` (`Dockerfile:4`) selects Liquibase contexts (`src/main/resources/application.properties:41`) |
| `Dockerfile.local` | `centos:7` (wget source stage) + hardened-registry `maven-jdk11:3.6.3` | `Dockerfile.local:2`, `Dockerfile.local:6` | builds in-image with `mvn package -Dmaven.test.skip=true` (`Dockerfile.local:15`) |
| `Dockerfile.fullstack` | hardened-registry `maven-jdk11:3.6.3` | `Dockerfile.fullstack:2` | writes an extra changelog `diff-changelog-1.00.999.xml` seeding a dashboard-admin user (`Dockerfile.fullstack:9-19`), strips the datasource URL from production properties (`Dockerfile.fullstack:22`), builds with tests skipped (`Dockerfile.fullstack:28`) |

Confidence: High. `centos:7` is itself past end-of-life (June 2024) — see `02-risk-register.md` R-14.

### 1.4 CI discovery

| Check | Command | Result | Confidence |
|---|---|---|---|
| GitHub Actions | `ls -a .github` | no `.github/` directory | High |
| GitLab CI | `ls .gitlab-ci.yml` | absent at repository root; the artifact version placeholder `${CI_COMMIT_SHORT_SHA}` (`pom.xml:14`) and the hardened-registry images (`Dockerfile:2`) indicate the original upstream pipeline lived outside this fork | High (absence) / Medium (origin inference) |
| Pre-commit / husky | `ls .pre-commit-config.yaml .husky` | absent | High |
| `package.json` | `package.json:1-12` | only a no-op `test:e2e-ci` script; no JS application code | High |
| Checkstyle | `pom.xml:356-384` | `check` goal bound to `process-classes` (`pom.xml:370-373`) but with an empty rule set (`pom.xml:378-382`) — effectively no quality gate in-repo | High |

Consequence: **no CI runs on pull requests in this fork** (also recorded in PR #4's follow-ups). Raising confidence: none needed — this is an absence check.

---

## 2. Top-level packages under `mil.tron.commonapi`

Counts were produced on the checkout with:

```bash
cd src/main/java/mil/tron/commonapi
for d in */; do
  find $d -name '*.java' | wc -l                                            # files
  grep -rl -E '^@RestController|^@Controller' $d | wc -l                     # controllers
  grep -rl -E '^@Service|^@Component' $d | wc -l                             # services/components
  grep -rl -E 'extends (JpaRepository|CrudRepository|PagingAndSortingRepository)' $d | wc -l   # repositories
  grep -rl '^@Entity' $d | wc -l                                             # entities
done
```

| Package | Files | Ctrl | Svc | Repo | Entity | Purpose (one line) | Evidence |
|---|---|---|---|---|---|---|---|
| `annotation` | 23 | 0 | 0 | 0 | 0 | Custom `@PreAuthorize*` security meta-annotations, response-envelope and JSON-Patch markers, MinIO conditional | `src/main/java/mil/tron/commonapi/annotation/security/PreAuthorizeDashboardAdmin.java:8-10`, `annotation/minio/IfMinioEnabledOnIL4OrDevLocal.java:15` |
| `appgateway` | 8 | 0 | 3 | 0 | 0 | Builds Apache Camel routes and dynamic Spring MVC mappings for proxied "app sources" from OpenAPI files | `appgateway/AppGatewayRouteBuilder.java:20-36`, `appgateway/AppSourceEndpointsBuilder.java:66-118` |
| `controller` | 26 | 25 | 1 | 0 | 0 | REST surface (21 `@RestController`, 1 gateway `@Controller`, 3 `@ControllerAdvice`) | `controller/AppGatewayController.java:22`, `controller/advice/ExceptionHandlerAdvice.java:24` |
| `dto` | 134 | 0 | 0 | 0 | 0 | Request/response DTOs, v2 response wrappers, filter DTOs | directory listing |
| `entity` | 42 | 0 | 0 | 0 | 28 | JPA entities (people, orgs, app clients/sources, document space, pub/sub, scratch, KPI, ranks) | §4.1 |
| `exception` | 25 | 0 | 0 | 0 | 0 | Domain exceptions and `ExceptionResponse` | directory listing |
| `health` | 2 | 0 | 1 | 0 | 0 | Per-app-source Actuator `HealthIndicator` and custom status aggregator | `health/AppSourceHealthIndicator.java:32,150-185`, `health/CustomStatusAggregator.java:13-14,32-33` |
| `logging` | 1 | 0 | 1 | 0 | 0 | `CommonApiLogger` marker class used for Commons-Logging lookups | `logging/CommonApiLogger.java` |
| `pubsub` | 12 | 0 | 2 | 0 | 0 | Event ledger, async webhook publisher with HMAC signing, event types | `pubsub/EventPublisher.java:95-100,144-145,200-218`, `pubsub/EventManagerServiceImpl.java:51-91` |
| `repository` | 33 | 0 | 0 | 26 | 0 | Spring Data JPA repositories + JPA `Specification` filter builder | `repository/ranks/RankRepository.java:12` |
| `security` | 13 | 0 | 0 | 0 | 0 | `WebSecurityConfigurerAdapter`-based filter chain, XFCC/JWT pre-auth filter, `AccessCheck*` SpEL beans | `security/WebSecurityConfig.java:19-76`, `security/AppClientPreAuthFilter.java:16-45` |
| `service` | 82 | 0 | 38 | 0 | 0 | Business logic; interface + `Impl` pairs incl. document space, scratch storage, puckboard, webdav | directory listing; e.g. `service/ranks/RankServiceImpl.java:15-16` |
| `validations` | 15 | 0 | 0 | 0 | 0 | JSR-303 custom validators (DoD ID, phone, subscriber address, …) | directory listing |
| *(root)* | 10 | 1 | 0 | 0 | 0 | `CommonApiApplication`, `ApplicationProperties`, `DocumentSpaceConfig` (S3 client), `CacheConfig`, `MetricsConfig`, `SpringdocConfig`, `WebConfig`, `RootPathController` | `src/main/java/mil/tron/commonapi/DocumentSpaceConfig.java:17-62`, `RootPathController.java:7` |

Confidence: High for file counts; Medium for role counts (regex on leading annotations; a `@Service` placed after another annotation on the same line would be missed). Raise by running an annotation-processor or `jdeps`-style scan.

---

## 3. Lines of code

Tool: **cloc 1.90** (`cloc --version` → `1.90`). Command, run from the repository root on the inspected commit:

```bash
cloc . --exclude-dir=target,.git --quiet
```

| Language | Files | Blank | Comment | Code |
|---|---|---|---|---|
| Java | 530 | 10,146 | 5,651 | **48,501** |
| YAML | 4 | 1 | 0 | 16,447 |
| JSON | 17 | 0 | 0 | 3,482 |
| XML | 56 | 142 | 2 | 2,521 |
| Markdown | 4 | 172 | 0 | 755 |
| Maven (`pom.xml`) | 1 | 9 | 0 | 407 |
| Bourne Shell (`mvnw`) | 1 | 33 | 62 | 227 |
| CSV | 14 | 0 | 0 | 210 |
| DOS Batch (`mvnw.cmd`) | 1 | 35 | 0 | 147 |
| Dockerfile | 3 | 13 | 11 | 44 |
| **Total** | **631** | 10,551 | 5,726 | **72,741** |

Java split (`cloc src/main/java --quiet` / `cloc src/test/java --quiet`): main **426 files / 22,135 LOC**, test **103 files / 26,290 LOC** — the test tree is larger than production code. The YAML total is dominated by the four app-source OpenAPI definitions under `src/main/resources/appsourceapis/` (`arms-gateway.yml`, `expr-arms-gateway.yml`, `mock.yml`, `puckboard.yml`).

Confidence: High (reproducible command; note that Markdown count excludes this `docs/modernization/` set, which did not exist at the inspected commit).

---

## 4. Persistence layer

### 4.1 JPA entities (28)

`grep -rl '^@Entity' src/main/java | wc -l` → 28. Grouped by sub-package of `src/main/java/mil/tron/commonapi/entity/`:

| Group | Entities |
|---|---|
| core | `AppClientUser`, `DashboardUser`, `HttpLogEntry`, `MeterValue`, `Organization`, `OrganizationMetadata`, `Person`, `PersonMetadata`, `Privilege` |
| `appsource/` | `AppEndpoint`, `AppEndpointPriv`, `AppSource` |
| `documentspace/` | `DocumentSpace`, `DocumentSpaceFileSystemEntry`, `DocumentSpacePrivilege`, `DocumentSpaceUserCollection`, `metadata/FileSystemEntryMetadata` |
| `kpi/` | `KpiSummary`, `ServiceMetric`, `UniqueVisitorCount` |
| `pubsub/` | `PubSubLedger` (`entity/pubsub/PubSubLedger.java:18-44`; message column length 2,097,152 at `:44`), `Subscriber`, `log/EventRequestLog` |
| `ranks/` | `Rank` (`entity/ranks/Rank.java:17-19`) |
| `scratch/` | `ScratchStorageAppRegistryEntry`, `ScratchStorageAppUserPriv`, `ScratchStorageEntry`, `ScratchStorageUser` |

All entities use `javax.persistence.*` (e.g. `entity/ranks/Rank.java` imports; `pom.xml:135-139` declares `javax.persistence-api` 2.2 explicitly). Confidence: High.

### 4.2 Repositories (26)

`grep -rl -E 'extends (JpaRepository|CrudRepository|PagingAndSortingRepository)' src/main/java | wc -l` → 26, all under `repository/`. Filtering uses a JPA `Specification` builder (`repository/filter/SpecificationBuilder.java`), which PR #4 had to rewrite for Hibernate 6 (see §8). Confidence: High.

### 4.3 Liquibase

| Item | Evidence | Finding |
|---|---|---|
| Master changelog | `src/main/resources/db/db.changelog-master.xml:8` | `<includeAll path="db/changelog/" />` — every file in the directory is applied in filename order |
| Changelog files | `ls src/main/resources/db/changelog \| wc -l` | **55** files, `diff-changelog-1.00.001.xml` … `diff-changelog-1.00.055.xml` |
| Changesets | `grep -o '<changeSet' -r src/main/resources/db/changelog \| wc -l` | **282** |
| Seed data | `db/changelog/diff-changelog-1.00.002.xml:11-17` (`<loadData>` in a `test`-context changeset), CSV directories `src/main/resources/db/seed/`, `db/seed.1.0.10/`, `db/seed.1.0.40/` (14 CSV files per `cloc`) | people/org/rank seeds are loaded by Liquibase `loadData`, i.e. reference data (ranks) is part of the schema history |
| Schema ownership | `src/main/resources/application.properties:5` (`spring.jpa.hibernate.ddl-auto=none`) | schema is migration-driven only |
| Contexts | `application.properties:41` (`spring.liquibase.contexts=${CONTEXTS}`), `application-production.properties:4` (`production`), `Dockerfile:4` (`DEV`) | context selection is an environment variable |
| Diff tooling | `pom.xml:331-355`, `src/main/resources/db/liquibase-development.properties`, `db/liquibase-production.properties` | `liquibase-hibernate5` extension pins the diff tool to Hibernate 5 dialect classes |
| Schema XSD | `db.changelog-master.xml:6` | `dbchangelog-3.8.xsd` referenced while the runtime is Liquibase 4.3.1 |

Confidence: High.

### 4.4 PostgreSQL vs H2 profiles

| Concern | Default / production (PostgreSQL) | `development` (H2) | `test` (H2, via development profile) |
|---|---|---|---|
| Driver / URL | `jdbc:postgresql://${PGHOST}:${PGPORT}/${PG_DATABASE}` `application.properties:14` (production adds `sslmode=require` `application-production.properties:5`) | `jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` `application-development.properties:3` | inherits development (`pom.xml:388-393` default profile); `application-test.properties` only overrides flags |
| Dialect | `PostgreSQL82Dialect` `application.properties:7` | `H2Dialect` `application-development.properties:1-2` | same as development |
| Credentials | `${PG_RW_USER}` / `${APP_DB_RW_PASSWORD}` `application.properties:15-16`; Liquibase uses `${PG_USER}` / `${APP_DB_ADMIN_PASSWORD}` `application-production.properties:2-3` | `sa` / empty `application-development.properties:5-6` | same |
| H2 console | — | enabled `application-development.properties:8` | — |
| Security | `security.enabled=true` `application.properties:76` | `security.enabled=true` `application-development.properties:19` (README describes toggling via `SECURITY_ENABLED`) | **`security.enabled=false`** `application-test.properties:16` — method security is not loaded (`security/MethodSecurityConfig.java:19`) unless a test re-enables it (10 test classes do; see `03-slice-candidates.md` §0) |
| MinIO/S3 | enabled with env placeholders `application-production.properties:7-12` | enabled against `http://localhost:9002` `application-development.properties:39-44` | disabled `application-test.properties:13` |
| H2 artifact scope | — | `com.h2database:h2` is **test-scoped** (`pom.xml:159-164`) while the `development` profile needs it at runtime; PR #4 noted it had to become a runtime dependency | |

Confidence: High. Note `application.properties:12-13` still uses `spring.datasource.initialization-mode` / `spring.datasource.platform`, which were removed in Boot 2.7/3.0 (PR #4 renamed them).

---

## 5. Interfaces and integrations

### 5.1 REST surface (static)

Count method: a small Python script (not committed) parsed every `@GetMapping/@PostMapping/@PutMapping/@PatchMapping/@DeleteMapping/@RequestMapping` on methods under `src/main/java`, expanding class-level `@RequestMapping` arrays and method-level path arrays.

| Measure | Value |
|---|---|
| Handler methods (static) | **193** (192 REST + `RootPathController` redirect `src/main/java/mil/tron/commonapi/RootPathController.java:7`) |
| Declared path variants (v1/v2 arrays expanded) | 255 |
| By verb | GET 101 · POST 40 · PUT 9 · PATCH 12 · DELETE 29 · generic `@RequestMapping` 2 |
| Class-level `@RequestMapping` | 12 |
| Largest controllers | `DocumentSpaceController` 43 handlers (`controller/documentspace/DocumentSpaceController.java:55`), `ScratchStorageController` 41 (`controller/scratch/ScratchStorageController.java:44`), `OrganizationController` 17, `AppSourceController` 15, `PersonController` 14 |
| Context path / prefixes | `server.servlet.context-path=/api` and `api-prefix.v1=/v1`, `api-prefix.v2=/v2` `src/main/resources/application.properties:18-21` |
| OpenAPI | springdoc at `/api-docs`, Swagger UI at `/api-docs/index` `application.properties:1-3`; permitted anonymously `security/WebSecurityConfig.java:45-47` |
| Dynamic surface | `AppGatewayController` has 0 static mappings (`controller/AppGatewayController.java:22`); paths are registered at startup by `AppSourceEndpointsBuilder` from OpenAPI files (`appgateway/AppSourceEndpointsBuilder.java:162-179`) — **not included in 193** |

Confidence: Medium. Raise by booting the app with the `development` profile and diffing `/api/api-docs` against this count (springdoc will also show the gateway routes).

### 5.2 Apache Camel

| Item | Evidence |
|---|---|
| Dependencies | `camel-spring-boot-starter`, `camel-jackson`, `camel-servlet`, `camel-http` at `${camel.version}`=3.5.0 (`pom.xml:236-255`); `camel-core` test-scoped (`pom.xml:209-215`); `camel-test`, `camel-test-spring` **compile**-scoped (`pom.xml:216-225`) |
| Routes | One `GatewayRoute` per app source, created programmatically (`appgateway/AppGatewayRouteBuilder.java:20-36`); the route consumes from `generateAppSourceRouteUri(appSourcePath)` (`appgateway/GatewayRoute.java`) and forwards to the app source URL over `camel-http` |
| Static route definitions | none — `grep -rn "extends RouteBuilder" src/main/java` returns only `GatewayRoute` |

Confidence: High.

### 5.3 AWS SDK / S3 (document space)

| Item | Evidence |
|---|---|
| SDK | `aws-java-sdk-bom` 1.12.68 (`pom.xml:29-35`), `aws-java-sdk-s3` (`pom.xml:51-54`) — **v1 SDK** |
| Client | `AmazonS3ClientBuilder` against an S3-compatible (MinIO) endpoint with path-style access; `TransferManager` bean (`src/main/java/mil/tron/commonapi/DocumentSpaceConfig.java:38-62`) |
| Activation | `@IfMinioEnabledOnIL4OrDevLocal` = `minio.enabled && (enclave.level=='IL4' \|\| profile in {development, local})` (`annotation/minio/IfMinioEnabledOnIL4OrDevLocal.java:15`); applied to `DocumentSpaceConfig`, the three document-space controllers, `DocumentSpaceServiceImpl`, `DocumentSpacePrivilegeServiceImpl`, `WebDavServiceImpl` |
| Config | `minio.*` + `aws-default-region` (`application.properties:124-129`); note the malformed placeholder `@Value("${aws-default-region")` (missing `}`) at `DocumentSpaceConfig.java:34` |
| Test double | `io.findify:s3mock_2.13` 0.2.6 (`pom.xml:45-50`) used by document-space tests |
| Upload limit | `spring.servlet.multipart.max-file-size=40000MB` (`application.properties:131`) |

Confidence: High.

### 5.4 Puckboard ETL

| Item | Evidence |
|---|---|
| Controller | `controller/puckboard/PuckboardEtlController.java:21-22` maps `${api-prefix.v1}/puckboard` and `${api-prefix.v2}/puckboard`; handlers at `:51-111` |
| Service | `service/puckboard/PuckboardExtractorServiceImpl.java` (organisation/person conversion from Puckboard JSON) |
| Upstream URLs | cluster-local service DNS names hard-coded as defaults `application.properties:45-46`; development points at `http://localhost:8099/…` `application-development.properties:16` |
| Security | `/puckboard/**` requires `DASHBOARD_ADMIN` `security/WebSecurityConfig.java:50-51` |
| Prior work | PR #6 (open) guards unknown Puckboard rank ids in this service |

Confidence: High.

### 5.5 Pub/sub webhooks

| Item | Evidence |
|---|---|
| Ledger | `pubsub/EventManagerServiceImpl.java:51-91` persists a `PubSubLedger` row then hands off to `EventPublisher` |
| Publisher | `@Async` publish `pubsub/EventPublisher.java:95-100`; bounded in-memory queue `webhook-queue-max-size` (`EventPublisher.java:55-59`, default 1,000,000 `application.properties:116`); `@Scheduled(fixedDelayString="${webhook-delay-ms}")` consumer `EventPublisher.java:144-145` (50 ms `application.properties:109`); per-message HMAC signature header + `RestTemplate` POST `EventPublisher.java:200-218`; send timeout `webhook-send-timeout-secs=5` `application.properties:112` |
| Subscribers | `entity/pubsub/Subscriber.java` (address, event type, secret, eager `AppClientUser`) |
| Delivery log | `entity/pubsub/log/EventRequestLog.java:20-30` |
| Prior work | PR #1 (open) binds subscription creation to the calling app client and stops returning secrets |

Confidence: High.

### 5.6 Actuator

| Item | Evidence |
|---|---|
| Exposure | `management.endpoints.web.exposure.include=health,logfile,httptrace` `application.properties:68`; development adds `metrics` `application-development.properties:35` |
| `httptrace` | enabled with body tracing `application.properties:69-71`; HTTP path denied at the filter chain `security/WebSecurityConfig.java:48` while the trace repository backs `HttpLogsController`; **removed/renamed in Boot 3 (`httpexchanges`)** — PR #4 rewrote `HttpTraceService`/`TraceRequestFilter` |
| Health | components/details always shown, DOWN/FATAL/OUT_OF_SERVICE mapped to HTTP 200 `application.properties:55-59`; custom `AppSourceHealthIndicator` per app source (`health/AppSourceHealthIndicator.java`), pinged every `app-source-ping-rate-millis=60000` `application.properties:122` |
| Access | `/actuator/health/**` → `DASHBOARD_USER`, `/actuator/logfile` → `DASHBOARD_ADMIN` `security/WebSecurityConfig.java:49-50` |

Confidence: High.

---

## 6. Test inventory (baseline facts used by later documents)

| Item | Command / evidence | Value |
|---|---|---|
| Test classes | `cloc src/test/java` | 103 files |
| Test methods | `grep -rE '^\s*@(Test\|ParameterizedTest\|RepeatedTest)\b' src/test/java \| wc -l` | 948 annotations; Surefire executed **949** tests |
| JUnit 5 | 99 files import `org.junit.jupiter` | JUnit 4 (`junit:junit`, `pom.xml:231-235`) is on the classpath for PowerMock only |
| PowerMock | `grep -rl powermock src/test/java` | 2 files: `src/test/java/mil/tron/commonapi/repository/filter/SpecificationBuilderTest.java`, `src/test/java/mil/tron/commonapi/service/OrganizationServiceImplTest.java` |
| Mockito static mocking | `grep -rl mockStatic src/test/java` | 1 file: `src/test/java/mil/tron/commonapi/service/LogfileServiceImplTest.java` |
| Security-enabled tests | `grep -rl 'security.enabled=true' src/test/java` | 10 integration classes (listed in `03-slice-candidates.md` §0) |
| Baseline `mvn -B test` on `master` with JDK 11 | see `02-risk-register.md` R-08 | 949 run, **1 failure**, 0 errors, 0 skipped; 5 min 09 s wall time |

Confidence: High.

---

## 7. Third-party dependency inventory

Scope: every artifact declared in `pom.xml` `<dependencies>` and `<dependencyManagement>` (48 declared coordinates, `pom.xml:29-276`), plus the version-property-controlled transitive lines that the tasking called out (Tomcat, log4j2, logback, Spring Framework). Resolved versions come from `mvn -B -DCI_COMMIT_SHORT_SHA=7b22ec56 dependency:list -DincludeScope=test` run on this checkout (259 resolved artifacts). "Newest" is the `<release>` in each artifact's Maven Central `maven-metadata.xml` fetched 2026-09-28; support status cites the project's own policy page or `endoflife.date`.

**Definition of "past end-of-support" used for the headline:** the declared version's release line has (a) reached its published open-source end-of-support/EOL date, or (b) been declared closed/unmaintained by the project, or (c) the project repository is archived. Dormant-but-not-declared projects are *not* counted (they are flagged "dormant" and listed separately).

### 7.1 Declared artifacts

| # | Artifact (`pom.xml` lines) | Declared | Scope | Resolved | Newest (Central) | Support status of declared line | Past EOS? | Conf. |
|---|---|---|---|---|---|---|---|---|
| 1 | `org.springframework.boot:spring-boot-starter-parent` (`:5-9`) | 2.5.12 | parent | 2.5.12 | 2.5.15 (line) / 4.1.x | Boot 2.5 OSS EOL **2022-05-19**, commercial 2023-08-24 ([endoflife.date/spring-boot](https://endoflife.date/spring-boot)) | **Yes** | High |
| 2 | `com.amazonaws:aws-java-sdk-bom` (`:29-35`) | 1.12.68 | import | — | 1.12.797 (v1) / v2 `software.amazon.awssdk` 2.55.x | v1 end-of-support **2025-12-31** ([announcement](https://aws.amazon.com/blogs/developer/announcing-end-of-support-for-aws-sdk-for-java-v1-x-on-december-31-2025/)) | **Yes** | High |
| 3 | `com.jamesmurty.utils:java-xmlbuilder` (`:40-44`) | 1.3 | compile | 1.3 | 1.3 (2020) | no release since 2020; no EOL statement | dormant | Medium |
| 4 | `io.findify:s3mock_2.13` (`:45-50`) | 0.2.6 | test | 0.2.6 | 0.2.6 (2020) | upstream repository **archived** (GitHub API `archived=true`) | **Yes** | High |
| 5 | `com.amazonaws:aws-java-sdk-s3` (`:51-54`) | via BOM | compile | 1.12.68 | 1.12.797 | as #2 | **Yes** | High |
| 6 | `spring-boot-starter-validation` (`:55-59`) | **2.5.2** (pinned below parent) | compile | 2.5.2 | 4.1.x | as #1 | **Yes** | High |
| 7 | `spring-boot-starter-web` (`:60-64`) | **2.5.2** | compile | 2.5.2 | 4.1.x | as #1 | **Yes** | High |
| 8 | `commons-io:commons-io` (`:65-69`) | 2.9.0 | compile | 2.9.0 | 2.22.0 | rolling release, current line maintained | No (old) | High |
| 9 | `spring-boot-devtools` (`:70-75`) | managed | runtime | 2.5.12 | 4.1.x | as #1 | **Yes** | High |
| 10 | `org.projectlombok:lombok` (`:76-80`) | managed | compile | 1.18.22 | 1.18.48 | rolling | No (old) | High |
| 11 | `spring-boot-starter-test` (`:81-85`) | managed | test | 2.5.12 | 4.1.x | as #1 | **Yes** | High |
| 12 | `org.springdoc:springdoc-openapi-ui` (`:86-90`) | 1.5.0 | compile | 1.5.0 | 1.8.0 (v1) / `springdoc-openapi-starter-webmvc-ui` 3.1.x | v1 docs marked "no longer maintained"; 1.8.0 is last OSS v1 release ([springdoc v1 FAQ](https://springdoc.org/v1/faq.html)) | **Yes** | High |
| 13 | `spring-boot-starter-log4j2` (`:91-95`) | **2.5.2** | compile | 2.5.2 | 4.1.x | as #1 | **Yes** | High |
| 14 | `spring-boot-starter-aop` (`:96-100`) | **2.5.2** | compile | 2.5.2 | 4.1.x | as #1 | **Yes** | High |
| 15 | `spring-boot-starter-cache` (`:101-104`) | managed | compile | 2.5.12 | 4.1.x | as #1 | **Yes** | High |
| 16 | `jackson-dataformat-yaml` (`:105-109`) | 2.13.1 | compile | 2.13.1 | 2.22.x | 2.13 branch **closed Nov 2023** ([Jackson releases wiki](https://github.com/FasterXML/jackson/wiki/Jackson-Releases)) | **Yes** | High |
| 17 | `jackson-core` (`:110-114`) | 2.13.1 | compile | 2.13.1 | 2.22.x | as #16 | **Yes** | High |
| 18 | `jackson-databind` (`:115-119`) | 2.13.2.1 | compile | 2.13.2.1 | 2.22.3 | as #16 | **Yes** | High |
| 19 | `com.auth0:java-jwt` (`:120-124`) | 3.10.3 | compile | 3.10.3 | 4.6.1 | 4.x is the maintained major; no explicit 3.x EOL statement found | dormant line | Medium |
| 20 | `io.swagger.parser.v3:swagger-parser-v3` (`:125-129`) | 2.0.24 | compile | 2.0.24 | 2.1.48 | rolling | No (old) | High |
| 21 | `commons-validator:commons-validator` (`:130-134`) | 1.7 | compile | 1.7 | 1.11.0 | rolling | No (old) | High |
| 22 | `javax.persistence:javax.persistence-api` (`:135-139`) | 2.2 | compile | 2.2 | 2.2 (2017); successor `jakarta.persistence-api` 3.x | `javax.*` namespace frozen since the Jakarta EE transfer; no further releases | **Yes** | High |
| 23 | `spring-boot-starter-data-jpa` (`:140-143`) | managed | compile | 2.5.12 (Spring Data JPA 2.5.10) | 4.1.x | as #1 | **Yes** | High |
| 24 | `net.minidev:json-smart` (`:144-148`) | 2.4.7 | compile | 2.4.7 | 2.6.0 | rolling | No (old) | High |
| 25 | `org.hibernate:hibernate-core` (`:149-153`) | 5.4.32.Final | compile | 5.4.32.Final | 5.4.33.Final (line) / `org.hibernate.orm:hibernate-core` 7.x | 5.4 **end-of-life** ([hibernate.org 5.4](https://hibernate.org/orm/releases/5.4/)) | **Yes** | High |
| 26 | `org.postgresql:postgresql` (`:154-158`) | managed | runtime | 42.2.25 | 42.7.13 | project active; 42.2.x line superseded, no formal EOL | No (old) | Medium |
| 27 | `com.h2database:h2` (`:159-164`) | 1.4.200 | **test** | 1.4.200 | 2.5.x | maintainers state all 1.x releases are end-of-life; 1.4.200 carries unfixed CVEs ([h2 issue #3360](https://github.com/h2database/h2database/issues/3360)) | **Yes** | High |
| 28 | `org.liquibase:liquibase-core` (`:165-169`) | 4.3.1 | compile | 4.3.1 | 5.0.4 | Liquibase 4 OSS EOL **2025-09-30** ([endoflife.date/liquibase](https://endoflife.date/liquibase)) | **Yes** | Medium |
| 29 | `com.github.blagerweij:liquibase-sessionlock` (`:170-174`) | 1.2.5 | compile | 1.2.5 | 1.6.9 | small project, releases through 2023 | No (old) | Medium |
| 30 | `com.google.guava:guava` (`:175-179`) | 30.1.1-jre | compile | 30.1.1-jre | 33.7.1-jre | rolling | No (old) | High |
| 31 | `org.modelmapper:modelmapper` (`:180-184`) | 2.3.0 | compile | 2.3.0 | 3.2.6 | 3.x current; no 2.x EOL statement | dormant line | Medium |
| 32 | `spring-boot-starter-actuator` (`:185-188`) | managed | compile | 2.5.12 | 4.1.x | as #1 | **Yes** | High |
| 33 | `spring-boot-starter-security` (`:189-192`) | managed; Security 5.5.4 via `pom.xml:22` | compile | 5.5.4 | 7.1.x | Spring Security 5.5 OSS EOL **2022-05-31** ([endoflife.date/spring-security](https://endoflife.date/spring-security)) | **Yes** | High |
| 34 | `spring-security-test` (`:193-197`) | 5.5.4 | test | 5.5.4 | 7.1.x | as #33 | **Yes** | High |
| 35 | `org.mockito:mockito-inline` (`:198-203`) | 3.9.0 | test | 3.9.0 (core 3.9.0) | 5.2.0 (artifact discontinued; inline is default in `mockito-core` 5+) | artifact retired; Mockito 3.x unmaintained | **Yes** | Medium |
| 36 | `com.github.java-json-tools:json-patch` (`:204-208`) | 1.12 | compile | 1.12 | 1.13 (2020) | last release 2020; repository not archived | dormant | Medium |
| 37 | `org.apache.camel:camel-core` (`:209-215`) | 3.5.0 | test | 3.5.0 | 4.22.x | Camel 3.x EOL **Dec 2024**; 3.5 was a non-LTS release ([Camel 3 EOL notice](https://camel.apache.org/blog/2024/12/camel3-eol/)) | **Yes** | High |
| 38 | `camel-test-spring` (`:216-220`) | 3.5.0 | **compile** | 3.5.0 | 4.22.x | as #37 | **Yes** | High |
| 39 | `camel-test` (`:221-225`) | 3.5.0 | **compile** | 3.5.0 | 4.22.x | as #37 | **Yes** | High |
| 40 | `com.jayway.jsonpath:json-path` (`:226-230`) | 2.6.0 | compile | 2.6.0 | 3.0.0 | rolling | No (old) | High |
| 41 | `junit:junit` (`:231-235`) | managed | test | 4.13.2 | 4.13.2 | JUnit 4 in maintenance; not declared EOL | No | Medium |
| 42 | `camel-spring-boot-starter` (`:236-240`) | 3.5.0 | compile | 3.5.0 | 4.22.x | as #37 | **Yes** | High |
| 43 | `camel-jackson` (`:241-245`) | 3.5.0 | compile | 3.5.0 | 4.22.x | as #37 | **Yes** | High |
| 44 | `camel-servlet` (`:246-250`) | 3.5.0 | compile | 3.5.0 | 4.22.x | as #37 | **Yes** | High |
| 45 | `camel-http` (`:251-255`) | 3.5.0 | compile | 3.5.0 | 4.22.x | as #37 | **Yes** | High |
| 46 | `springdoc-openapi-data-rest` (`:257-261`) | 1.5.8 | compile | 1.5.8 | 1.8.0 (v1) | as #12 | **Yes** | High |
| 47 | `org.powermock:powermock-module-junit4` (`:262-266`) | 2.0.9 | **compile** | 2.0.9 | 2.0.9 (Nov 2020) | no release in >5 years; no JDK 17+ support; removed by PR #4 | **Yes** (b) | Medium |
| 48 | `org.powermock:powermock-api-mockito2` (`:267-271`) | 2.0.9 | **compile** | 2.0.9 | 2.0.9 | as #47 | **Yes** (b) | Medium |
| 49 | `com.opencsv:opencsv` (`:272-276`) | 5.5.2 | compile | 5.5.2 | 5.12.0 | rolling | No (old) | High |

(#1 is the parent, not a `<dependency>`; the 48 declared coordinates are #2–#49.)

**Count past end-of-support: 34** (#2, 4, 5, 6, 7, 9, 11, 12, 13, 14, 15, 16, 17, 18, 22, 23, 25, 27, 28, 32, 33, 34, 35, 37, 38, 39, 42, 43, 44, 45, 46, 47, 48 = 33 dependency coordinates, plus the parent #1 = 34). Grouped by upstream product line: Spring Boot 2.5, Spring Security 5.5, Hibernate ORM 5.4, H2 1.x, Liquibase 4, Apache Camel 3, AWS SDK v1, Jackson 2.13, springdoc v1, `javax.persistence`, Mockito 3/`mockito-inline`, PowerMock, findify s3mock, plus (transitive, below) Spring Framework 5.3 and Java 11 commercial support = **15 lines**.

Confidence on the headline: Medium — the number depends on the definition above; PowerMock and `mockito-inline` are counted under criterion (b) without a formal vendor statement. Raise by adopting a program-approved definition and re-running the table.

### 7.2 Version-property-controlled and notable transitive lines

| Line | Declared via | Resolved | Newest | Status | Conf. |
|---|---|---|---|---|---|
| Spring Framework | Boot parent | 5.3.18 (`spring-core`, `spring-webmvc`) | 6.2.x / 7.0.x | 5.3 OSS EOL **2024-08-31** ([endoflife.date/spring-framework](https://endoflife.date/spring-framework)) | High |
| Tomcat embed | `pom.xml:23` | 9.0.62 | 9.0.122 / 11.0.x | Tomcat 9 supported until **2027-03-31** ([endoflife.date/tomcat](https://endoflife.date/tomcat)); declared patch is far behind | High |
| Log4j 2 | `pom.xml:20` | 2.17.1 | 2.26.x | 2.x line active; 2.17.1 is post-Log4Shell but 4+ years old | High |
| Logback | `pom.xml:19` | 1.2.10 | 1.6.x | 1.2 line superseded (last 1.2.13); both logback and log4j2 are on the runtime classpath (`dependency:list`) | Medium |
| Hibernate Validator | Boot parent | 6.2.3.Final | 9.x | Jakarta Validation 2.0 (`javax.validation`) API | High |
| Spring Data JPA | Boot parent | 2.5.10 | 4.x | follows Boot 2.5 EOL | High |
| HikariCP | Boot parent | 4.0.3 | 7.x | — | High |
| Caffeine | Boot parent | 2.9.3 | 3.x | — | High |
| `javax.servlet-api` | Boot parent | 4.0.1 | `jakarta.servlet-api` 6.x | `javax.*` namespace, replaced in Boot 3 | High |
| Mockito core | via PowerMock (**compile** scope) | 3.9.0 | 5.24.0 | test library leaks onto the runtime classpath | High |
| JDK | `pom.xml:18`, `Dockerfile:2` | 11 | 21/25 LTS | Java 11 vendor premier support ended **2023-09-30**; community builds continue to Oct 2027 ([endoflife.date/oracle-jdk](https://endoflife.date/oracle-jdk), [endoflife.date/eclipse-temurin](https://endoflife.date/eclipse-temurin)) | High |

### 7.3 Build-plugin versions

| Plugin | Declared (`pom.xml`) | Newest | Note |
|---|---|---|---|
| `jacoco-maven-plugin` | 0.8.6 (`:293-311`) | 0.8.15 | 0.8.6 cannot instrument class files newer than Java 15 |
| `liquibase-maven-plugin` | 4.3.1 (`:331-355`) | 5.0.4 | same EOL as `liquibase-core` |
| `liquibase-hibernate5` | 4.3.1 (`:339-343`) | 4.27.0 (last for Hibernate 5) | Hibernate 6 requires `liquibase-hibernate6` |
| `maven-checkstyle-plugin` / `checkstyle` | 3.1.2 / 8.30 (`:356-384`) | 3.6.0 / 14.x | Checkstyle 8.30 predates Java 17 syntax support |

Confidence: High for versions, Medium for the JaCoCo/Checkstyle capability notes (from release notes, not tested here).

---

## 8. Prior work on this fork (PRs #1–#6)

All six PRs target `master`. They are referenced, not duplicated, by this characterization.

| PR | State | Scope | Relevance to modernization |
|---|---|---|---|
| [#1](https://github.com/COG-GTM/USAF-tron-common-api/pull/1) | open | Pub/sub: bind subscription creation to the authenticated app client; stop returning webhook secrets | Changes `@PreAuthorizeSubscriptionCreation` semantics — an **authorization rule that must be pinned by characterization tests before any Security 6 rewrite** |
| [#2](https://github.com/COG-GTM/USAF-tron-common-api/pull/2) | open | Document space: scope favorites/entry-path endpoints to the target space | Same category: SpEL `@PreAuthorize` expressions are the behaviour to preserve |
| [#3](https://github.com/COG-GTM/USAF-tron-common-api/pull/3) | open | Document space mobile contents endpoint IDOR fix | Overlaps #2 on one endpoint |
| [#4](https://github.com/COG-GTM/USAF-tron-common-api/pull/4) | **closed** | Whole-repository migration to Java 21 / Spring Boot 3.5.16 | **Blast-radius evidence** — see §8.1 |
| [#5](https://github.com/COG-GTM/USAF-tron-common-api/pull/5) | open | Scratch storage: deny instead of NPE on missing/malformed ACL entries | Touches `ScratchStorageServiceImpl` authorization helpers |
| [#6](https://github.com/COG-GTM/USAF-tron-common-api/pull/6) | open | Puckboard ETL: guard unknown rank ids | Touches `PuckboardExtractorServiceImpl` |

Confidence: High (PR metadata read via the repository API on 2026-09-28).

### 8.1 What PR #4 tells us about the lift (not a completed migration)

From the PR #4 description and file list (217 files changed: 159 under `src/main`, 51 under `src/test`, plus build/Docker files):

| Dimension | PR #4 evidence | Implication |
|---|---|---|
| Namespace | `javax.{persistence,validation,servlet,transaction,annotation}` → `jakarta.*` "across main+test (bulk of the 200+ touched files)" | Mechanical but repository-wide; every entity, DTO validator and filter is touched |
| Security | `WebSecurityConfig`/`WebSecurityConfigDisabled` rewritten from `WebSecurityConfigurerAdapter` to `SecurityFilterChain` beans; `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity`; Security 6 rejects two `@PreAuthorize` on one method, so four `DocumentSpaceController` endpoints had `@PreAuthorizeOnlySSO` merged into a single SpEL expression | Authorization semantics are re-expressed by hand — the highest-risk behavioural change |
| Actuator | `httptrace` → `httpexchanges`; `HttpTraceService`/`TraceRequestFilter` re-based on new APIs; one test literal changed for Security 6's message text | Observable API/response text changes |
| Hibernate 6 | `SpecificationBuilder` rewritten (dropped `BasicPathUsageException`); explicit persist/detach added in `OrganizationServiceImpl`, `PersonServiceImpl`, document-space cascades because of stricter transient-instance checks; `PostgreSQL82Dialect` → `PostgreSQLDialect` | Persistence semantics differ in ways only caught by integration tests |
| H2 2.x | `KEY`/`VALUE` became reserved words → `NON_KEYWORDS=KEY,VALUE` on JDBC URLs | Test/dev database coupling |
| Boot 3 | `spring.datasource.initialization-mode/platform` → `spring.sql.init.*`; circular `RestTemplate` reference in `PuckboardEtlController` broken with `@Lazy`; `commons-logging` excluded from the AWS SDK; Caffeine declared explicitly | Configuration and wiring changes |
| Tests | PowerMock removed, Mockito 5; 949 tests, **2 failures on both JDK 11 `master` and the migration branch**: `AppSourceIntegrationTest.testHealthChecks`, `DocumentSpaceFileSystemServiceTests.propagateModificationStateOnlyDoesOlderAncestors` | The failures are pre-existing, but one of them is environment-dependent (see `02-risk-register.md` R-08) |
| Follow-ups it left | no CI on the repository; the two failures; hardened-image tag verification | Not a mergeable end state |

Confidence: High for what the PR states; Medium for the inference that the same seams will appear in an incremental approach (raise by re-running PR #4's branch build on this box).
