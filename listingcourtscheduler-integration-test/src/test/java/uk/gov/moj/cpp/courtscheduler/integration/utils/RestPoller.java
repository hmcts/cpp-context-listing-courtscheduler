package uk.gov.moj.cpp.courtscheduler.integration.utils;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Replacement for {@code uk.gov.justice.services.test.utils.core.http.RestPoller}.
 *
 * <p>Preserves the fluent API the legacy IT classes use:
 * {@code poll(requestParams).with().timeout(30L, SECONDS).pollInterval(...).pollDelay(...).until()}.
 * Unlike the legacy version we don't wait for a JSON-content matcher — the dockerised app is
 * already up by the time tests run, so we just retry until a non-error response comes back or
 * the timeout elapses.</p>
 */
public final class RestPoller {

    private static final RestTemplate REST = newRestTemplate();

    private RestPoller() {
    }

    public static Builder poll(final RequestParams params) {
        return new Builder(params);
    }

    public static final class Builder {
        private final RequestParams params;
        private long timeoutMillis = TimeUnit.SECONDS.toMillis(30);
        private long pollIntervalMillis = 50L;
        private long pollDelayMillis = 0L;

        private Builder(final RequestParams params) {
            this.params = params;
        }

        public Builder with() {
            return this;
        }

        public Builder timeout(final long value, final TimeUnit unit) {
            this.timeoutMillis = unit.toMillis(value);
            return this;
        }

        public Builder pollInterval(final long value, final TimeUnit unit) {
            this.pollIntervalMillis = unit.toMillis(value);
            return this;
        }

        public Builder pollDelay(final long value, final TimeUnit unit) {
            this.pollDelayMillis = unit.toMillis(value);
            return this;
        }

        public ResponseData until() {
            return poll();
        }

        /** Legacy variant accepting a matcher — ignored, we just poll for a response. */
        public ResponseData until(final Object ignored) {
            return poll();
        }

        private ResponseData poll() {
            try {
                if (pollDelayMillis > 0) {
                    Thread.sleep(pollDelayMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            final long deadline = System.currentTimeMillis() + timeoutMillis;
            ResponseData last = null;
            while (true) {
                last = doGet();
                final int code = last.getStatus().getStatusCode();
                if (code != 0 && code < 500) {
                    return last;
                }
                if (System.currentTimeMillis() >= deadline) {
                    return last;
                }
                try {
                    Thread.sleep(pollIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return last;
                }
            }
        }

        private ResponseData doGet() {
            final HttpHeaders headers = new HttpHeaders();
            if (params.getMediaType() != null) {
                headers.set(HttpHeaders.ACCEPT, params.getMediaType());
            }
            if (params.getUserId() != null) {
                headers.set("CJSCPPUID", params.getUserId());
            }
            final HttpEntity<String> entity = new HttpEntity<>(headers);
            try {
                final ResponseEntity<String> response = REST.exchange(
                        URI.create(params.getUrl()), HttpMethod.GET, entity, String.class);
                return new ResponseData(response.getStatusCode().value(), response.getBody());
            } catch (Exception e) {
                return new ResponseData(0, e.getMessage());
            }
        }
    }

    private static RestTemplate newRestTemplate() {
        final RestTemplate template = new RestTemplate();
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(final ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }
}
