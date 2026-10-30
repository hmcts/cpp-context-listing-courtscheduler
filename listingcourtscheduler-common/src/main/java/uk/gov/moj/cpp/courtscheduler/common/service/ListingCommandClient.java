package uk.gov.moj.cpp.courtscheduler.common.service;

import uk.gov.moj.cpp.courtscheduler.common.config.CourtSchedulerSystemUserConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Posts commands to the listing context's command API against the configured
 * {@code listing.base-url}, mirroring {@link CommonPlatformQueryClient}.
 *
 * <p>Each call sends:</p>
 * <ul>
 *   <li>{@code CJSCPPUID} = configured system user UUID,</li>
 *   <li>{@code Content-Type} = the command's vendor media type,</li>
 *   <li>the command payload as the JSON body.</li>
 * </ul>
 */
@Component
public class ListingCommandClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListingCommandClient.class);

    private static final String HEARINGS_COMMAND_PATH = "/listing-command-api/command/api/rest/listing/hearings";
    private static final String CHANGE_JUDICIARY_FOR_HEARINGS_MEDIA_TYPE =
            "application/vnd.listing.command.change-judiciary-for-hearings+json";

    private final RestClient restClient;
    private final CourtSchedulerSystemUserConfig systemUserConfig;
    private final String listingBaseUrl;

    public ListingCommandClient(final CourtSchedulerSystemUserConfig systemUserConfig,
                                @Value("${listing.base-url:}") final String listingBaseUrl,
                                @Value("${courtscheduler.http.connect-timeout-seconds:5}") final int connectTimeoutSeconds,
                                @Value("${courtscheduler.http.read-timeout-seconds:30}") final int readTimeoutSeconds) {
        this.systemUserConfig = systemUserConfig;
        this.listingBaseUrl = listingBaseUrl;

        // Same timeout rationale as CommonPlatformQueryClient: a hung listing peer must not
        // wedge the @Async rota pipeline indefinitely.
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        final JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * POSTs a {@code listing.command.change-judiciary-for-hearings} payload to the listing
     * command API. The listing context accepts the command asynchronously (202 Accepted).
     *
     * @param payload the change-judiciary-for-hearings payload (hearings + judiciary)
     */
    public void changeJudiciaryForHearings(final JsonObject payload) {
        post(HEARINGS_COMMAND_PATH, CHANGE_JUDICIARY_FOR_HEARINGS_MEDIA_TYPE, payload);
    }

    private void post(final String path, final String contentMediaType, final JsonObject payload) {
        if (listingBaseUrl == null || listingBaseUrl.isBlank()) {
            throw new IllegalStateException("listing.base-url is not configured for path: " + path);
        }

        final URI uri = UriComponentsBuilder
                .fromUriString(listingBaseUrl)
                .path(path)
                .build()
                .toUri();

        LOGGER.debug("Posting {} to {}", contentMediaType, uri);
        restClient.post()
                .uri(uri)
                .contentType(MediaType.parseMediaType(contentMediaType))
                .header("CJSCPPUID", systemUserConfig.getRequiredSystemUserId())
                .body(payload.toString())
                .retrieve()
                .toBodilessEntity();
    }
}
