package uk.gov.moj.cpp.courtscheduler.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates inbound request bodies against the legacy RAML JSON Schemas, restoring the
 * Justice Services / WildFly behaviour: a request body is validated against the JSON Schema
 * registered for its media type before the controller sees it.
 *
 * <p>Faithful to the old behaviour:
 * <ul>
 *   <li>Only request bodies are validated — GET/HEAD/OPTIONS (no body) pass through, and
 *       responses are never validated.</li>
 *   <li>A media type with no matching schema passes through unvalidated (e.g. the empty
 *       {@code remove.hearing.slots} DELETE body, or non-vendor content types).</li>
 *   <li>Validation failures return HTTP 400 with the framework error shape
 *       {@code {"error": "..."}} (see GlobalExceptionHandler).</li>
 * </ul>
 *
 * <p>Media-type → schema mapping:
 * {@code application/vnd.courtscheduler.<subtype>+json} →
 * {@code classpath:request-schemas/courtscheduler.<subtype>.json}.
 *
 * <p>Runs early so the cached-body wrapper is visible to the audit filter and the controller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class RequestSchemaValidationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestSchemaValidationFilter.class);
    private static final String SCHEMA_DIR = "request-schemas/";
    private static final String VND_PREFIX = "vnd.";
    private static final String JSON_SUFFIX = "+json";
    private static final String SCHEMA_NAME_PREFIX = "courtscheduler.";

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();
    private final Set<String> noSchema = ConcurrentHashMap.newKeySet();

    public RequestSchemaValidationFilter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String method = request.getMethod();
        // No request body to validate on these — matches WildFly (GETs were never body-validated).
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method) || "TRACE".equals(method);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final String schemaName = schemaNameFor(request);
        if (schemaName == null) {
            chain.doFilter(request, response);
            return;
        }
        final JsonSchema schema = schemaFor(schemaName);
        if (schema == null) {
            chain.doFilter(request, response);
            return;
        }

        final byte[] body = request.getInputStream().readAllBytes();
        final CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, body);

        if (body.length == 0) {
            // Empty body against a schema that requires content -> let the controller/handler decide,
            // preserving existing behaviour for endpoints that tolerate empty payloads.
            chain.doFilter(wrapped, response);
            return;
        }

        final JsonNode node;
        try {
            node = objectMapper.readTree(body);
        } catch (final IOException parseEx) {
            writeBadRequest(response, "Request body is not valid JSON");
            return;
        }

        final Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            final Set<String> messages = new TreeSet<>();
            for (final ValidationMessage error : errors) {
                messages.add(error.getMessage());
            }
            final String joined = String.join("; ", messages);
            LOG.debug("Request body failed schema {}: {}", schemaName, joined);
            writeBadRequest(response, joined);
            return;
        }

        chain.doFilter(wrapped, response);
    }

    /**
     * Derives the schema resource name from the request Content-Type, or {@code null} if the
     * content type is not a {@code application/vnd.courtscheduler.*+json} vendor media type.
     */
    private static String schemaNameFor(final HttpServletRequest request) {
        final String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        final MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (final RuntimeException invalid) {
            return null;
        }
        final String subtype = mediaType.getSubtype(); // e.g. vnd.courtscheduler.validate.create+json
        if (!subtype.startsWith(VND_PREFIX) || !subtype.endsWith(JSON_SUFFIX)) {
            return null;
        }
        final String token = subtype.substring(VND_PREFIX.length(), subtype.length() - JSON_SUFFIX.length());
        if (!token.startsWith(SCHEMA_NAME_PREFIX)) {
            return null;
        }
        return token + ".json";
    }

    private JsonSchema schemaFor(final String schemaName) {
        if (noSchema.contains(schemaName)) {
            return null;
        }
        final JsonSchema cached = schemaCache.get(schemaName);
        if (cached != null) {
            return cached;
        }
        final ClassPathResource resource = new ClassPathResource(SCHEMA_DIR + schemaName);
        if (!resource.exists()) {
            noSchema.add(schemaName);
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            final JsonSchema compiled = schemaFactory.getSchema(in);
            schemaCache.put(schemaName, compiled);
            return compiled;
        } catch (final IOException e) {
            LOG.warn("Could not load request schema {}: {}", schemaName, e.getMessage());
            noSchema.add(schemaName);
            return null;
        }
    }

    private void writeBadRequest(final HttpServletResponse response, final String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
