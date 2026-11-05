package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.common.config.CourtSchedulerSystemUserConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

class ListingCommandClientTest {

    private static final String EXPECTED_PATH = "/listing-command-api/command/api/rest/listing/hearings";
    private static final String EXPECTED_CONTENT_TYPE = "application/vnd.listing.command.change-judiciary-for-hearings+json";

    private final String systemUserId = UUID.randomUUID().toString();
    private final CourtSchedulerSystemUserConfig systemUserConfig = new CourtSchedulerSystemUserConfig(systemUserId);

    private HttpServer server;
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private final AtomicReference<Headers> receivedHeaders = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private int responseStatus = 202;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedMethod.set(exchange.getRequestMethod());
            receivedHeaders.set(exchange.getRequestHeaders());
            try (InputStream requestBody = exchange.getRequestBody()) {
                receivedBody.set(new String(requestBody.readAllBytes(), UTF_8));
            }
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void shouldPostChangeJudiciaryForHearingsCommandWithContentTypeAndSystemUser() {
        final ListingCommandClient listingCommandClient = clientFor(baseUrl());
        final JsonObject payload = changeJudiciaryPayload();

        listingCommandClient.changeJudiciaryForHearings(payload);

        assertEquals("POST", receivedMethod.get());
        assertEquals(EXPECTED_PATH, receivedPath.get());
        assertEquals(EXPECTED_CONTENT_TYPE, receivedHeaders.get().getFirst("Content-Type"));
        assertEquals(systemUserId, receivedHeaders.get().getFirst("CJSCPPUID"));
        assertEquals(payload.toString(), receivedBody.get());
    }

    @Test
    void shouldThrowWhenListingBaseUrlIsNotConfigured() {
        final ListingCommandClient listingCommandClient = clientFor("");

        final IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> listingCommandClient.changeJudiciaryForHearings(changeJudiciaryPayload()));

        assertTrue(exception.getMessage().contains("listing.base-url"));
    }

    @Test
    void shouldThrowWhenListingRespondsWithServerError() {
        responseStatus = 500;
        final ListingCommandClient listingCommandClient = clientFor(baseUrl());

        assertThrows(HttpServerErrorException.class,
                () -> listingCommandClient.changeJudiciaryForHearings(changeJudiciaryPayload()));
    }

    private ListingCommandClient clientFor(final String baseUrl) {
        return new ListingCommandClient(systemUserConfig, baseUrl, 5, 10);
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private JsonObject changeJudiciaryPayload() {
        return Json.createObjectBuilder()
                .add("hearings", Json.createArrayBuilder().add(UUID.randomUUID().toString()))
                .add("judiciary", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("judicialId", UUID.randomUUID().toString())
                                .add("judicialRoleType", Json.createObjectBuilder()
                                        .add("judiciaryType", "Magistrate"))))
                .build();
    }
}
