package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import uk.gov.moj.cpp.courtscheduler.domain.SessionType;

@Entity
@Table(name = "judiciary_availability_rule")
public class JudiciaryAvailabilityRule {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @Column(name = "judiciary_id", nullable = false, length = 100)
    private String judiciaryId;

    @Column(name = "court_house_id", nullable = false, length = 100)
    private String courtHouseId;

    @OneToMany(mappedBy = "rule", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<JudiciaryUnavailability> unavailabilities = new java.util.ArrayList<>();

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @jakarta.persistence.CollectionTable(
            name = "judiciary_availability_rule_repeat_day",
            joinColumns = @jakarta.persistence.JoinColumn(name = "rule_id")
    )
    private List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type")
    private SessionType sessionType;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_on", nullable = false)
    private Date createdOn;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_on", nullable = false)
    private Date updatedOn;

    public JudiciaryAvailabilityRule() {
        //For JPA
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJudiciaryId() {
        return this.judiciaryId;
    }

    public void setJudiciaryId(String judiciaryId) {
        this.judiciaryId = judiciaryId;
    }

    public String getCourtHouseId() {
        return this.courtHouseId;
    }

    public void setCourtHouseId(String courtHouseId) {
        this.courtHouseId = courtHouseId;
    }

    public java.util.List<JudiciaryUnavailability> getUnavailabilities() {
        return this.unavailabilities;
    }

    public void setUnavailabilities(java.util.List<JudiciaryUnavailability> unavailabilities) {
        this.unavailabilities = unavailabilities;
    }

    public LocalDate getFromDate() {
        return this.fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return this.toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public List<JudiciaryAvailabilityRuleRepeatDay> getRepeatDays() {
        return this.repeatDays;
    }

    public void setRepeatDays(List<JudiciaryAvailabilityRuleRepeatDay> repeatDays) {
        this.repeatDays = repeatDays;
    }

    public Date getCreatedOn() {
        return this.createdOn;
    }

    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    public Date getUpdatedOn() {
        return this.updatedOn;
    }

    public void setUpdatedOn(Date updatedOn) {
        this.updatedOn = updatedOn;
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(final SessionType sessionType) {
        this.sessionType = sessionType;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof final JudiciaryAvailabilityRule that)) return false;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getJudiciaryId(), that.getJudiciaryId()) && Objects.equals(getCourtHouseId(), that.getCourtHouseId()) && Objects.equals(getFromDate(), that.getFromDate()) && Objects.equals(getToDate(), that.getToDate()) && Objects.equals(getRepeatDays(), that.getRepeatDays()) && getSessionType() == that.getSessionType() && Objects.equals(getCreatedOn(), that.getCreatedOn()) && Objects.equals(getUpdatedOn(), that.getUpdatedOn());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getJudiciaryId(), getCourtHouseId(), getFromDate(), getToDate(), getRepeatDays(), getSessionType(), getCreatedOn(), getUpdatedOn());
    }
}

