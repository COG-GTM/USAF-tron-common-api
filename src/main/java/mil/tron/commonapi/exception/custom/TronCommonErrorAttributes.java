package mil.tron.commonapi.exception.custom;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.Optional;

public class TronCommonErrorAttributes extends DefaultErrorAttributes {

    private static final String MESSAGE_FIELD = "message";
    private static final String NO_MESSAGE_AVAILABLE = "No message available";

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        final Map<String, Object> defaultErrorAttributes = super.getErrorAttributes(webRequest, options);
        if (defaultErrorAttributes.containsKey(MESSAGE_FIELD)) {
            legacyMessage(getError(webRequest))
                    .ifPresent(message -> defaultErrorAttributes.put(MESSAGE_FIELD, message));
        }
        final TronCommonAppError tronCommonAppError = TronCommonAppError.fromDefaultAttributeMap(defaultErrorAttributes);
        return tronCommonAppError.toAttributeMap();
    }

    /**
     * Spring MVC 6 changed the text of its built-in "method not allowed" error and started reporting
     * unmapped paths through {@link NoResourceFoundException}; API clients match on the original
     * wording, so both are reported in the established form.
     */
    private static Optional<String> legacyMessage(Throwable error) {
        if (error instanceof HttpRequestMethodNotSupportedException ex) {
            return Optional.of("Request method '" + ex.getMethod() + "' not supported");
        }
        if (error instanceof NoResourceFoundException) {
            return Optional.of(NO_MESSAGE_AVAILABLE);
        }
        return Optional.empty();
    }
}
