package mil.tron.commonapi.exception.custom;

import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Optional;

/**
 * Spring MVC 6 changed the text of its built-in "method not allowed" error and started reporting
 * unmapped paths through {@link NoResourceFoundException}, and Spring Security 6 reports
 * authorization failures as {@link AuthorizationDeniedException} ("Access Denied" instead of
 * "Access is denied"). API clients and the persisted HTTP logs match on the original wording, so
 * these three framework messages are reported in the established form.
 */
public final class LegacyErrorMessages {

    private static final String NO_MESSAGE_AVAILABLE = "No message available";
    private static final String ACCESS_IS_DENIED = "Access is denied";

    private LegacyErrorMessages() {
    }

    public static Optional<String> of(Throwable error) {
        if (error instanceof HttpRequestMethodNotSupportedException ex) {
            return Optional.of("Request method '" + ex.getMethod() + "' not supported");
        }
        if (error instanceof NoResourceFoundException) {
            return Optional.of(NO_MESSAGE_AVAILABLE);
        }
        if (error instanceof AuthorizationDeniedException) {
            return Optional.of(ACCESS_IS_DENIED);
        }
        return Optional.empty();
    }

    public static String messageOf(Throwable error) {
        return of(error).orElseGet(error::getMessage);
    }
}
