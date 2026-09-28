package mil.tron.commonapi.controller.ranks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for the rank endpoints, pinned against the seeded H2 database
 * (Liquibase changelogs 1.00.010, 1.00.027 and 1.00.040) through a real embedded server so that the
 * Spring Boot error handling path ({@code TronCommonErrorAttributes}) is exercised exactly as a
 * client would see it. See docs/modernization/slices/ranks.md for the invariant each test pins.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "efa-enabled=false", "security.enabled=false" })
@ActiveProfiles(value = { "development", "test" })
@AutoConfigureTestDatabase
public class RankEndpointCharacterizationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int TOTAL_SEEDED_RANKS = 160;
    private static final int USAF_SEEDED_RANKS = 25;
    private static final List<String> ERROR_BODY_KEYS =
            List.of("timestamp", "status", "error", "errors", "reason", "path");
    private static final String ISO_UTC_TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00";
    private static final List<String> RANK_KEYS = List.of("abbreviation", "name", "payGrade", "branchType");
    private static final List<String> CATEGORY_KEYS =
            List.of("enlisted", "warrantOfficer", "officer", "civilService", "other");

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api" + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api" + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) throws IOException {
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> values(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(node -> node.get(field).asText())
                .collect(Collectors.toList());
    }

    private static List<String> sortedTexts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::toString)
                .sorted()
                .collect(Collectors.toList());
    }

    private static void assertNotFoundBody(HttpResponse<String> response, String reason, String path) throws IOException {
        String raw = response.body();
        assertThat(response.statusCode()).as(raw).isEqualTo(404);
        assertThat(response.headers().firstValue("content-type")).as(raw).hasValue("application/json");
        JsonNode body = json(response);
        assertThat(fieldNames(body)).as(raw).containsExactlyElementsOf(ERROR_BODY_KEYS);
        assertThat(body.get("status").asInt()).as(raw).isEqualTo(404);
        assertThat(body.get("error").asText()).as(raw).isEqualTo("Not Found");
        assertThat(body.get("errors").isNull()).as(raw).isTrue();
        assertThat(body.get("reason").asText()).as(raw).isEqualTo(reason);
        assertThat(body.get("path").asText()).as(raw).isEqualTo(path);
        assertThat(body.get("timestamp").asText()).as(raw).matches(ISO_UTC_TIMESTAMP);
    }

    @Nested
    class AllRanks {

        @Test
        void v1ReturnsEveryStoredRankInsideDataEnvelope() throws Exception {
            HttpResponse<String> response = get("/v1/rank");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type")).hasValue("application/json");
            JsonNode body = json(response);
            assertThat(fieldNames(body)).containsExactly("data");
            assertThat(body.get("data").isArray()).isTrue();
            assertThat(body.get("data")).hasSize(TOTAL_SEEDED_RANKS);
        }

        @Test
        void v2ReturnsTheSameEnvelopeAsV1() throws Exception {
            JsonNode v1 = json(get("/v1/rank"));
            JsonNode v2 = json(get("/v2/rank"));

            assertThat(fieldNames(v2)).containsExactly("data");
            assertThat(sortedTexts(v2.get("data"))).isEqualTo(sortedTexts(v1.get("data")));
        }

        @Test
        void everyRankSerializesExactlyFourFieldsAndNeverItsId() throws Exception {
            JsonNode data = json(get("/v1/rank")).get("data");

            for (JsonNode rank : data) {
                assertThat(fieldNames(rank)).containsExactlyElementsOf(RANK_KEYS);
                assertThat(rank.get("abbreviation").isTextual()).isTrue();
                assertThat(rank.get("name").isTextual()).isTrue();
                assertThat(rank.get("payGrade").isTextual()).isTrue();
                assertThat(rank.get("branchType").isTextual()).isTrue();
            }
        }

        @Test
        void allRanksSpanEverySeededBranch() throws Exception {
            JsonNode data = json(get("/v1/rank")).get("data");

            assertThat(values(data, "branchType")).containsOnly(
                    "OTHER", "USA", "USAF", "USMC", "USN", "USSF", "USCG");
        }

        @Test
        void trailingSlashIsAcceptedAndReturnsTheSameEnvelope() throws Exception {
            HttpResponse<String> withSlash = get("/v1/rank/");

            assertThat(withSlash.statusCode()).isEqualTo(200);
            assertThat(json(withSlash).get("data")).hasSize(TOTAL_SEEDED_RANKS);
        }

        @Test
        void postIsRejectedWithMethodNotAllowed() throws Exception {
            HttpResponse<String> response = send("POST", "/v1/rank");

            assertThat(response.statusCode()).isEqualTo(405);
            assertThat(response.headers().firstValue("allow")).hasValue("GET");
            JsonNode body = json(response);
            assertThat(fieldNames(body)).containsExactlyElementsOf(ERROR_BODY_KEYS);
            assertThat(body.get("status").asInt()).isEqualTo(405);
            assertThat(body.get("error").asText()).isEqualTo("Method Not Allowed");
            assertThat(body.get("errors").isNull()).isTrue();
            assertThat(body.get("reason").asText()).isEqualTo("Request method 'POST' not supported");
            assertThat(body.get("path").asText()).isEqualTo("/api/v1/rank");
            assertThat(body.get("timestamp").asText()).matches(ISO_UTC_TIMESTAMP);
        }

        @Test
        void unmappedSubPathReturnsNotFoundWithNoMessage() throws Exception {
            HttpResponse<String> response = get("/v1/rank/usaf/Capt/extra");

            assertNotFoundBody(response, "No message available", "/api/v1/rank/usaf/Capt/extra");
        }
    }

    @Nested
    class RanksByBranch {

        @ParameterizedTest(name = "{0} has {1} ranks")
        @CsvSource({
                "usaf, 25",
                "usa, 28",
                "usn, 28",
                "usmc, 28",
                "uscg, 26",
                "ussf, 22",
                "other, 3"
        })
        void everyBranchReturnsOnlyItsOwnSeededRanks(String branch, int expectedCount) throws Exception {
            HttpResponse<String> response = get("/v1/rank/" + branch);

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(fieldNames(body)).containsExactly("data");
            assertThat(body.get("data")).hasSize(expectedCount);
            assertThat(values(body.get("data"), "branchType")).containsOnly(branch.toUpperCase());
        }

        @Test
        void branchPathVariableIsCaseInsensitive() throws Exception {
            List<String> lower = sortedTexts(json(get("/v1/rank/usaf")).get("data"));
            List<String> upper = sortedTexts(json(get("/v1/rank/USAF")).get("data"));
            List<String> mixed = sortedTexts(json(get("/v1/rank/UsAf")).get("data"));

            assertThat(lower).hasSize(USAF_SEEDED_RANKS);
            assertThat(upper).isEqualTo(lower);
            assertThat(mixed).isEqualTo(lower);
        }

        @Test
        void v2AndTrailingSlashReturnTheSameBranchEnvelope() throws Exception {
            List<String> v1 = sortedTexts(json(get("/v1/rank/usn")).get("data"));

            assertThat(sortedTexts(json(get("/v2/rank/usn")).get("data"))).isEqualTo(v1);
            assertThat(sortedTexts(json(get("/v1/rank/usn/")).get("data"))).isEqualTo(v1);
        }

        @Test
        void unknownBranchReturnsNotFoundWithUnknownBranchNameReason() throws Exception {
            HttpResponse<String> response = get("/v1/rank/doesnotexist");

            assertNotFoundBody(response, "Unknown Branch Name", "/api/v1/rank/doesnotexist");
        }

        @Test
        void branchWithNonAsciiLettersIsRejectedAsUnknownBranch() throws Exception {
            HttpResponse<String> response = get("/v1/rank/us%C3%A4f");

            assertNotFoundBody(response, "Unknown Branch Name", "/api/v1/rank/us%C3%A4f");
        }
    }

    @Nested
    class SingleRank {

        @Test
        void returnsTheBareRankWithoutEnvelope() throws Exception {
            HttpResponse<String> response = get("/v1/rank/usaf/Capt");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type")).hasValue("application/json");
            assertThat(response.body()).isEqualTo(
                    "{\"abbreviation\":\"Capt\",\"name\":\"Captain\",\"payGrade\":\"O-3\",\"branchType\":\"USAF\"}");
        }

        @Test
        void abbreviationLookupIsCaseInsensitiveAndReturnsStoredCasing() throws Exception {
            String canonical = get("/v1/rank/usaf/Capt").body();

            assertThat(get("/v1/rank/usaf/CAPT").body()).isEqualTo(canonical);
            assertThat(get("/v1/rank/usaf/capt").body()).isEqualTo(canonical);
            assertThat(get("/v2/rank/USAF/cApT").body()).isEqualTo(canonical);
        }

        @Test
        void sameAbbreviationResolvesPerBranch() throws Exception {
            HttpResponse<String> navy = get("/v1/rank/usn/Capt");

            assertThat(navy.statusCode()).isEqualTo(200);
            assertThat(navy.body()).isEqualTo(
                    "{\"abbreviation\":\"CAPT\",\"name\":\"Captain\",\"payGrade\":\"O-6\",\"branchType\":\"USN\"}");
        }

        @Test
        void abbreviationsContainingSpacesAreLookedUpUrlEncoded() throws Exception {
            HttpResponse<String> response = get("/v1/rank/usaf/2nd%20Lt");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(
                    "{\"abbreviation\":\"2nd Lt\",\"name\":\"Second Lieutenant\",\"payGrade\":\"O-1\",\"branchType\":\"USAF\"}");
        }

        @Test
        void unknownRankInKnownBranchEchoesBranchAndRequestedAbbreviation() throws Exception {
            HttpResponse<String> response = get("/v1/rank/usaf/nope");

            assertNotFoundBody(response, "USAF Rank 'nope' does not exist.", "/api/v1/rank/usaf/nope");
        }

        @Test
        void rankFromAnotherBranchIsNotFound() throws Exception {
            HttpResponse<String> response = get("/v1/rank/usaf/CMC");

            assertNotFoundBody(response, "USAF Rank 'CMC' does not exist.", "/api/v1/rank/usaf/CMC");
        }

        @Test
        void unknownBranchIsReportedBeforeAbbreviation() throws Exception {
            HttpResponse<String> response = get("/v1/rank/nope/Capt");

            assertNotFoundBody(response, "Unknown Branch Name", "/api/v1/rank/nope/Capt");
        }

        @Test
        void unknownRankAbbreviationInOtherBranchUsesEnumName() throws Exception {
            HttpResponse<String> response = get("/v2/rank/Other/Capt");

            assertNotFoundBody(response, "OTHER Rank 'Capt' does not exist.", "/api/v2/rank/Other/Capt");
        }
    }

    @Nested
    class Categorized {

        @Test
        void usafResponseIsAnUnwrappedDtoWithFiveCategoriesInDeclarationOrder() throws Exception {
            HttpResponse<String> response = get("/v1/rank/usaf/categorized");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type")).hasValue("application/json");
            JsonNode body = json(response);
            assertThat(fieldNames(body)).containsExactlyElementsOf(CATEGORY_KEYS);
            assertThat(body.has("data")).isFalse();
        }

        @Test
        void usafEnlistedAreSortedNumericallyByPayGradeWithTiesKept() throws Exception {
            JsonNode enlisted = json(get("/v1/rank/usaf/categorized")).get("enlisted");

            assertThat(values(enlisted, "payGrade")).containsExactly(
                    "E-1", "E-2", "E-3", "E-4", "E-5", "E-6", "E-7", "E-8", "E-9", "E-9", "E-9");
            assertThat(values(enlisted, "abbreviation").subList(0, 8)).containsExactly(
                    "AB", "Amn", "A1C", "SrA", "SSgt", "TSgt", "MSgt", "SMSgt");
            assertThat(values(enlisted, "abbreviation").subList(8, 11))
                    .containsExactlyInAnyOrder("CMSgt", "CCM", "CMSAF");
        }

        @Test
        void usafOfficersPlaceO10AfterO9NotAfterO1() throws Exception {
            JsonNode officer = json(get("/v1/rank/usaf/categorized")).get("officer");

            assertThat(values(officer, "payGrade")).containsExactly(
                    "O-1", "O-2", "O-3", "O-4", "O-5", "O-6", "O-7", "O-8", "O-9", "O-10", "O-10");
            assertThat(values(officer, "abbreviation").subList(0, 9)).containsExactly(
                    "2nd Lt", "1st Lt", "Capt", "Maj", "Lt Col", "Col", "Brig Gen", "Maj Gen", "Lt Gen");
            assertThat(values(officer, "abbreviation").subList(9, 11)).containsExactlyInAnyOrder("Gen", "GAF");
        }

        @Test
        void usafCivilServiceHoldsGsAndSesWhileContractorFallsToOther() throws Exception {
            JsonNode body = json(get("/v1/rank/usaf/categorized"));

            assertThat(values(body.get("civilService"), "payGrade")).containsExactlyInAnyOrder("GS", "SES");
            assertThat(values(body.get("civilService"), "abbreviation")).containsExactlyInAnyOrder("CIV", "SES");
            assertThat(values(body.get("other"), "abbreviation")).containsExactly("CTR");
            assertThat(values(body.get("other"), "payGrade")).containsExactly("CTR");
        }

        @Test
        void emptyCategoryIsSerializedAsJsonNull() throws Exception {
            JsonNode body = json(get("/v1/rank/usaf/categorized"));

            assertThat(body.has("warrantOfficer")).isTrue();
            assertThat(body.get("warrantOfficer").isNull()).isTrue();
        }

        @Test
        void otherBranchHasOnlyCivilServiceAndOther() throws Exception {
            JsonNode body = json(get("/v1/rank/other/categorized"));

            assertThat(body.get("enlisted").isNull()).isTrue();
            assertThat(body.get("warrantOfficer").isNull()).isTrue();
            assertThat(body.get("officer").isNull()).isTrue();
            assertThat(sortedTexts(body.get("civilService"))).containsExactly(
                    "{\"abbreviation\":\"CIV\",\"name\":\"Civilian\",\"payGrade\":\"GS\",\"branchType\":\"OTHER\"}");
            assertThat(sortedTexts(body.get("other"))).containsExactly(
                    "{\"abbreviation\":\"CTR\",\"name\":\"Contractor\",\"payGrade\":\"N/A\",\"branchType\":\"OTHER\"}",
                    "{\"abbreviation\":\"Unk\",\"name\":\"Unknown\",\"payGrade\":\"Unk\",\"branchType\":\"OTHER\"}");
        }

        @Test
        void usaWarrantOfficersAreSortedAndO11SortsAfterO10() throws Exception {
            JsonNode body = json(get("/v1/rank/usa/categorized"));

            assertThat(values(body.get("warrantOfficer"), "payGrade")).containsExactly(
                    "W-1", "W-2", "W-3", "W-4", "W-5");
            assertThat(values(body.get("warrantOfficer"), "abbreviation")).containsExactly(
                    "WO1", "CW2", "CW3", "CW4", "CW5");
            List<String> officerGrades = values(body.get("officer"), "payGrade");
            assertThat(officerGrades.get(officerGrades.size() - 1)).isEqualTo("O-11");
            assertThat(officerGrades.get(officerGrades.size() - 2)).isEqualTo("O-10");
            assertThat(values(body.get("officer"), "abbreviation").get(officerGrades.size() - 1)).isEqualTo("GA");
        }

        @Test
        void everyBranchCategorizesEachOfItsRanksExactlyOnce() throws Exception {
            for (String branch : List.of("usaf", "usa", "usn", "usmc", "uscg", "ussf", "other")) {
                JsonNode flat = json(get("/v1/rank/" + branch)).get("data");
                JsonNode categorized = json(get("/v1/rank/" + branch + "/categorized"));

                List<String> categorizedTexts = new ArrayList<>();
                for (String category : CATEGORY_KEYS) {
                    JsonNode list = categorized.get(category);
                    if (!list.isNull()) {
                        assertThat(list.size()).as(branch + "." + category).isGreaterThan(0);
                        list.forEach(node -> categorizedTexts.add(node.toString()));
                    }
                }
                assertThat(categorizedTexts.stream().sorted().collect(Collectors.toList()))
                        .as(branch).isEqualTo(sortedTexts(flat));
            }
        }

        @Test
        void categorizedPathIsCaseInsensitiveOnBranchAndAcceptsV2() throws Exception {
            String lower = get("/v1/rank/usmc/categorized").body();

            assertThat(get("/v1/rank/USMC/categorized").body()).isEqualTo(lower);
            assertThat(get("/v2/rank/Usmc/categorized").body()).isEqualTo(lower);
        }

        @Test
        void unknownBranchReturnsNotFound() throws Exception {
            HttpResponse<String> response = get("/v1/rank/nope/categorized");

            assertNotFoundBody(response, "Unknown Branch Name", "/api/v1/rank/nope/categorized");
        }

        @Test
        void categorizedIsNotTreatedAsAnAbbreviationWhenBranchIsInvalid() throws Exception {
            // "/{branch}/categorized" wins over "/{branch}/{abbreviation}" for every branch value
            HttpResponse<String> valid = get("/v1/rank/usaf/CATEGORIZED");

            assertNotFoundBody(valid, "USAF Rank 'CATEGORIZED' does not exist.", "/api/v1/rank/usaf/CATEGORIZED");
        }
    }

    @Test
    void payGradeDigitsDriveOrderingAcrossAllBranches() throws Exception {
        for (String branch : List.of("usaf", "usa", "usn", "usmc", "uscg", "ussf")) {
            JsonNode body = json(get("/v1/rank/" + branch + "/categorized"));
            for (String category : List.of("enlisted", "officer", "warrantOfficer")) {
                JsonNode list = body.get(category);
                if (list.isNull()) {
                    continue;
                }
                List<Integer> numbers = values(list, "payGrade").stream()
                        .map(grade -> Integer.parseInt(grade.replaceAll("[^\\d]", "")))
                        .collect(Collectors.toList());
                assertThat(IntStream.range(1, numbers.size()).allMatch(i -> numbers.get(i - 1) <= numbers.get(i)))
                        .as(branch + "." + category + " " + numbers).isTrue();
            }
        }
    }
}
