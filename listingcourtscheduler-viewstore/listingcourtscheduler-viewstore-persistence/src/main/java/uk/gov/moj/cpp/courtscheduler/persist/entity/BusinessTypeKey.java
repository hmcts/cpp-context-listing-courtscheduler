package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BusinessTypeKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "active", nullable = false)
    private Boolean active;

    public BusinessTypeKey() {
        //For JPA
    }

    public BusinessTypeKey(final UUID id, final Boolean active) {
        this.id = id;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.active);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(this.id, ((BusinessTypeKey) o).id)
                && Objects.equals(this.active, ((BusinessTypeKey) o).active);
    }

    @Override
    public String toString() {
        return "BusinessTypeKey [id=" + id + ", active=" + active + "]";
    }
}