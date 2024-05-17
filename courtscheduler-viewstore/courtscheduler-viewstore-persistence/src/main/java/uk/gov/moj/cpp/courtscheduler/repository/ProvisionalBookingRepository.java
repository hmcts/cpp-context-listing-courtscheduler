package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.AbstractFullEntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey_;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking_;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Root;
import java.util.*;

@Repository(forEntity = ProvisionalBooking.class)
@java.lang.SuppressWarnings("squid:S3740")
public abstract class ProvisionalBookingRepository extends AbstractFullEntityRepository<ProvisionalBooking, ProvisionalBookingKey> {

    @Inject
    EntityManager em;

    public Map<String, Date> getCourtScheduleInfo(final List<String> bookingSlots) {


        final Map<String, Date> courtScheduleInfoMap = new HashMap<>();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ProvisionalBooking> cq = cb.createQuery(ProvisionalBooking.class);
        Root<ProvisionalBooking> root = cq.from(ProvisionalBooking.class);
        ParameterExpression<List> bookingSlotsExpr = cb.parameter(List.class);
        cq.where(root.get(ProvisionalBooking_.provisionalBookingKey).get(ProvisionalBookingKey_.bookingId).in(bookingSlotsExpr));
        TypedQuery<ProvisionalBooking> tq = em.createQuery(cq);
        tq.setParameter(bookingSlotsExpr, bookingSlots);
        tq.getResultList().forEach(provisionalBooking -> {
            courtScheduleInfoMap.put(provisionalBooking.getProvisionalBookingKey().getCourtSchedule().getCourtScheduleId(), provisionalBooking.getHearingStartTime());
        });

        return courtScheduleInfoMap;
    }

    public Optional<ProvisionalBooking> findByBookingId(String bookingId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ProvisionalBooking> cq = cb.createQuery(ProvisionalBooking.class);
        Root<ProvisionalBooking> root = cq.from(ProvisionalBooking.class);
        ParameterExpression<List> bookingSlotsExpr = cb.parameter(List.class);
        cq.where(root.get(ProvisionalBooking_.provisionalBookingKey).get(ProvisionalBookingKey_.bookingId).in(bookingSlotsExpr));
        TypedQuery<ProvisionalBooking> tq = em.createQuery(cq);
        tq.setParameter(bookingSlotsExpr, Collections.singletonList(bookingId));
        return tq.getResultList().stream().findFirst();
    }

    abstract List<ProvisionalBooking> findByBookingIdIn(final List<String> bookingId);
}
