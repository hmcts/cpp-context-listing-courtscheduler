package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public class Venue {
    private Integer locationId;
    private Integer venueId;
    private String venueName;

    public Venue(final Integer locationId, final Integer venueId, final String venueName) {
        this.locationId = locationId;
        this.venueId = venueId;
        this.venueName = venueName;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(final String venueName) {
        this.venueName = venueName;
    }

    public Integer getLocationId() {
        return locationId;
    }

    public void setLocationId(final Integer locationId) {
        this.locationId = locationId;
    }

    public Integer getVenueId() {
        return venueId;
    }

    public void setVenueId(final Integer venueId) {
            this.venueId = venueId;

    }

    @Override
    public boolean equals(final Object v) {
        if (this == v) {
            return true;
        }
        if (!(v instanceof Venue)) {
            return false;
        }
        final Venue venue = (Venue) v;
        return Objects.equals(locationId, venue.locationId) &&
                Objects.equals(venueId, venue.venueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, venueId,venueName);
    }

    @Override
    public String toString() {
        return "Venue{" +
                "locationId=" + locationId +
                ", venueId='" + venueId + '\'' +
                ", venueName='"+ venueName+ '\''+
                '}';
    }
}
