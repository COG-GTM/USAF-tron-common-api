package mil.tron.commonapi.service.trace;

import org.springframework.boot.actuate.autoconfigure.web.exchanges.HttpExchangesProperties;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.servlet.HttpExchangesFilter;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
/**
 * This configures the HttpTracer to NOT log requests to specific endpoints - which we really don't care about,
 * or they are just reachable in a dev environment.
 */
@Component
public class TraceRequestFilter extends HttpExchangesFilter {

    public TraceRequestFilter(HttpExchangeRepository repository, HttpExchangesProperties properties) {
        super(repository, properties.getRecording().getInclude());
    }

    /**
     * Dont log stuff from actuator, http log audit, h2-console, or from swagger paths
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().contains("actuator")
                || request.getServletPath().contains("api-docs")
                || request.getServletPath().contains("/logs")
                || request.getServletPath().contains("h2-console");
    }
}
