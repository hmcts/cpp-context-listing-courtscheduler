package uk.gov.moj.cpp.courtscheduler.integration.utils;

/**
 * Replacement for {@code uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams}.
 * Pure data carrier used by {@link RestPoller} — keeps the legacy IT test API intact.
 */
public final class RequestParams {

    private final String url;
    private final String mediaType;
    private final String userId;

    public RequestParams(final String url, final String mediaType, final String userId) {
        this.url = url;
        this.mediaType = mediaType;
        this.userId = userId;
    }

    public String getUrl() {
        return url;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getUserId() {
        return userId;
    }

    public static Builder requestParams(final String url, final String mediaType) {
        return new Builder(url, mediaType);
    }

    public static final class Builder {
        private final String url;
        private final String mediaType;
        private String userId;

        private Builder(final String url, final String mediaType) {
            this.url = url;
            this.mediaType = mediaType;
        }

        public Builder withUserId(final String userId) {
            this.userId = userId;
            return this;
        }

        public RequestParams build() {
            return new RequestParams(url, mediaType, userId);
        }
    }
}
