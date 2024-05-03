package uk.gov.moj.cpp.courtscheduler.persist.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.util.Objects;

@SuppressWarnings({"squid:S1845"})
@Entity
@Table(name = "rota_file_process_history")
public class RotaFileProcessHistory {

    @Id
    private RotaFileProcessHistoryKey id;

    @Column(name = "processed_on", nullable = false)
    private Timestamp processedOn;

    public RotaFileProcessHistory() {
        //For JPA
    }

    public RotaFileProcessHistoryKey getId() {
        return id;
    }

    public void setId(RotaFileProcessHistoryKey id) {
        this.id = id;
    }

    public Timestamp getProcessedOn() {
        return new Timestamp(processedOn.getTime());
    }

    public void setProcessedOn(Timestamp processedOn) {
        this.processedOn = new Timestamp(processedOn.getTime());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RotaFileProcessHistory that = (RotaFileProcessHistory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RotaFileProcessHistory{" +
                "id=" + id +
                ", processedOn=" + processedOn +
                '}';
    }
}
