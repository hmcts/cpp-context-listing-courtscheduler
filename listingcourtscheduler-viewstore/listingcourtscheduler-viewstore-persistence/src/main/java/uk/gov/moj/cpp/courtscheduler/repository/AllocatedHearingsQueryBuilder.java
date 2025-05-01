package uk.gov.moj.cpp.courtscheduler.repository;

import static java.util.Arrays.stream;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.COURT_ROOM;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.COURT_ROOM_NUMBER;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.COURT_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.OU_CODE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.OU_LEVEL2;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.PAGE_NUMBER;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.PAGE_SIZE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.SESSION_END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.SESSION_START_DATE;

import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class AllocatedHearingsQueryBuilder {

    private Map<String, Object> pagedQueryParamMap;
    private String allocatedHearingsQuery;

    public AllocatedHearingsQueryBuilder(HearingSlotRequestParam hearingIdsReq) {
        bindQueryParamsFromHearingReq(hearingIdsReq);
        generateAllocatedHearingsQuery();
    }

    public Map<String, Object> getPagedQueryParamMap() {
        return pagedQueryParamMap;
    }

    public String getAllocatedHearingsQuery() {
        return allocatedHearingsQuery;
    }

    private void generateAllocatedHearingsQuery() {
        final StringBuilder queryStrBuilder =
                new StringBuilder("select al.hearing_id, count(*) over() as totalCount " +
                                  "from allocated_listings al, court_schedule cs ");
        queryStrBuilder.append("where al.court_schedule_id = cs.id and cs.active = true ");
        queryStrBuilder.append("and cs.panel in (:panel) ");
        queryStrBuilder.append("and cs.session_start >= :sessionStartDate ");
        queryStrBuilder.append("and cs.session_start <= :sessionEndDate ");
        addCondition(OU_LEVEL2.getLabel(), "and cs.operational_unit = :oucodeL2Code ", queryStrBuilder);
        addCondition(OU_CODE.getLabel(), "and cs.oucode = :ouCode ", queryStrBuilder);
        addCondition(COURT_ROOM.getLabel(), "and cs.court_room_id = :courtRoomId ", queryStrBuilder);
        addCondition(COURT_ROOM_NUMBER.getLabel(), "and cs.court_room_number = :courtRoomNumber ", queryStrBuilder);
        addCondition(BUSINESS_TYPE.getLabel(), "and cs.rota_business_type = :businessType ", queryStrBuilder);
        addCondition(COURT_SESSION.getLabel(), "and cs.court_session = :courtSession ", queryStrBuilder);

        queryStrBuilder.append("order by cs.session_start, " +
                "cs.court_house_name, " +
                "cs.court_room_name, " +
                "cs.court_session, " +
                "al.hearing_start_time ");

        queryStrBuilder.append("LIMIT :pageSize ");
        queryStrBuilder.append("OFFSET :pageNumber ");

        allocatedHearingsQuery = queryStrBuilder.toString();
    }

    private void bindQueryParamsFromHearingReq(HearingSlotRequestParam hearingIdsReq) {
        pagedQueryParamMap = new HashMap<>();
        pagedQueryParamMap.put(PANEL.getLabel(), stream(hearingIdsReq.panel().split(",")).map(String::trim).toList());
        pagedQueryParamMap.put(SESSION_START_DATE.getLabel(), LocalDate.parse(hearingIdsReq.sessionStartDate()));
        pagedQueryParamMap.put(SESSION_END_DATE.getLabel(), LocalDate.parse(hearingIdsReq.sessionEndDate()));
        pagedQueryParamMap.put(PAGE_SIZE.getLabel(), Integer.parseInt(hearingIdsReq.pageSize()));
        pagedQueryParamMap.put(PAGE_NUMBER.getLabel(), Integer.parseInt(hearingIdsReq.pageNumber()) - 1);

        addOptionalParam(OU_LEVEL2.getLabel(), hearingIdsReq.oucodeL2Code());
        addOptionalParam(OU_CODE.getLabel(), hearingIdsReq.ouCode());
        addOptionalParam(COURT_ROOM.getLabel(), hearingIdsReq.courtRoomId());
        addOptionalParam(COURT_ROOM_NUMBER.getLabel(), hearingIdsReq.courtRoomNumber());
        addOptionalParam(BUSINESS_TYPE.getLabel(), hearingIdsReq.businessType());
        addOptionalParam(COURT_SESSION.getLabel(), hearingIdsReq.courtSession());
    }

    private void addOptionalParam(String name, String val) {
        if (StringUtils.isNotBlank(val)) {
            pagedQueryParamMap.put(name, val);
        }
    }

    private void addCondition(String name, String condition, StringBuilder queryBuilder) {
        if (pagedQueryParamMap.containsKey(name)) {
            queryBuilder.append(condition);
        }
    }
}
