package mil.tron.commonapi.exception.custom;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyErrorMessagesTest {

    @Test
    void authorizationDenialIsReportedWithTheSpringSecurity5Wording() {
        AuthorizationDeniedException denied =
                new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false));

        assertThat(LegacyErrorMessages.of(denied)).contains("Access is denied");
        assertThat(LegacyErrorMessages.messageOf(denied)).isEqualTo("Access is denied");
    }

    @Test
    void methodNotSupportedAndUnmappedPathUseTheSpringMvc5Wording() {
        assertThat(LegacyErrorMessages.messageOf(new HttpRequestMethodNotSupportedException("POST", List.of("GET"))))
                .isEqualTo("Request method 'POST' not supported");
        assertThat(LegacyErrorMessages.messageOf(new NoResourceFoundException(HttpMethod.GET, "v1/rank/x/y/z")))
                .isEqualTo("No message available");
    }

    @Test
    void otherExceptionsKeepTheirOwnMessage() {
        IllegalStateException other = new IllegalStateException("Unknown Branch Name");

        assertThat(LegacyErrorMessages.of(other)).isEmpty();
        assertThat(LegacyErrorMessages.messageOf(other)).isEqualTo("Unknown Branch Name");
    }
}
