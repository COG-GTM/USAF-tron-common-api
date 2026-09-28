# 03 — Candidate vertical slices for a first tested modernization step

Head commit inspected: `7b22ec561c2625ff1200156d058fcdd0ff022e69`. Evidence and confidence conventions: `00-inventory.md`.

## 0. Selection constraints and how they were checked

| Constraint (from the tasking) | How it was verified on this checkout |
|---|---|
| Builds and tests on a plain Linux box with Maven | Baseline `mvn -B -DCI_COMMIT_SHORT_SHA=7b22ec56 test` on JDK 11 completes (949 tests, 1 failure — `02-risk-register.md` R-08). Only requirement outside the repo is a Maven mirror if Central rate-limits |
| H2 profile only | Default `development` profile (`pom.xml:388-393`) → H2 in-memory `MODE=PostgreSQL` (`src/main/resources/application-development.properties:3`); tests use `application-test.properties` on top of it |
| No external services | Candidate must not require MinIO/S3 (`minio.enabled=false` in `src/main/resources/application-test.properties:13`), a Puckboard host, a webhook listener, or app-source upstreams. Slices whose *service* publishes events are acceptable only if the publisher is mocked in existing tests |
| Handler counts | Same parser as `00-inventory.md` §5.1 (method-level `@*Mapping` annotations in `@RestController` classes; class-level `@RequestMapping` excluded), so per-slice totals reconcile with the inventory's per-controller figures |
| Test counts | `grep -c "@Test"` per test file (JUnit 5 `@Test`, JUnit 4 `@Test` for PowerMock classes). Counts are **per annotated method**, not parameterised expansions. Confidence High for the count, Medium for "exercises the slice" (attribution is by file name / package, not by coverage) |
| Security-enabled coverage | Only 10 test classes set `security.enabled=true` (`grep -rl "security.enabled=true" src/test/java`): `AppClientIntegrationTest`, `AppSourceIntegrationTest`, `DocumentSpaceIntegrationTests`, `EntityFieldAuthIntegrationTests`, `HttpTraceIntegrationTest`, `InputFuzzer`, `JsonDbIntegrationTest`, `OrgRelationshipIntegrationTest`, `PubSubPrivsTest`, `ScratchStorageIntegrationTest` (all under `src/test/java/mil/tron/commonapi/integration/`) |

"Modernization step" here means: move the slice's code to the target stack conventions (Jakarta imports, Security 6 authorization declaration, Hibernate 6-safe persistence, JUnit 5/Mockito-only tests) **behind characterization tests**, while the rest of the service stays on the current stack. Because this is a single-module Maven project with one Boot parent (`pom.xml:5-9`), a slice cannot literally run on Boot 3 while the rest runs on Boot 2.5 in the same JVM; the slice is therefore the *unit of pinning and rewrite*, and the stack flip happens for the whole module at `lift-build`. That ordering is program decision D-03 in `04-decisions.md`.

## 1. Candidates

### S-1 Rank reference data (read-only) — **recommended**

| Aspect | Detail | Evidence |
|---|---|---|
| Scope | 4 GET handlers under `/{v1,v2}/rank` returning ranks by branch/abbreviation; seeded reference data | `src/main/java/mil/tron/commonapi/controller/ranks/RankController.java:25,41-85` |
| Files (main) | 7: `controller/ranks/RankController.java`, `service/ranks/RankService.java`, `service/ranks/RankServiceImpl.java`, `repository/ranks/RankRepository.java`, `entity/ranks/Rank.java`, `dto/rank/RankCategorizedDto.java`, `dto/rank/RankResponseWrapper.java` (plus the shared `entity/branches/Branch.java` enum) | `find src/main/java -ipath '*rank*' -name '*.java'` → 7 |
| Why bounded | No writes, no events, no authorization annotation beyond the chain-level `authenticated()` (`WebSecurityConfig.java:52-53`); depends only on Liquibase CSV seeds (`src/main/resources/db/seed.1.0.40/`) and H2. Exercises exactly the risky seams — `javax.persistence` entity (`Rank.java:8-10`), Spring Data repository, Liquibase `loadData` on H2, controller returning **entities directly** (`RankController.java:42,57,85` return `Iterable<Rank>`/`Rank`), and the v1/v2 dual mapping | inline above |
| Existing tests | **15**: `controller/ranks/RankControllerTests.java` (8, `@SpringBootTest`+MockMvc with `@MockBean` service), `service/ranks/RankServiceImplTest.java` (7, Mockito) | `grep -c @Test` |
| Gaps → characterization tests to write first | (1) end-to-end test through H2 with the real Liquibase seed asserting the exact JSON shape of `GET /v2/rank/{branch}` (entity is the contract, so field order/nullability must be pinned); (2) OpenAPI fragment snapshot for the 4 operations — taken from the `common-api-v2` springdoc group (`/v3/api-docs/common-api-v2`), which is the only named group whose path list includes `rank/**` (`src/main/java/mil/tron/commonapi/SpringdocConfig.java:55-70`); the v1 `common-api` group omits rank paths (`SpringdocConfig.java:22-39`), and `dashboard-api-v2` matches `${api-prefix.v2}/**` (`SpringdocConfig.java:74-78`), so the v1 `GET /v1/rank/**` operations appear only in the ungrouped document (`/v3/api-docs`) — the snapshot must name the group (or ungrouped document) it was taken from; (3) security-enabled test proving an unauthenticated request is 401/403 and an `APP_CLIENT` principal is allowed; (4) seed-data checksum test (row count per branch) to detect Liquibase/H2 behaviour changes (R-06) | inline above |
| Risks touched | R-02, R-05, R-06, R-16 directly; R-03 indirectly (chain-level only) | inline above |
| Confidence | High that it meets all constraints. Raise: run the 2 test classes in isolation on the target JDK once `lift-build` starts | inline above |

### S-2 Scratch storage (JSON key-value + JsonDb)

| Aspect | Detail | Evidence |
|---|---|---|
| Scope | 41 handlers on `ScratchStorageController` (`/{v1,v2}/scratch`): app registry, key/value CRUD, JSON-path reads/patches, per-app user ACLs | `src/main/java/mil/tron/commonapi/controller/scratch/ScratchStorageController.java:45` (1,082 lines) |
| Files (main) | 25 under `*/scratch/`: 1 controller, 4 services (`ScratchStorageServiceImpl` 889 lines, `JsonDbServiceImpl` 410 lines), 4 repositories, 4 entities, 3 exceptions, DTOs | `find src/main/java -ipath '*scratch*' -name '*.java'` → 25 |
| Why bounded | Self-contained tables (`scratch_storage*`), no S3/webhooks/Camel; external libraries are `json-path` (`ScratchStorageServiceImpl.java:7-10`) and Jackson only. However it embeds its **own authorization model** (`ScratchStorageAppUserPriv`, `digitize` app mapping in `AppClientUserPreAuthenticatedService.java:113-156`) and the `KEY`/`VALUE` column names that broke on H2 2.x in PR #4 | inline above |
| Existing tests | **95**: `service/scratch/ScratchStorageServiceImplTest.java` (34), `controller/scratch/ScratchStorageControllerTest.java` (29), `integration/ScratchStorageIntegrationTest.java` (20, security-enabled), `service/scratch/JsonDbTests.java` (5), `integration/JsonDbIntegrationTest.java` (7, security-enabled) | `grep -c @Test` |
| Gaps → characterization tests first | (1) authorization matrix for the 41 handlers with `security.enabled=true` (only 27 of 95 tests run secured); (2) H2 2.x compatibility probe for `KEY`/`VALUE` columns; (3) JSON-path edge cases (PR #5 NPE class) as regression fixtures | inline above |
| Risks touched | R-02, R-03, R-04, R-05, R-06 | inline above |
| Confidence | High on constraints; Medium on boundedness (largest controller in the service after document space; couples to the pre-auth service). Raise: JaCoCo run restricted to the 5 test classes to measure line coverage of the 25 files | inline above |

### S-3 Dashboard users + privileges (admin identity)

| Aspect | Detail | Evidence |
|---|---|---|
| Scope | `DashboardUserController` (7 handlers, `/{v1,v2}/dashboard-users`; v1 and v2 list endpoints are separate methods), `PrivilegeController` (2 handlers, v1/v2 list); the SSO → `DashboardUser` → authorities path used by the filter chain | `src/main/java/mil/tron/commonapi/controller/DashboardUserController.java:27,47-151`, `src/main/java/mil/tron/commonapi/controller/PrivilegeController.java:40,52`, `src/main/java/mil/tron/commonapi/service/AppClientUserPreAuthenticatedService.java:80-90` |
| Files (main) | `DashboardUserController`, `DashboardUserService(+Impl)`, `DashboardUserRepository`, `entity/DashboardUser`, `dto/DashboardUserDto`, `PrivilegeController`, `PrivilegeService(+Impl)`, `PrivilegeRepository`, `entity/Privilege`, `dto/PrivilegeDto` (12) plus the pre-auth service | `find … -iname '*DashboardUser*' -o -iname '*Privilege*.java'` (filtered) |
| Why bounded | Two small tables, no events, no S3; **but** it is the substrate of R-03/R-04: `@PreAuthorizeDashboardAdmin`/`@PreAuthorizeDashboardUser` (`DashboardUserController.java:46,59`) and chain rules for Actuator/Puckboard (`WebSecurityConfig.java:49-51`) resolve through it. Modernizing it means re-declaring `AuthenticationUserDetailsService` wiring for Security 6 | inline above |
| Existing tests | **44**: `service/DashboardUserServiceImplTest.java` (19), `controller/DashboardUserControllerTest.java` (12), `entity/DashboardUserTest.java` (1), `controller/PrivilegeControllerTest.java` (3), `service/PrivilegeServiceImplTest.java` (2), `service/AppClientUserPreAuthenticatedServiceTest.java` (7) | `grep -c @Test` |
| Gaps → characterization tests first | (1) security-enabled MockMvc tests injecting `x-forwarded-client-cert` + bearer JWT headers for admin / user / unknown-email principals and asserting 200/403 per handler; (2) test that an unknown SSO email yields an authenticated principal with **zero** authorities (`AppClientUserPreAuthenticatedService.java:90`); (3) fixture of the `Privilege` seed rows | inline above |
| Risks touched | R-03, R-04, R-13 head-on; R-02, R-05 | inline above |
| Confidence | High on constraints; Medium on "first step" suitability — highest behavioural risk per line of code, so better as the **second** slice once the harness from S-1 exists | inline above |

### S-4 Organization CRUD (+ EFA, parent/child relationships)

| Aspect | Detail | Evidence |
|---|---|---|
| Scope | 17 handlers on `OrganizationController`; service of 1,339 lines with EFA field protection, subordinate/parent graph, JSON Patch | `src/main/java/mil/tron/commonapi/controller/OrganizationController.java`, `src/main/java/mil/tron/commonapi/service/OrganizationServiceImpl.java` |
| Files (main) | controller, service pair, `OrganizationUniqueChecksService`, repository (+ specification filter), `entity/Organization`, ~10 DTOs, EFA service | `01-architecture.md` §2.2 |
| Why bounded / not | Runs on H2 with no external services — **but** it publishes pub/sub events (`EventManagerService`, mocked in unit tests), has a `@Lazy` circular dependency with `PersonService` (`.windsurfrules` convention), and is exactly where PR #4 hit Hibernate 6 transient-instance failures. Its unit test uses **PowerMock** (`src/test/java/mil/tron/commonapi/service/OrganizationServiceImplTest.java:44-64`, `@RunWith(PowerMockRunner.class)`), so the test itself must be rewritten before the slice can run on JDK 17+ | inline above |
| Existing tests | **115**: `service/OrganizationServiceImplTest.java` (44, PowerMock/JUnit 4), `controller/OrganizationControllerTest.java` (30), `integration/OrganizationIntegrationTest.java` (14), `integration/OrgRelationshipIntegrationTest.java` (14, secured), `entity/OrganizationTest.java` (11), `dto/OrganizationDtoTest.java` (1), `service/utility/OrganizationUniqueChecksServiceImplTest.java` (1); plus `integration/EntityFieldAuthIntegrationTests.java` (7, secured, shared with Person) | `grep -c @Test` |
| Gaps → characterization tests first | (1) port the 44 PowerMock tests to Mockito 4/5 `mockStatic` (R-07) — this is rework, not characterization; (2) Hibernate SQL snapshot for the parent/subordinate cascade paths; (3) EFA matrix (which privilege may change which `@ProtectedField`) | inline above |
| Risks touched | R-02, R-05 (highest), R-07, R-03/R-04 (EFA) | inline above |
| Confidence | High on constraints; **Low on boundedness** — too many seams for a first step. Raise: none; recommended as a later slice | inline above |

### S-5 Person CRUD — considered and **not** recommended as first slice

Same profile as S-4 (`PersonController` 14 handlers, `PersonServiceImpl` 576 lines, publishes events, EFA, `@Lazy` cycle with organizations, PII fields) with **75** tests (`controller/PersonControllerTest.java` 26, `service/PersonServiceImplTest.java` 27, `integration/PersonIntegrationTest.java` 15, `entity/PersonTests.java` 5, `service/utility/PersonUniqueChecksServiceImplTest.java` 2). It additionally depends on `Rank` (S-1) for rank resolution and on Puckboard ETL semantics (PR #6). Bounded enough for a second wave, not the first.

### Rejected outright (fail the "no external services" constraint)

| Area | Why rejected | Evidence |
|---|---|---|
| Document space | Requires S3-compatible storage. The test profile defaults to `minio.enabled=false`, but the document-space tests override it to `true` and boot an in-JVM `s3mock` (archived upstream) instead: `DocumentSpaceIntegrationTests` (`@SpringBootTest(properties = {"security.enabled=true", ..., "minio.enabled=true"})`), `DocumentSpaceControllerTest`, `AccessCheckDocumentSpaceImplTest`. So the slice *can* be loaded under test; it is rejected because its behaviour is defined against an S3 API mock, not H2 alone, and the mock library is unmaintained (`00-inventory.md` dependency #4; storage client itself is R-11) | `src/main/java/mil/tron/commonapi/annotation/minio/IfMinioEnabledOnIL4OrDevLocal.java:15`, `pom.xml:45-50`, `src/main/resources/application-test.properties:13`, `src/test/java/mil/tron/commonapi/integration/DocumentSpaceIntegrationTests.java:71-73`, `grep -rl "minio.enabled=true" src/test/java` → 3 files |
| Pub/sub delivery | Needs an HTTP listener for webhooks; only the ledger/subscriber CRUD is H2-only | `src/main/java/mil/tron/commonapi/pubsub/EventPublisher.java:200-218` |
| App-source gateway | Camel routes proxy to upstream URLs; health pings hit the network (`AppSourceIntegrationTest.testHealthChecks` is environment-dependent, R-08) | `src/main/java/mil/tron/commonapi/appgateway/GatewayRoute.java:9`, `src/main/java/mil/tron/commonapi/health/AppSourceHealthIndicator.java:161` |
| Puckboard ETL | Pulls from a Puckboard API over HTTP | `src/main/resources/application.properties:45-46` |

## 2. Recommendation

**Start with S-1 (Rank reference data), immediately followed by S-3 (Dashboard users + privileges).**

Rationale, in order of weight:

1. S-1 crosses every mechanical seam of the lift (Jakarta persistence, Spring Data/Hibernate, Liquibase seed on H2, dual v1/v2 mapping, entity-as-contract, springdoc output) with 7 main files and 15 tests, and no PowerMock, events or external calls. It is the cheapest place to build the **characterization harness** (H2 + Liquibase end-to-end test, OpenAPI snapshot, security-enabled MockMvc fixture) that every later slice reuses.
2. S-3 then applies that harness to the behaviour that PR #4 showed can change silently (R-03/R-04) while the code volume is still small.
3. S-2 and S-4/S-5 follow once PowerMock is removed and the Hibernate 6 SQL diff is available.

Confidence in the recommendation: High for S-1's suitability, Medium for the ordering after it (depends on D-03/D-04 in `04-decisions.md` — whether authorization or data semantics is the program's first preservation priority).

## 3. Summary table

| Slice | Main files | Handlers | Existing tests (secured) | External deps | PowerMock | First-step fit |
|---|---:|---:|---:|---|---|---|
| S-1 Rank | 7 | 4 | 15 (0) | none | no | **Best** |
| S-2 Scratch | 25 | 41 | 95 (27) | none | no | Good, large |
| S-3 Dashboard users/privileges | 12 (+1) | 9 | 44 (0) | none | no | Good, security-critical |
| S-4 Organization | ~18 | 17 | 115 (21) | events (mocked) | **yes** | Later |
| S-5 Person | ~15 | 14 | 75 (7) | events (mocked), ranks | no | Later |

Counts derived on the checkout with `find`/`grep -c "@Test"`; handler counts exclude class-level `@RequestMapping`. Confidence: High (counts) / Medium (attribution of shared integration classes to a single slice).
