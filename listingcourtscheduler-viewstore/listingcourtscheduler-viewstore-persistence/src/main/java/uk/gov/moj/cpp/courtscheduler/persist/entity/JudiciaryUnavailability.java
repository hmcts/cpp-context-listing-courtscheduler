package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "judiciary_unavailability")
public class JudiciaryUnavailability {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "availability_rule_id")
    private JudiciaryAvailabilityRule rule;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private UnavailabilityReason reason;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_on", nullable = false)
    private Date createdOn;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_on", nullable = false)
    private Date updatedOn;

    public JudiciaryUnavailability() {
        //For JPA
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public JudiciaryAvailabilityRule getRule() {
        return this.rule;
    }

    public void setRule(JudiciaryAvailabilityRule rule) {
        this.rule = rule;
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

    public UnavailabilityReason getReason() {
        return this.reason;
    }

    public void setReason(UnavailabilityReason reason) {
        this.reason = reason;
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

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof final JudiciaryUnavailability that)) return false;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getRule(), that.getRule()) && Objects.equals(getFromDate(), that.getFromDate()) && Objects.equals(getToDate(), that.getToDate()) && Objects.equals(getReason(), that.getReason()) && Objects.equals(getCreatedOn(), that.getCreatedOn()) && Objects.equals(getUpdatedOn(), that.getUpdatedOn());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getRule(), getFromDate(), getToDate(), getReason(), getCreatedOn(), getUpdatedOn());
    }
}

