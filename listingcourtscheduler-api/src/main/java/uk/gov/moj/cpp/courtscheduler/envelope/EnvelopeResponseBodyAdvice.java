package uk.gov.moj.cpp.courtscheduler.envelope;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every JSON response in the {@code _metadata} envelope produced by the
 * legacy Justice Services {@code Enveloper}. Toggle off via
 * {@code courtscheduler.envelope.enabled=false} once UI clients confirm they
 * do not read {@code _metadata}.
 */
@RestControllerAdvice
public class EnvelopeResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final String CJSCPPUID = "CJSCPPUID";
    private static final String CPP_ACTION = "CPP-ACTION";

    @Value("${courtscheduler.envelope.enabled:true}")
    private boolean envelopeEnabled;

    @Override
    public boolean supports(final MethodParameter returnType,
                            final Class<? extends HttpMessageConverter<?>> converterType) {
        if (!envelopeEnabled) {
            return false;
        }
        // Per-method opt-out: legacy validate-* endpoints assert response body is exactly "{}".
        return returnType.getMethodAnnotation(SkipEnvelope.class) == null
                && (returnType.getContainingClass() == null
                    || returnType.getContainingClass().getAnnotation(SkipEnvelope.class) == null);
    }

    @Override
    public Object beforeBodyWrite(final Object body,
                                  final MethodParameter returnType,
                                  final MediaType selectedContentType,
                                  final Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  final ServerHttpRequest request,
                                  final ServerHttpResponse response) {
        if (body == null) {
            return null;
        }
        final MediaType contentType = selectedContentType == null ? MediaType.APPLICATION_JSON : selectedContentType;
        if (!isJsonLike(contentType)) {
            return body;
        }
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        // Error responses (4xx/5xx from GlobalExceptionHandler) are not enveloped — the
        // legacy framework returned them as bare {"error":"<msg>"} bodies. Envelope is
        // only applied to 2xx successes.
        if (response instanceof org.springframework.http.server.ServletServerHttpResponse servletResp) {
            final int status = servletResp.getServletResponse().getStatus();
            if (status >= 400) {
                return body;
            }
        }
        final HttpServletRequest http = servletRequest.getServletRequest();
        final String userId = http.getHeader(CJSCPPUID);
        final String action = http.getHeader(CPP_ACTION);
        return JsonEnvelopeWrapper.wrap(body, action, userId);
    }

    private static boolean isJsonLike(final MediaType type) {
        return MediaType.APPLICATION_JSON.includes(type)
                || (type.getSubtype() != null && type.getSubtype().endsWith("+json"));
    }
}
