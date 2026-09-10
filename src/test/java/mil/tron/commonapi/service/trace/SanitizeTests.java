package mil.tron.commonapi.service.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.web.exchanges.HttpExchange;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class SanitizeTests {

    @Autowired
    private HttpTraceService httpTraceService;

    @Test
    void testSanitize() throws URISyntaxException {

        HttpExchange.Request request = new HttpExchange.Request(
                new URI("https://tron-common-api-il4.apps.dso.mil/api/app/arms-gateway/training-svc/"),
                "POST", "local", new HashMap<>());

        HttpExchange trace = new HttpExchange(java.time.Instant.now(), request, null, null, null, java.time.Duration.ofMillis(1000));

        ContentTrace contentTrace = new ContentTrace();
        contentTrace.setRequestBody("some sensitive stuff");
        contentTrace.setResponseBody("some sensitive stuff");

        httpTraceService.sanitizeBodies(trace, contentTrace);

        assertEquals("Redacted", contentTrace.getRequestBody());
        assertEquals("Redacted", contentTrace.getResponseBody());

        HttpExchange.Request request2 = new HttpExchange.Request(
                new URI("https://tron-common-api-il4.apps.dso.mil/api/app/puckboard/events/"),
                "POST", "local", new HashMap<>());

        HttpExchange trace2 = new HttpExchange(java.time.Instant.now(), request2, null, null, null, java.time.Duration.ofMillis(1000));

        ContentTrace contentTrace2 = new ContentTrace();
        contentTrace.setRequestBody("some stuff");
        contentTrace.setResponseBody("some stuff");

        httpTraceService.sanitizeBodies(trace2, contentTrace2);

        assertEquals("some stuff", contentTrace.getRequestBody());
        assertEquals("some stuff", contentTrace.getResponseBody());
    }


}
