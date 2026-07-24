package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;


@Embeddable
public class Venue implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "location_id", nullable = false)
    private Integer locationId;
    @Column(name = "venue_id", nullable = false)
    private Integer venueId;
    @Column(name = "venue_name", nullable = false)
    private String venueName;

    public Venue() {
        //For JPA
    }

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
