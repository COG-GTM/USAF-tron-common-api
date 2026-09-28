# Modernization slice: ranks

Vertical slice under characterization: `GET /api/{v1,v2}/rank/**` — controller, service, repository,
JPA entity, Liquibase seed data, and the three cross-cutting pieces every rank response passes
through (security filter chain, response-envelope advice, error-attribute mapping).

## Why this slice

- Bounded: four `GET` endpoints, one service, one repository, one entity, one DTO, no writes
  (`src/main/java/mil/tron/commonapi/controller/ranks/RankController.java:25-94`).
- Behavior-heavy for its size: path-variable normalization, two error paths with distinct
  messages, two response shapes (enveloped vs. bare), category assignment by pay-grade prefix,
  numeric-not-lexical ordering, and null-vs-list semantics in the categorized DTO.
- Runs on a plain Linux box with Maven and the H2 `development`+`test` profiles: the data is
  Liquibase-seeded from CSV into the in-memory H2 database
  (`src/main/resources/db/changelog/diff-changelog-1.00.010.xml:138-139`,
  `src/main/resources/db/changelog/diff-changelog-1.00.040.xml:4`,
  `src/main/resources/db/changelog/diff-changelog-1.00.027.xml:11-17`); no PostgreSQL, S3/MinIO,
  or network calls are involved (`src/main/resources/application-test.properties`).
- Touches every layer the framework lift changes: `javax.persistence` on the entity
  (`src/main/java/mil/tron/commonapi/entity/ranks/Rank.java:7-10`), Spring Data derived queries
  (`src/main/java/mil/tron/commonapi/repository/ranks/RankRepository.java:13-14`),
  `WebSecurityConfigurerAdapter`-based authorization
  (`src/main/java/mil/tron/commonapi/security/WebSecurityConfig.java:41-65`),
  `ResponseBodyAdvice` (`src/main/java/mil/tron/commonapi/controller/advice/ResponseWrapperAdvice.java:38-104`),
  Boot `DefaultErrorAttributes` subclassing
  (`src/main/java/mil/tron/commonapi/exception/custom/TronCommonErrorAttributes.java:12-16`),
  Jackson serialization, Liquibase + H2 seed loading.

## Inputs and outputs

| Endpoint | Input | 200 body | Error |
|---|---|---|---|
| `GET /v1/rank`, `GET /v2/rank` | none | `{"data":[Rank...]}` (all 160 seeded ranks) | — |
| `GET /{v}/rank/{branch}` | `branch` path var, any case | `{"data":[Rank...]}` filtered to that branch | 404 `Unknown Branch Name` |
| `GET /{v}/rank/{branch}/categorized` | `branch` | bare `RankCategorizedDto` | 404 `Unknown Branch Name` |
| `GET /{v}/rank/{branch}/{abbreviation}` | `branch`, `abbreviation` (any case, URL-encoded) | bare `Rank` | 404 `Unknown Branch Name` / `<BRANCH> Rank '<abbr>' does not exist.` |

`Rank` JSON: `{"abbreviation","name","payGrade","branchType"}` — `id` is `@JsonIgnore`
(`Rank.java:28-29`). Error JSON: `{"timestamp","status","error","errors","reason","path"}` in that
order (`TronCommonAppError.java:92-97`); `timestamp` is an ISO-8601 string with a `+00:00` offset
(Boot's default `WRITE_DATES_AS_TIMESTAMPS=false` applied to `java.util.Date`).

Seeded data used as golden inputs (`src/main/resources/db/seed.1.0.10/ranks.csv`,
`src/main/resources/db/seed.1.0.40/ranks.csv`, `diff-changelog-1.00.027.xml:11-17`):
USAF 25, USA 28, USN 28, USMC 28, USCG 26, USSF 22, OTHER 3 (CIV/GS, CTR/N/A, Unk/Unk) = 160.

## Invariants pinned

Confidence: **High** = read directly from code and confirmed by an existing test or the seed data;
**Medium** = read from code but depends on framework defaults that the lift is known to touch;
**Low** = observed behavior whose specification is implicit.

### Endpoint contract (`RankEndpointCharacterizationTest`, full embedded server, security disabled)

| # | Invariant | Evidence | Confidence |
|---|---|---|---|
| E1 | `GET /v1/rank` and `/v2/rank` return 200 and the same `{"data":[...]}` envelope containing all 160 ranks | `RankController.java:25,40-43`; `ResponseWrapperAdvice.java:38,104`; seed CSVs | High |
| E2 | Every serialized rank has exactly `abbreviation,name,payGrade,branchType`; `id` never appears | `Rank.java:18-46` | High |
| E3 | `GET /{branch}` filters to the branch; per-branch counts 25/28/28/28/26/22/3 | `RankController.java:56-58`; `RankServiceImpl.java:32`; seed CSVs | High |
| E4 | Branch path variable is case-insensitive (`usaf`, `USAF`, `UsAf` identical) | `RankController.java:91` | High |
| E5 | Unknown branch → 404, `error=Not Found`, `reason=Unknown Branch Name`, `errors=null`, `path` echoed, `timestamp` ISO-8601 `+00:00` | `RankController.java:93`; `RecordNotFoundException.java:6`; `TronCommonAppError.java:78,92-97`; `application.properties:23-25` | High |
| E6 | `GET /{branch}/{abbreviation}` returns the bare rank (no envelope), matched case-insensitively, echoing stored casing | `RankController.java:84-86` (no `@WrappedEnvelopeResponse`); `RankRepository.java:14` | High |
| E7 | Same abbreviation resolves per branch (`usaf/Capt` → O-3, `usn/Capt` → CAPT O-6) | `RankRepository.java:14`; seed rows | High |
| E8 | Missing rank → 404 `reason="<Branch.name()> Rank '<abbreviation as requested>' does not exist."` | `RankServiceImpl.java:107` | High |
| E9 | Branch validity is checked before abbreviation lookup (`/nope/Capt` → `Unknown Branch Name`) | `RankController.java:86` (argument evaluation order) | High |
| E10 | `/{branch}/categorized` returns a bare DTO with keys `enlisted,warrantOfficer,officer,civilService,other` in declaration order, no `data` key | `RankController.java:70-72`; `RankCategorizedDto.java:16-20` | Medium (Jackson property order) |
| E11 | Empty category serializes as JSON `null` (key present) | `RankServiceImpl.java:76-80` | High |
| E12 | Enlisted/officer/warrant lists sorted numerically by pay-grade digits (E-9 < E-10, O-10 < O-11), ties keep DB order | `RankServiceImpl.java:71-73,85-97` | High (order) / Medium (tie order = insertion order) |
| E13 | GS*/SES → civilService, anything else (CTR, N/A, Unk) → other; neither list is sorted | `RankServiceImpl.java:63-68` | High |
| E14 | Categorized lists partition the branch's ranks exactly (every rank once) | `RankServiceImpl.java:37-68` | High |
| E15 | Literal `categorized` segment wins over `{abbreviation}` for any branch value | Spring MVC pattern specificity; `RankController.java:70,84` | High |
| E16 | Trailing slash is accepted (`/v1/rank/`, `/v1/rank/usn/` → 200) | Spring 5.3 `useTrailingSlashMatch=true` default; existing `RankControllerTests.java` uses `/v1/rank/` | Medium — Spring 6 flips the default; PR #4 restored it via `PathMatchConfigurer` |
| E17 | `POST /v1/rank` → 405, `Allow: GET`, `error=Method Not Allowed`, `reason=Request method 'POST' not supported` | Spring MVC `DefaultHandlerExceptionResolver`; `TronCommonAppError.java:78` | Medium — message text is framework-owned |
| E18 | Unmapped sub-path (`/v1/rank/usaf/Capt/extra`) → 404 with `reason=No message available` | Boot 2.x `DispatcherServlet` `sendError(404)` path; `TronCommonAppError.java:78` | Medium — Boot 3 throws `NoHandlerFoundException` by default |
| E19 | Non-ASCII branch (`us%C3%A4f`) → 404 `Unknown Branch Name`, `path` keeps the encoded URI | `RankController.java:91-93`; servlet `request_uri` attribute | Low |

### Service rules (`RankServiceCharacterizationTest`, repository mocked)

| # | Invariant | Evidence | Confidence |
|---|---|---|---|
| S1 | Prefix rules: `E-`→enlisted, `W-`→warrant, `O-`→officer, `GS`/`SES`→civilService, else other | `RankServiceImpl.java:46-68` | High |
| S2 | Prefix test is case-insensitive; values are returned unchanged | `RankServiceImpl.java:46` | High |
| S3 | Uniformed prefixes need the hyphen (`E5`, `WO1`, `O5` → other); civil-service prefixes do not (`GS13` → civilService) | `RankServiceImpl.java:48-63` | High |
| S4 | Empty categories are `null`, never empty lists; a branch with no ranks yields five nulls | `RankServiceImpl.java:76-80` | High |
| S5 | Sort key = integer formed from all digits in the pay grade; numeric not lexical | `RankServiceImpl.java:17,87-90` | High |
| S6 | Sort is stable (equal keys keep repository order) | `List.sort` contract | High |
| S7 | civilService and other are never sorted | `RankServiceImpl.java:71-73` (only three lists sorted) | High |
| S8 | Every digit counts (`O-2A1` → 21); no digits → 0; overflow → 0 | `RankServiceImpl.java:87-96` | High |
| S9 | `getRanks()` = `findAll()`; `getRanks(branch)` = `findAllByBranchType`, no sorting | `RankServiceImpl.java:26-33` | High |
| S10 | `getRank(UUID)` not found → `Rank resource with ID: <id> does not exist.` | `RankServiceImpl.java:102` | High |
| S11 | `getRank(abbr, branch)` passes the raw abbreviation to the ignoring-case query; not found → `<BRANCH> Rank '<abbr>' does not exist.` | `RankServiceImpl.java:107` | High |

### Authorization (`RankAuthorizationCharacterizationTest`, MockMvc, `security.enabled=true`)

| # | Invariant | Evidence | Confidence |
|---|---|---|---|
| A1 | No `x-forwarded-client-cert` header → 403 on all five rank routes, empty body | `WebSecurityConfig.java:52-53` (`anyRequest().authenticated()`); pre-auth filter yields no principal → `Http403ForbiddenEntryPoint` | High |
| A2 | Unknown app-client namespace → 403 | `AppClientUserPreAuthenticatedService.java:74-76` (`UsernameNotFoundException`) | High |
| A3 | Malformed XFCC (no `URI=` pair) → 403 | `AppClientPreAuthFilter.java:47-75` returns null principal | High |
| A4 | Gateway namespace (`istio-system`) without a bearer token → 403 (falls to app-client lookup of `istio-system`) | `AppClientUserPreAuthenticatedService.java:56,74`; `AppClientPreAuthFilter.java:32-42` (`NoCredentials`) | High |
| A5 | Registered app client with **no** privileges → 200 on every rank route | `WebSecurityConfig.java:52-53`; no `@PreAuthorize` on `RankController` | High |
| A6 | App-client namespace matched case-insensitively | `AppClientUserRespository.java:15`; `App.java:50-51` | High |
| A7 | Authenticated caller still receives 404 for unknown branch/rank (authn does not mask routing errors) | `RecordNotFoundException.java:6` | High |
| A8 | CSRF disabled → `POST /v1/rank` with valid app client is 405 (`Allow: GET`), not 403 | `WebSecurityConfig.java:57-58` | High |
| A9 | Dashboard admin (SSO XFCC + JWT email) → 200; email matched case-insensitively | `AppClientUserPreAuthenticatedService.java:57-61`; `DashboardUserRepository.findByEmailIgnoreCase` | High |
| A10 | SSO user unknown to the dashboard → 200 (authenticated with empty authorities) | `AppClientUserPreAuthenticatedService.java:63-67` | High |
| A11 | Any authenticated principal (`@WithMockUser`, no authorities) → 200 | `WebSecurityConfig.java:52-53` | High |

### Pinned legacy defects (tests prefixed `pinnedDefect_`)

| # | Defect | Evidence | Confidence |
|---|---|---|---|
| D1 | `rank.pay_grade` is nullable in the schema, but the categorizer calls `getPayGrade().toUpperCase()` unconditionally, so one null row turns `/{branch}/categorized` into a 500 | `diff-changelog-1.00.010.xml:11`; `RankServiceImpl.java:46` | High |
| D2 | `Rank.branchType` has an initializer (`OTHER`) but no `@Builder.Default`, so `Rank.builder().build()` has a `null` branch while `new Rank()` has `OTHER` | `Rank.java:16,46` | High |

Neither defect is fixed by this slice; both are listed for a program decision in the PR.

## Characterization result on unmodified code (JDK 11, Spring Boot 2.5.12)

```
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 mvn -B test \
  -Dtest='RankEndpointCharacterizationTest*,RankServiceCharacterizationTest*,RankAuthorizationCharacterizationTest*'

RankEndpointCharacterizationTest       Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
RankServiceCharacterizationTest        Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
RankAuthorizationCharacterizationTest  Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
                                       Total:     71 passing
```

## Conversion and validation (JDK 21, Spring Boot 3.5.16)

The Maven build compiles the whole service at one Java/Boot level, so the lift is whole-repository,
applied as one-topic commits (build/toolchain, `javax`→`jakarta`, Spring Security 6, Actuator
`httpexchanges`, Hibernate 6/H2 2.x, trailing-slash matching, Boot 3 library APIs, test APIs).
The mechanics were reused from the closed whole-repository migration attempt (PR #4).

Running the unchanged characterization suite against the converted tree surfaced two invariants
that the framework itself no longer honours (both had been marked Medium above for that reason):

| Invariant | Boot 2.5.12 body | Boot 3.5.16 body (before fix) | Resolution |
|---|---|---|---|
| E17 | `reason=Request method 'POST' not supported` | `reason=Method 'POST' is not supported.` | `LegacyErrorMessages` maps `HttpRequestMethodNotSupportedException` back to the Boot 2 wording |
| E18 | `reason=No message available` | `reason=No static resource v1/rank/usaf/Capt/extra.` | `LegacyErrorMessages` maps `NoResourceFoundException` back to `No message available` |

The existing suite (second oracle) surfaced one more framework wording change outside the ranks
routes: Spring Security 6 raises `AuthorizationDeniedException("Access Denied")` for `@PreAuthorize`
failures where Spring Security 5 said `Access is denied`. That text reaches the JSON `reason` and,
through `TraceErrorAdvice`, the persisted `http_logs.response_body`
(`HttpTraceIntegrationTest.testNewTraceIsAdded` asserts on it). `LegacyErrorMessages` maps it back
as well, so that test also runs unchanged. All three mappings live in
`src/main/java/mil/tron/commonapi/exception/custom/LegacyErrorMessages.java` and are unit-tested by
`LegacyErrorMessagesTest` and `TronCommonErrorAttributesTest`.

E16 (trailing slash) is kept by `WebConfig.configurePathMatch` (`setUseTrailingSlashMatch(true)`).

One characterization file needed a version-neutral edit that touches no assertion:
`RankEndpointCharacterizationTest` reads the random port through `@Value("${local.server.port}")`
because `@LocalServerPort` moved packages between Boot 2.5 and Boot 3. The edited file was re-run
against unmodified `master` on JDK 11 (71/71) before being run on the converted tree.

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B test \
  -Dtest='RankEndpointCharacterizationTest*,RankServiceCharacterizationTest*,RankAuthorizationCharacterizationTest*'

RankEndpointCharacterizationTest       Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
RankServiceCharacterizationTest        Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
RankAuthorizationCharacterizationTest  Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
                                       Total:     71 passing (unchanged assertions)
```

Full suite as second oracle: `master` on JDK 11 — 949 run, 948 pass, 1 fail; converted tree on
JDK 21 — the same 949 pre-existing tests, 948 pass, 1 fail (same test,
`DocumentSpaceFileSystemServiceTests.propagateModificationStateOnlyDoesOlderAncestors`, a
wall-clock-dependent assertion that fails on `master` before any change here), plus 71
characterization tests and 8 new unit tests for the wording shim (`TronCommonErrorAttributesTest`,
`LegacyErrorMessagesTest`). No pre-existing test changed outcome.

## Out of scope

`RankService.getRank(UUID)` is exercised only via `PersonService` and has no HTTP route; it is pinned
at the service level (S10) but not through HTTP. HTTP trace persistence for rank requests
(`TraceRequestFilter`) is covered by the existing `HttpTraceIntegrationTest` and is not re-pinned here.
