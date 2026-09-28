package mil.tron.commonapi.security;

import mil.tron.commonapi.JwtUtils;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests for the authorization decisions the security filter chain makes for the
 * rank endpoints when {@code security.enabled=true}: ranks are reference data readable by any
 * authenticated principal (app client or SSO user) and by nobody else.
 * See docs/modernization/slices/ranks.md for the invariant each test pins.
 */
@SpringBootTest(properties = { "security.enabled=true", "efa-enabled=false" })
@ActiveProfiles(value = { "development", "test" })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class RankAuthorizationCharacterizationTest {

    private static final String APP_CLIENT_NAME = "rank-reader";
    private static final String ADMIN_EMAIL = "rank-admin@unit.test";
    private static final String STRANGER_EMAIL = "rank-stranger@unit.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppClientUserRespository appClientUserRespository;

    @Autowired
    private DashboardUserRepository dashboardUserRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    private AppClientUser appClient;
    private DashboardUser admin;

    @BeforeEach
    void setUp() {
        appClient = appClientUserRespository.save(AppClientUser.builder()
                .id(UUID.randomUUID())
                .name(APP_CLIENT_NAME)
                .build());

        admin = dashboardUserRepository.save(DashboardUser.builder()
                .id(UUID.randomUUID())
                .email(ADMIN_EMAIL)
                .privileges(Set.of(privilegeRepository.findByName("DASHBOARD_ADMIN")
                        .orElseThrow(() -> new RecordNotFoundException("DASHBOARD_ADMIN privilege missing"))))
                .build());
    }

    @AfterEach
    void tearDown() {
        appClientUserRespository.delete(appClient);
        dashboardUserRepository.delete(admin);
    }

    private static MockHttpServletRequestBuilder asAppClient(MockHttpServletRequestBuilder request, String namespace) {
        return request.header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeader(namespace));
    }

    private static MockHttpServletRequestBuilder asSsoUser(MockHttpServletRequestBuilder request, String email) {
        return request
                .header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO())
                .header(JwtUtils.AUTH_HEADER_NAME, JwtUtils.createToken(email));
    }

    @Nested
    class Unauthenticated {

        @Test
        void requestWithoutClientCertHeaderIsForbidden() throws Exception {
            mockMvc.perform(get("/v1/rank")).andExpect(status().isForbidden());
            mockMvc.perform(get("/v2/rank")).andExpect(status().isForbidden());
            mockMvc.perform(get("/v1/rank/usaf")).andExpect(status().isForbidden());
            mockMvc.perform(get("/v1/rank/usaf/categorized")).andExpect(status().isForbidden());
            mockMvc.perform(get("/v1/rank/usaf/Capt")).andExpect(status().isForbidden());
        }

        @Test
        void unknownAppClientNamespaceIsForbidden() throws Exception {
            mockMvc.perform(asAppClient(get("/v1/rank"), "no-such-app"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void malformedClientCertHeaderIsForbidden() throws Exception {
            mockMvc.perform(get("/v1/rank").header(JwtUtils.XFCC_HEADER_NAME, "not-an-xfcc-header"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/v1/rank").header(JwtUtils.XFCC_HEADER_NAME, "By=a;Hash=b;Subject=c"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void gatewayNamespaceWithoutBearerTokenIsForbidden() throws Exception {
            // istio-system with no JWT is treated as an app client lookup for "istio-system", which does not exist
            mockMvc.perform(get("/v1/rank").header(JwtUtils.XFCC_HEADER_NAME, JwtUtils.generateXfccHeaderFromSSO()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void forbiddenResponseCarriesNoRankData() throws Exception {
            mockMvc.perform(get("/v1/rank"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(""));
        }
    }

    @Nested
    class AppClients {

        @Test
        void registeredAppClientWithNoPrivilegesCanReadEveryRankEndpoint() throws Exception {
            mockMvc.perform(asAppClient(get("/v1/rank"), APP_CLIENT_NAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(160)));
            mockMvc.perform(asAppClient(get("/v2/rank"), APP_CLIENT_NAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(160)));
            mockMvc.perform(asAppClient(get("/v1/rank/usaf"), APP_CLIENT_NAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(25)));
            mockMvc.perform(asAppClient(get("/v1/rank/usaf/categorized"), APP_CLIENT_NAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enlisted", hasSize(11)));
            mockMvc.perform(asAppClient(get("/v1/rank/usaf/Capt"), APP_CLIENT_NAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.abbreviation").value("Capt"));
        }

        @Test
        void appClientNamespaceIsMatchedCaseInsensitively() throws Exception {
            mockMvc.perform(asAppClient(get("/v1/rank/usaf/Capt"), APP_CLIENT_NAME.toUpperCase()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payGrade").value("O-3"));
        }

        @Test
        void authenticatedAppClientStillGetsNotFoundForUnknownBranchOrRank() throws Exception {
            mockMvc.perform(asAppClient(get("/v1/rank/nope"), APP_CLIENT_NAME))
                    .andExpect(status().isNotFound());
            mockMvc.perform(asAppClient(get("/v1/rank/usaf/nope"), APP_CLIENT_NAME))
                    .andExpect(status().isNotFound());
        }

        @Test
        void csrfIsDisabledSoPostFailsOnMethodNotOnToken() throws Exception {
            mockMvc.perform(asAppClient(post("/v1/rank"), APP_CLIENT_NAME))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET"));
        }
    }

    @Nested
    class SsoUsers {

        @Test
        void dashboardAdminCanReadRanks() throws Exception {
            mockMvc.perform(asSsoUser(get("/v1/rank/usaf/categorized"), ADMIN_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.officer", hasSize(11)));
        }

        @Test
        void dashboardAdminEmailIsMatchedCaseInsensitively() throws Exception {
            mockMvc.perform(asSsoUser(get("/v1/rank/usaf/Capt"), ADMIN_EMAIL.toUpperCase()))
                    .andExpect(status().isOk());
        }

        @Test
        void ssoUserUnknownToTheDashboardCanStillReadRanks() throws Exception {
            mockMvc.perform(asSsoUser(get("/v1/rank"), STRANGER_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(160)));
        }

        @Test
        @WithMockUser(username = "anyone", authorities = {})
        void mockAuthenticatedPrincipalWithoutAuthoritiesCanReadRanks() throws Exception {
            mockMvc.perform(get("/v1/rank/usn/CAPT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payGrade").value("O-6"));
        }
    }
}
