package uk.gov.moj.cpp.courtscheduler.validation;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
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
 * <p>Uses <b>everit-json-schema</b> — the same library the legacy framework used — so validation
 * is byte-for-byte faithful to WildFly, including draft-04 semantics. In particular {@code date}
 * is not a defined format in draft-04, so {@code format: date} is ignored (everit only asserts the
 * formats it recognises for the schema's draft). Structure, required, type, enum, pattern and
 * {@code additionalProperties} are enforced. This is why the {@code /validate*} endpoints need no
 * special-casing: everit lets a (format-only) imperfect payload through to the business validators,
 * exactly as WildFly did.
 *
 * <p>Faithful to the old behaviour:
 * <ul>
 *   <li>Only request bodies are validated — GET/HEAD/OPTIONS (no body) pass through; responses are
 *       never validated.</li>
 *   <li>A media type with no matching schema passes through unvalidated (e.g. the empty
 *       {@code remove.hearing.slots} DELETE body, or non-vendor content types).</li>
 *   <li>Validation failures return HTTP 400 with the legacy framework error shape {@code {"error": "..."}}.</li>
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
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();
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
        final Schema schema = schemaFor(schemaName);
        if (schema == null) {
            chain.doFilter(request, response);
            return;
        }

        final byte[] body = request.getInputStream().readAllBytes();
        final CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, body);

        if (body.length == 0) {
            // Empty body -> let the controller/handler decide, preserving behaviour for endpoints
            // that tolerate empty payloads.
            chain.doFilter(wrapped, response);
            return;
        }

        final Object json;
        try {
            json = new JSONTokener(new String(body, StandardCharsets.UTF_8)).nextValue();
        } catch (final JSONException parseEx) {
            writeBadRequest(response, "Request body is not valid JSON");
            return;
        }

        try {
            schema.validate(json);
        } catch (final ValidationException ve) {
            final String joined = String.join("; ", ve.getAllMessages());
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

    private Schema schemaFor(final String schemaName) {
        if (noSchema.contains(schemaName)) {
            return null;
        }
        final Schema cached = schemaCache.get(schemaName);
        if (cached != null) {
            return cached;
        }
        final ClassPathResource resource = new ClassPathResource(SCHEMA_DIR + schemaName);
        if (!resource.exists()) {
            noSchema.add(schemaName);
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            // SchemaLoader.load detects the draft from the schema's $schema (draft-04 here).
            final Schema compiled = SchemaLoader.load(new JSONObject(new JSONTokener(in)));
            schemaCache.put(schemaName, compiled);
            return compiled;
        } catch (final Exception e) {
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
        // Legacy framework error shape: the WildFly exception mapper rendered all 4xx bodies as
        // {"error":"<msg>"} (see GlobalExceptionHandler#errorBody and CourtSchedulerIT).
        body.put("error", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
