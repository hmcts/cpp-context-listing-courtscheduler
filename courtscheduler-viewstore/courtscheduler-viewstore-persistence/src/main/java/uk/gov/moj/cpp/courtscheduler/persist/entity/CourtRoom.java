package uk.gov.moj.cpp.courtscheduler.persist.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;

@SuppressWarnings({"squid:S1845"})
@Entity
@Table(name = "court_room_mapping")
public class CourtRoom {

    @Id
    private String id;
    private Venue venue;

    @Column(name = "court_house_id", nullable = false)
    private Integer courtHouseId;
    @Column(name = "oucode", nullable = false)
    private String oucode;
    @Column(name = "court_house_name", nullable = false)
    private String oucodeL3Name;
    @Column(name = "court_room_name", nullable = false)
    private String courtroomName;
    @Column(name = "court_room_number", nullable = false)
    private Integer courtroomId;
    @Column(name = "operation_unit", nullable = false)
    private String validFrom;
    @Column(name = "active", nullable = false)
    private Boolean active;


    public CourtRoom() {
        //For JPA
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public Integer getCourtHouseId() {
        return courtHouseId;
    }

    public void setCourtHouseId(Integer courtHouseId) {
        this.courtHouseId = courtHouseId;
    }

    public String getOucode() {
        return oucode;
    }

    public void setOucode(String oucode) {
        this.oucode = oucode;
    }

    public String getOucodeL3Name() {
        return oucodeL3Name;
    }

    public void setOucodeL3Name(String oucodeL3Name) {
        this.oucodeL3Name = oucodeL3Name;
    }

    public String getCourtroomName() {
        return courtroomName;
    }

    public void setCourtroomName(String courtroomName) {
        this.courtroomName = courtroomName;
    }

    public Integer getCourtroomId() {
        return courtroomId;
    }

    public void setCourtroomId(Integer courtroomId) {
        this.courtroomId = courtroomId;
    }

    public String getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CourtRoom courtRoom = (CourtRoom) o;
        return Objects.equals(venue, courtRoom.venue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(venue);
    }
}
