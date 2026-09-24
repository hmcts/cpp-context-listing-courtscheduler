package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionalSessionDateProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionalSessionDateProvider.class.getName());

    private final Map<ProvisionalDataLookUpKey, LocalDate> provisionalDataLookUp = new TreeMap<>();
    private final ProvisionalDataExtractDateInfoProvider provisionalDataExtractDateInfoProvider;
    private final ProvisionalDataDateInfoProvider provisionalDataDateInfoProvider;

    public ProvisionalSessionDateProvider(final ProvisionalDataExtractDateInfoProvider provisionalDataExtractDateInfoProvider,
                                          final ProvisionalDataDateInfoProvider provisionalDataDateInfoProvider,
                                          final int rotaFileCycleLength) {
        this.provisionalDataExtractDateInfoProvider = provisionalDataExtractDateInfoProvider;
        this.provisionalDataDateInfoProvider = provisionalDataDateInfoProvider;
        populateProvisionalDataLookUp(rotaFileCycleLength);
    }

    public LocalDate provisionalDate(final int cycle, final LocalDate sessionDate) {
        return provisionalDataLookUp.get(new ProvisionalDataLookUpKey(cycle, sessionDate));
    }

    @SuppressWarnings("squid:S2629")
    private void populateProvisionalDataLookUp(final int rotaFileCycleLength) {
        final LocalDate provisionalDataStartDate = provisionalDataDateInfoProvider.getProvisionalDataStartDate();
        final LocalDate provisionalDataEndDate = provisionalDataDateInfoProvider.getProvisionalDataEndDate();
        final long cyclesToPopulate = provisionalDataDateInfoProvider.getCyclesToPopulate();
        final LocalDate extractStartDate = provisionalDataExtractDateInfoProvider.getProvisionalDataExtractStartDate();
        LocalDate nextProvisionalDataStartDate = provisionalDataStartDate;

        for (int i = 0; i < cyclesToPopulate; i++) {
            for (int j = 0; j < rotaFileCycleLength; j++) {
                if (nextProvisionalDataStartDate.isBefore(provisionalDataEndDate) ||
                        nextProvisionalDataStartDate.isEqual(provisionalDataEndDate)) {
                    final LocalDate extractDate = extractStartDate.plusDays(j);
                    // A fresh key is required per (cycle, date) pair: each instance is stored as a
                    // distinct Map key below, so reusing/hoisting one would corrupt every entry
                    // already put into provisionalDataLookUp.
                    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                    final ProvisionalDataLookUpKey key = new ProvisionalDataLookUpKey(i, extractDate);
                    provisionalDataLookUp.putIfAbsent(key, nextProvisionalDataStartDate);
                    nextProvisionalDataStartDate = nextProvisionalDataStartDate.plusDays(1);
                }
            }
        }

        LOGGER.info("Populated {} provisional data ", provisionalDataLookUp.size());
    }
}
