package uk.gov.moj.cpp.courtscheduler.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Configuration;

/**
 * Per-action request metrics, restoring the dimensions emitted by the legacy
 * Justice Services {@code TotalActionMetricsInterceptor} +
 * {@code IndividualActionMetricsInterceptor} (chain entries 1 and 2 in the legacy
 * {@code CourtSchedulerApiInterceptorChainProvider}).
 *
 * <p>Two Micrometer meters are emitted per request:</p>
 * <ul>
 *   <li>{@code cpp.action.total} — single-bucket counter, no tags. Mirrors
 *       {@code TotalActionMetricsInterceptor}.</li>
 *   <li>{@code cpp.action.individual} — Timer tagged with {@code action} (the
 *       action name) and {@code status} (HTTP status). Mirrors
 *       {@code IndividualActionMetricsInterceptor}.</li>
 * </ul>
 *
 * <p>Action name is resolved (in priority order) from:</p>
 * <ol>
 *   <li>The {@code CPP-ACTION} request header (legacy explicit override),</li>
 *   <li>The vendor segment of the request {@code Content-Type} (e.g.
 *       {@code application/vnd.courtscheduler.create+json} → {@code courtscheduler.create}),</li>
 *   <li>The vendor segment of the {@code Accept} header,</li>
 *   <li>{@code "<METHOD> <path>"} as a fallback so unknown actions still appear
 *       in metrics rather than being silently dropped.</li>
 * </ol>
 */
@Configuration
public class ActionMetricsInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(ActionMetricsInterceptor.class);

    private static final String START_TIME_ATTR = ActionMetricsInterceptor.class.getName() + ".startNs";
    private static final String ACTION_HEADER = "CPP-ACTION";
    private static final String VENDOR_PREFIX = "application/vnd.";
    private static final String JSON_SUFFIX = "+json";
    private static final String UNKNOWN_ACTION = "unknown";

    private static final String METRIC_TOTAL = "cpp.action.total";
    private static final String METRIC_INDIVIDUAL = "cpp.action.individual";

    private final MeterRegistry meterRegistry;

    public ActionMetricsInterceptor(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/**");
    }

    @Override
    public boolean preHandle(final HttpServletRequest request,
                             final HttpServletResponse response,
                             final Object handler) {
        request.setAttribute(START_TIME_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final Object handler,
                                final Exception ex) {
        final Long start = (Long) request.getAttribute(START_TIME_ATTR);
        if (start == null) {
            return;
        }
        final long durationNs = System.nanoTime() - start;
        final String action = resolveActionName(request);
        final String status = String.valueOf(response.getStatus());
        try {
            meterRegistry.counter(METRIC_TOTAL).increment();
            Timer.builder(METRIC_INDIVIDUAL)
                    .tags(Tags.of("action", action, "status", status))
                    .register(meterRegistry)
                    .record(durationNs, TimeUnit.NANOSECONDS);
        } catch (RuntimeException recordingFailure) {
            LOG.debug("Action metric recording failed for action={}: {}", action, recordingFailure.toString());
        }
    }

    private static String resolveActionName(final HttpServletRequest request) {
        final String explicit = request.getHeader(ACTION_HEADER);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        final String fromContentType = vendorSegment(request.getContentType());
        if (fromContentType != null) {
            return fromContentType;
        }
        final String fromAccept = vendorSegment(request.getHeader("Accept"));
        if (fromAccept != null) {
            return fromAccept;
        }
        final String method = request.getMethod();
        final String path = request.getRequestURI();
        return method != null && path != null ? method + " " + path : UNKNOWN_ACTION;
    }

    private static String vendorSegment(final String mediaType) {
        if (mediaType == null) {
            return null;
        }
        final int vendorStart = mediaType.indexOf(VENDOR_PREFIX);
        if (vendorStart < 0) {
            return null;
        }
        final int after = vendorStart + VENDOR_PREFIX.length();
        final int suffix = mediaType.indexOf(JSON_SUFFIX, after);
        final int semi = mediaType.indexOf(';', after);
        final int end = suffix > 0 ? suffix : (semi > 0 ? semi : mediaType.length());
        final String segment = mediaType.substring(after, end).trim();
        return segment.isEmpty() ? null : segment;
    }
}
