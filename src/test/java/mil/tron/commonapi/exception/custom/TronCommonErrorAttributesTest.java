package mil.tron.commonapi.exception.custom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.RequestDispatcher;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TronCommonErrorAttributesTest {

    private static final List<String> BODY_KEYS = List.of("timestamp", "status", "error", "errors", "reason", "path");

    private final TronCommonErrorAttributes errorAttributes = new TronCommonErrorAttributes();

    private Map<String, Object> attributesFor(int status, Throwable error, ErrorAttributeOptions options) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/rank");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, error);
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, error.getMessage());
        return errorAttributes.getErrorAttributes(new ServletWebRequest(request), options);
    }

    @Test
    void methodNotAllowedKeepsLegacyReasonWording() {
        Map<String, Object> body = attributesFor(405,
                new HttpRequestMethodNotSupportedException("POST", List.of("GET")),
                ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.MESSAGE));

        assertThat(body.keySet()).containsExactlyElementsOf(BODY_KEYS);
        assertThat(body.get("status")).isEqualTo(405);
        assertThat(body.get("error")).isEqualTo("Method Not Allowed");
        assertThat(body.get("reason")).isEqualTo("Request method 'POST' not supported");
        assertThat(body.get("path")).isEqualTo("/api/v1/rank");
    }

    @Test
    void unmappedPathKeepsLegacyNoMessageAvailableReason() {
        Map<String, Object> body = attributesFor(404,
                new NoResourceFoundException(HttpMethod.GET, "v1/rank/usaf/Capt/extra"),
                ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.MESSAGE));

        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("error")).isEqualTo("Not Found");
        assertThat(body.get("reason")).isEqualTo("No message available");
    }

    @Test
    void otherExceptionsPassTheirMessageThroughUnchanged() {
        Map<String, Object> body = attributesFor(404,
                new IllegalStateException("Unknown Branch Name"),
                ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.MESSAGE));

        assertThat(body.get("reason")).isEqualTo("Unknown Branch Name");
    }

    @Test
    void messageStaysSuppressedWhenIncludeMessageIsOff() {
        Map<String, Object> body = attributesFor(405,
                new HttpRequestMethodNotSupportedException("POST", List.of("GET")),
                ErrorAttributeOptions.defaults());

        assertThat(body.get("reason")).isEqualTo("");
    }
}
