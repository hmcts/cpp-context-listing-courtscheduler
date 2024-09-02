package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import java.time.LocalDate;
import java.util.Comparator;

public class ProvisionalDataLookUpKey implements Comparable<ProvisionalDataLookUpKey> {

    private final int populateCycle;
    private final LocalDate extractDate;

    @Override
    @SuppressWarnings("squid:S00121")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProvisionalDataLookUpKey that = (ProvisionalDataLookUpKey) o;

        if (populateCycle != that.populateCycle) return false;
        return extractDate != null ? extractDate.equals(that.extractDate) : that.extractDate == null;
    }

    @Override
    public int hashCode() {
        int result = populateCycle;
        result = 31 * result + (extractDate != null ? extractDate.hashCode() : 0);
        return result;
    }


    @Override
    public String toString() {
        return "ProvisionalDataLookUpKey{" +
                "populateCycle=" + populateCycle +
                ", extractDate=" + extractDate +
                '}';
    }


    public ProvisionalDataLookUpKey(final int populateCycle,
                                    final LocalDate extractDate) {
        this.populateCycle = populateCycle;
        this.extractDate = extractDate;
    }

    public int getPopulateCycle() {
        return populateCycle;
    }

    public LocalDate getExtractDate() {
        return extractDate;
    }

    @Override
    public int compareTo(ProvisionalDataLookUpKey o) {
       return Comparator.comparing(ProvisionalDataLookUpKey::getPopulateCycle)
                .thenComparing(ProvisionalDataLookUpKey::getExtractDate)
                .compare(this, o);
    }
}
