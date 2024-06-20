package uk.gov.moj.cpp.courtscheduler.persist.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Entity
@Table(name = "judiciary_mapping")
public class Judiciary {
    @Id
    @Column(name = "judiciary_id", nullable = false)
    private String id;
    private Integer seqId;
    @Column(name = "title", nullable = false)
    private String titlePrefix;
    private String titlePrefixWelsh;
    private String titleJudicialPrefix;
    private String titleJudicialPrefixWelsh;
    private String titleSuffix;
    private String titleSuffixWelsh;
    @Column(name = "surname", nullable = false)
    private String surname;
    @Column(name = "forenames", nullable = false)
    private String forenames;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "judiciary_type", nullable = false)
    private String judiciaryType;
    private String personId;
    private String validFrom;
    private String validTo;
    private String emailAddress;
    private String cpUserId;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_on", nullable = false)
    private Timestamp createdOn;

    public Judiciary() {
        //For JPA
    }


    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Integer getSeqId() {
        return seqId;
    }

    public void setSeqId(final Integer seqId) {
        this.seqId = seqId;
    }

    public String getTitlePrefix() {
        return titlePrefix;
    }

    public void setTitlePrefix(final String titlePrefix) {
        this.titlePrefix = titlePrefix;
    }

    public String getTitlePrefixWelsh() {
        return titlePrefixWelsh;
    }

    public void setTitlePrefixWelsh(final String titlePrefixWelsh) {
        this.titlePrefixWelsh = titlePrefixWelsh;
    }

    public String getTitleJudicialPrefix() {
        return titleJudicialPrefix;
    }

    public void setTitleJudicialPrefix(final String titleJudicialPrefix) {
        this.titleJudicialPrefix = titleJudicialPrefix;
    }

    public String getTitleJudicialPrefixWelsh() {
        return titleJudicialPrefixWelsh;
    }

    public void setTitleJudicialPrefixWelsh(final String titleJudicialPrefixWelsh) {
        this.titleJudicialPrefixWelsh = titleJudicialPrefixWelsh;
    }

    public String getTitleSuffix() {
        return titleSuffix;
    }

    public void setTitleSuffix(final String titleSuffix) {
        this.titleSuffix = titleSuffix;
    }

    public String getTitleSuffixWelsh() {
        return titleSuffixWelsh;
    }

    public void setTitleSuffixWelsh(final String titleSuffixWelsh) {
        this.titleSuffixWelsh = titleSuffixWelsh;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(final String surname) {
        this.surname = surname;
    }

    public String getForenames() {
        return forenames;
    }

    public void setForenames(final String forenames) {
        this.forenames = forenames;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(final String fullName) {
        this.fullName = fullName;
    }

    public String getJudiciaryType() {
        return judiciaryType;
    }

    public void setJudiciaryType(final String judiciaryType) {
        this.judiciaryType = judiciaryType;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(final String personId) {
        this.personId = personId;
    }

    public String getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(final String validFrom) {
        this.validFrom = validFrom;
    }

    public String getValidTo() {
        return validTo;
    }

    public void setValidTo(final String validTo) {
        this.validTo = validTo;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(final String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getCpUserId() {
        return cpUserId;
    }

    public void setCpUserId(final String cpUserId) {
        this.cpUserId = cpUserId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Timestamp getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Timestamp createdOn) {
        this.createdOn = createdOn;
    }
}

