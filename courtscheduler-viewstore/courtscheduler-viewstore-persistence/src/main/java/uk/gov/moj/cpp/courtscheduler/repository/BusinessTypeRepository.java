package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BusinessType;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BusinessTypeKey;

@Repository(forEntity = BusinessType.class)
public interface  BusinessTypeRepository extends EntityRepository<BusinessType, BusinessTypeKey> {
    BusinessType findByTypeCode(String typeCode);
}
