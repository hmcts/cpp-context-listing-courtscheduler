package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class Judiciary {
    private String id;
    private Integer seqId;
    private String titlePrefix;
    private String titlePrefixWelsh;
    private String titleJudicialPrefix;
    private String titleJudicialPrefixWelsh;
    private String titleSuffix;
    private String titleSuffixWelsh;
    private String surname;
    private String forenames;
    private String judiciaryType;
    private String personId;
    private String validFrom;
    private String validTo;
    private String emailAddress;
    private String cpUserId;
    private List<JudiciarySpecialismType> specialisms;
    private String requestedName;

    public Judiciary() {
    }

    public Judiciary(final String id, final String title, final String forenames, final String surname, final String email, final String judiciaryType) {
        this.id = id;
        this.titlePrefix = title;
        this.forenames = forenames;
        this.surname = surname;
        this.emailAddress = email;
        this.judiciaryType = judiciaryType;
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

    public List<JudiciarySpecialismType> getSpecialisms() {
        return specialisms;
    }

    public void setSpecialisms(final List<JudiciarySpecialismType> specialisms) {
        this.specialisms = specialisms;
    }

    public String getRequestedName() {
        return requestedName;
    }

    public void setRequestedName(final String requestedName) {
        this.requestedName = requestedName;
    }

    public static final class JudiciaryBuilder {
        private String id;
        private Integer seqId;
        private String titlePrefix;
        private String titlePrefixWelsh;
        private String titleJudicialPrefix;
        private String titleJudicialPrefixWelsh;
        private String titleSuffix;
        private String titleSuffixWelsh;
        private String surname;
        private String forenames;
        private String judiciaryType;
        private String personId;
        private String validFrom;
        private String validTo;
        private String emailAddress;
        private String cpUserId;
        private List<JudiciarySpecialismType> specialisms;
        private String requestedName;

        private JudiciaryBuilder() {}

        public static JudiciaryBuilder aJudiciary() {return new JudiciaryBuilder();}

        public JudiciaryBuilder withId(final String id) {
            this.id = id;
            return this;
        }

        public JudiciaryBuilder withSeqId(final Integer seqId) {
            this.seqId = seqId;
            return this;
        }

        public JudiciaryBuilder withTitlePrefix(final String titlePrefix) {
            this.titlePrefix = titlePrefix;
            return this;
        }

        public JudiciaryBuilder withTitlePrefixWelsh(final String titlePrefixWelsh) {
            this.titlePrefixWelsh = titlePrefixWelsh;
            return this;
        }

        public JudiciaryBuilder withTitleJudicialPrefix(final String titleJudicialPrefix) {
            this.titleJudicialPrefix = titleJudicialPrefix;
            return this;
        }

        public JudiciaryBuilder withTitleJudicialPrefixWelsh(final String titleJudicialPrefixWelsh) {
            this.titleJudicialPrefixWelsh = titleJudicialPrefixWelsh;
            return this;
        }

        public JudiciaryBuilder withTitleSuffix(final String titleSuffix) {
            this.titleSuffix = titleSuffix;
            return this;
        }

        public JudiciaryBuilder withTitleSuffixWelsh(final String titleSuffixWelsh) {
            this.titleSuffixWelsh = titleSuffixWelsh;
            return this;
        }

        public JudiciaryBuilder withSurname(final String surname) {
            this.surname = surname;
            return this;
        }

        public JudiciaryBuilder withForenames(final String forenames) {
            this.forenames = forenames;
            return this;
        }

        public JudiciaryBuilder withJudiciaryType(final String judiaryType) {
            this.judiciaryType = judiaryType;
            return this;
        }

        public JudiciaryBuilder withPersonId(final String personId) {
            this.personId = personId;
            return this;
        }

        public JudiciaryBuilder withValidFrom(final String validFrom) {
            this.validFrom = validFrom;
            return this;
        }

        public JudiciaryBuilder withValidTo(final String validTo) {
            this.validTo = validTo;
            return this;
        }

        public JudiciaryBuilder withEmailAddress(final String emailAddress) {
            this.emailAddress = emailAddress;
            return this;
        }

        public JudiciaryBuilder withCpUserId(final String cpUserId) {
            this.cpUserId = cpUserId;
            return this;
        }

        public JudiciaryBuilder withSpecialisms(final List<JudiciarySpecialismType> specialisms) {
            this.specialisms = specialisms;
            return this;
        }

        public JudiciaryBuilder withRequestedName(final String requestedName) {
            this.requestedName = requestedName;
            return this;
        }

        public Judiciary build() {
            final Judiciary judiciary = new Judiciary();
            judiciary.setId(this.id);
            judiciary.setSeqId(this.seqId);
            judiciary.setTitlePrefix(this.titlePrefix);
            judiciary.setTitlePrefixWelsh(this.titlePrefixWelsh);
            judiciary.setTitleJudicialPrefix(this.titleJudicialPrefix);
            judiciary.setTitleJudicialPrefixWelsh(this.titleJudicialPrefixWelsh);
            judiciary.setSurname(this.surname);
            judiciary.setForenames(this.forenames);
            judiciary.setJudiciaryType(this.judiciaryType);
            judiciary.setPersonId(this.personId);
            judiciary.setValidFrom(this.validFrom);
            judiciary.setValidTo(this.validTo);
            judiciary.setEmailAddress(this.emailAddress);
            judiciary.setCpUserId(this.cpUserId);
            judiciary.setTitleSuffix(this.titleSuffix);
            judiciary.setTitleSuffixWelsh(this.titleSuffixWelsh);
            judiciary.setSpecialisms(this.specialisms);
            judiciary.setRequestedName(this.requestedName);
            return judiciary;
        }
    }
}

