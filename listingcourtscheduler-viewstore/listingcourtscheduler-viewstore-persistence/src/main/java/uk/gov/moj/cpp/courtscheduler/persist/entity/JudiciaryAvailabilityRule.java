package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @Column(name = "group_type", nullable = false, length = 20)
    private String group; // Available or Unavailable

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "recurring_type", length = 20)
    private String recurringType; // Weekly or Monthly

    @ElementCollection(fetch = FetchType.EAGER)
    @javax.persistence.CollectionTable(
            name = "judiciary_availability_rule_repeat_days",
            joinColumns = @javax.persistence.JoinColumn(name = "rule_id")
    )
    private List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();

    @Column(name = "reason", length = 500)
    private String reason;

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

    public String getGroup() {
        return this.group;
    }

    public void setGroup(String group) {
        this.group = group;
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

    public String getRecurringType() {
        return this.recurringType;
    }

    public void setRecurringType(String recurringType) {
        this.recurringType = recurringType;
    }

    public List<JudiciaryAvailabilityRuleRepeatDay> getRepeatDays() {
        return this.repeatDays;
    }

    public void setRepeatDays(List<JudiciaryAvailabilityRuleRepeatDay> repeatDays) {
        this.repeatDays = repeatDays;
    }

    public String getReason() {
        return this.reason;
    }

    public void setReason(String reason) {
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final JudiciaryAvailabilityRule that = (JudiciaryAvailabilityRule) o;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return "JudiciaryAvailabilityRule{" +
                "id='" + this.id + '\'' +
                ", judiciaryId='" + this.judiciaryId + '\'' +
                ", courtHouseId='" + this.courtHouseId + '\'' +
                ", group='" + this.group + '\'' +
                ", fromDate=" + this.fromDate +
                ", toDate=" + this.toDate +
                ", recurringType='" + this.recurringType + '\'' +
                ", repeatDays=" + this.repeatDays +
                ", reason='" + this.reason + '\'' +
                ", createdOn=" + this.createdOn +
                ", updatedOn=" + this.updatedOn +
                '}';
    }
}

