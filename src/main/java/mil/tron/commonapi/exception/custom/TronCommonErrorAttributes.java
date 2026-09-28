package mil.tron.commonapi.exception.custom;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

public class TronCommonErrorAttributes extends DefaultErrorAttributes {

    private static final String MESSAGE_FIELD = "message";

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        final Map<String, Object> defaultErrorAttributes = super.getErrorAttributes(webRequest, options);
        if (defaultErrorAttributes.containsKey(MESSAGE_FIELD)) {
            LegacyErrorMessages.of(getError(webRequest))
                    .ifPresent(message -> defaultErrorAttributes.put(MESSAGE_FIELD, message));
        }
        final TronCommonAppError tronCommonAppError = TronCommonAppError.fromDefaultAttributeMap(defaultErrorAttributes);
        return tronCommonAppError.toAttributeMap();
    }
}
