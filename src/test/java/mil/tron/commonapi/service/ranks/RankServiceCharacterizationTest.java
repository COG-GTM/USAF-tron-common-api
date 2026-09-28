package mil.tron.commonapi.service.ranks;

import mil.tron.commonapi.dto.rank.RankCategorizedDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.ranks.RankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization tests for the categorization and lookup rules in {@link RankServiceImpl}.
 * Repository results are stubbed so each rule can be pinned with a minimal golden input.
 * See docs/modernization/slices/ranks.md for the invariant each test pins.
 */
@ExtendWith(MockitoExtension.class)
public class RankServiceCharacterizationTest {

    @Mock
    private RankRepository repository;

    private RankServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RankServiceImpl(repository);
    }

    private static Rank rank(String abbreviation, String payGrade) {
        return Rank.builder()
                .id(UUID.randomUUID())
                .abbreviation(abbreviation)
                .name(abbreviation)
                .payGrade(payGrade)
                .branchType(Branch.USAF)
                .build();
    }

    private static List<String> grades(List<Rank> ranks) {
        return ranks.stream().map(Rank::getPayGrade).collect(Collectors.toList());
    }

    private RankCategorizedDto categorize(Rank... ranks) {
        Mockito.when(repository.findAllByBranchType(Branch.USAF)).thenReturn(List.of(ranks));
        return service.getRanksByBranchAndCategorize(Branch.USAF);
    }

    @Nested
    class CategoryAssignment {

        @Test
        void prefixesDecideTheCategory() {
            RankCategorizedDto dto = categorize(
                    rank("e", "E-5"), rank("w", "W-2"), rank("o", "O-3"),
                    rank("gs", "GS-13"), rank("ses", "SES"), rank("x", "N/A"));

            assertThat(grades(dto.getEnlisted())).containsExactly("E-5");
            assertThat(grades(dto.getWarrantOfficer())).containsExactly("W-2");
            assertThat(grades(dto.getOfficer())).containsExactly("O-3");
            assertThat(grades(dto.getCivilService())).containsExactly("GS-13", "SES");
            assertThat(grades(dto.getOther())).containsExactly("N/A");
        }

        @Test
        void prefixMatchingIsCaseInsensitiveButValuesAreReturnedUnchanged() {
            RankCategorizedDto dto = categorize(rank("e", "e-5"), rank("gs", "gs-9"), rank("ses", "ses"), rank("o", "o-1"));

            assertThat(grades(dto.getEnlisted())).containsExactly("e-5");
            assertThat(grades(dto.getCivilService())).containsExactly("gs-9", "ses");
            assertThat(grades(dto.getOfficer())).containsExactly("o-1");
        }

        @Test
        void prefixesRequireTheHyphenForUniformedGradesButNotForCivilService() {
            RankCategorizedDto dto = categorize(
                    rank("e", "E5"), rank("w", "WO1"), rank("o", "O5"), rank("gs", "GS13"));

            assertThat(dto.getEnlisted()).isNull();
            assertThat(dto.getWarrantOfficer()).isNull();
            assertThat(dto.getOfficer()).isNull();
            assertThat(grades(dto.getCivilService())).containsExactly("GS13");
            assertThat(grades(dto.getOther())).containsExactly("E5", "WO1", "O5");
        }

        @Test
        void emptyCategoriesAreNullNotEmptyLists() {
            RankCategorizedDto dto = categorize(rank("e", "E-1"));

            assertThat(dto.getEnlisted()).hasSize(1);
            assertThat(dto.getWarrantOfficer()).isNull();
            assertThat(dto.getOfficer()).isNull();
            assertThat(dto.getCivilService()).isNull();
            assertThat(dto.getOther()).isNull();
        }

        @Test
        void branchWithNoRanksYieldsAllNullCategories() {
            RankCategorizedDto dto = categorize();

            assertThat(dto.getEnlisted()).isNull();
            assertThat(dto.getWarrantOfficer()).isNull();
            assertThat(dto.getOfficer()).isNull();
            assertThat(dto.getCivilService()).isNull();
            assertThat(dto.getOther()).isNull();
        }

        @Test
        void emptyPayGradeFallsToOther() {
            RankCategorizedDto dto = categorize(rank("blank", ""));

            assertThat(grades(dto.getOther())).containsExactly("");
        }
    }

    @Nested
    class Ordering {

        @Test
        void uniformedCategoriesSortNumericallyNotLexically() {
            RankCategorizedDto dto = categorize(
                    rank("a", "E-10"), rank("b", "E-9"), rank("c", "E-1"),
                    rank("d", "O-10"), rank("e", "O-2"), rank("f", "O-1"),
                    rank("g", "W-5"), rank("h", "W-10"), rank("i", "W-1"));

            assertThat(grades(dto.getEnlisted())).containsExactly("E-1", "E-9", "E-10");
            assertThat(grades(dto.getOfficer())).containsExactly("O-1", "O-2", "O-10");
            assertThat(grades(dto.getWarrantOfficer())).containsExactly("W-1", "W-5", "W-10");
        }

        @Test
        void equalPayGradesKeepRepositoryOrder() {
            RankCategorizedDto dto = categorize(
                    rank("third", "E-9"), rank("first", "E-9"), rank("second", "E-9"), rank("low", "E-8"));

            assertThat(dto.getEnlisted().stream().map(Rank::getAbbreviation).collect(Collectors.toList()))
                    .containsExactly("low", "third", "first", "second");
        }

        @Test
        void civilServiceAndOtherAreNeverSorted() {
            RankCategorizedDto dto = categorize(
                    rank("a", "GS-15"), rank("b", "GS-7"), rank("c", "SES"),
                    rank("d", "ZZ-9"), rank("e", "ZZ-1"));

            assertThat(grades(dto.getCivilService())).containsExactly("GS-15", "GS-7", "SES");
            assertThat(grades(dto.getOther())).containsExactly("ZZ-9", "ZZ-1");
        }

        @Test
        void everyDigitInThePayGradeContributesToTheSortKey() {
            RankCategorizedDto dto = categorize(rank("a", "O-2A1"), rank("b", "O-3"), rank("c", "O-1x9"));

            // "O-2A1" -> 21, "O-1x9" -> 19, "O-3" -> 3
            assertThat(grades(dto.getOfficer())).containsExactly("O-3", "O-1x9", "O-2A1");
        }

        @Test
        void payGradeWithoutDigitsSortsAsZeroAheadOfEveryNumberedGrade() {
            RankCategorizedDto dto = categorize(rank("a", "E-2"), rank("b", "E-"), rank("c", "E-1"));

            assertThat(grades(dto.getEnlisted())).containsExactly("E-", "E-1", "E-2");
        }

        @Test
        void digitStringTooLargeForIntSortsAsZero() {
            RankCategorizedDto dto = categorize(rank("a", "E-1"), rank("b", "E-99999999999"));

            assertThat(grades(dto.getEnlisted())).containsExactly("E-99999999999", "E-1");
        }
    }

    @Nested
    class Lookup {

        @Test
        void getRanksDelegatesToFindAllWithoutFiltering() {
            List<Rank> all = List.of(rank("a", "E-1"), rank("b", "O-1"));
            Mockito.when(repository.findAll()).thenReturn(all);

            assertThat(service.getRanks()).containsExactlyElementsOf(all);
        }

        @Test
        void getRanksByBranchReturnsRepositoryResultUnsorted() {
            List<Rank> unsorted = List.of(rank("a", "E-9"), rank("b", "E-1"));
            Mockito.when(repository.findAllByBranchType(Branch.USAF)).thenReturn(unsorted);

            assertThat(service.getRanks(Branch.USAF)).containsExactlyElementsOf(unsorted);
        }

        @Test
        void getRankByIdMessageNamesTheId() {
            UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
            Mockito.when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRank(id))
                    .isInstanceOf(RecordNotFoundException.class)
                    .hasMessage("Rank resource with ID: 00000000-0000-0000-0000-000000000001 does not exist.");
        }

        @Test
        void getRankByIdReturnsTheStoredEntity() {
            Rank stored = rank("Capt", "O-3");
            Mockito.when(repository.findById(stored.getId())).thenReturn(Optional.of(stored));

            assertThat(service.getRank(stored.getId())).isSameAs(stored);
        }

        @Test
        void getRankByAbbreviationPassesTheRawAbbreviationToTheCaseInsensitiveQuery() {
            Rank stored = rank("Capt", "O-3");
            Mockito.when(repository.findByAbbreviationIgnoringCaseAndBranchType("cApT", Branch.USAF))
                    .thenReturn(Optional.of(stored));

            assertThat(service.getRank("cApT", Branch.USAF)).isSameAs(stored);
        }

        @Test
        void getRankByAbbreviationMessageUsesEnumNameAndRequestedAbbreviation() {
            Mockito.when(repository.findByAbbreviationIgnoringCaseAndBranchType("nope", Branch.USSF))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRank("nope", Branch.USSF))
                    .isInstanceOf(RecordNotFoundException.class)
                    .hasMessage("USSF Rank 'nope' does not exist.");
        }
    }

    @Nested
    class PinnedLegacyDefects {

        @Test
        void pinnedDefect_nullPayGradeMakesCategorizationThrowNullPointerException() {
            // rank.pay_grade is nullable in the schema (diff-changelog-1.00.010.xml) but the
            // categorizer dereferences it unconditionally, so one bad row breaks the whole endpoint.
            Mockito.when(repository.findAllByBranchType(Branch.USAF))
                    .thenReturn(List.of(rank("ok", "E-1"), rank("broken", null)));

            assertThatThrownBy(() -> service.getRanksByBranchAndCategorize(Branch.USAF))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void pinnedDefect_builderIgnoresBranchTypeDefaultWhileNoArgsConstructorKeepsIt() {
            // Rank.branchType is initialized to OTHER but lacks @Builder.Default, so the two
            // construction paths disagree on the default branch.
            assertThat(new Rank().getBranchType()).isEqualTo(Branch.OTHER);
            assertThat(Rank.builder().build().getBranchType()).isNull();
        }
    }
}
