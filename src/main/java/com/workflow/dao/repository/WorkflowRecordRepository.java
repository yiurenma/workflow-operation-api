package com.workflow.dao.repository;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Date;
import java.util.List;

@Repository
@RequestMapping(value = "/workflow/")
@RepositoryRestResource(path = "record")
@Tag(name = "DB Repository", description = "[Tracking] Workflow DB Records")
public interface WorkflowRecordRepository extends
        QuerydslPredicateExecutor<WorkflowRecord>,
        JpaRepository<WorkflowRecord, Long>,
        JpaSpecificationExecutor<WorkflowRecord>,
        RevisionRepository<WorkflowRecord, Long, Integer> {

    @Query(nativeQuery = true, value = "" +
            "SELECT id " +
            "FROM workflow_record " +
            "WHERE application_name=?1 " +
            "AND created_date_time >= ?2 " +
            "AND created_date_time <= ?3 "
    )
    List<Long> findIdsByApplicationNameAndCreatedDateTimeAfterAndCreatedDateTimeBefore
            (String applicationName, Date afterDateTime, Date beforeDateTime);

    @Query(nativeQuery = true, value = "" +
            "SELECT created_date_time " +
            "FROM workflow_record " +
            "WHERE id=?1 "
    )
    Date findCreatedDateTimeById(Long id);

    @Query(nativeQuery = true, value = "" +
            "SELECT id " +
            "FROM workflow_record " +
            "WHERE created_date_time < ?1 "
    )
    List<Long> findByCreatedDateTimeBefore(Date endDateTime);

    @Query(nativeQuery = true, value = "" +
            "SELECT id " +
            "FROM workflow_record " +
            "WHERE application_name=?1 " +
            "AND created_date_time >= ?3 " +
            "AND created_date_time <= ?4 " +
            "AND overall_status IN ?2 " +
            "AND retry_times is null"
    )
    List<Long> findIdsByApplicationNameAndOverallStatusInAndRetryTimesIsNULLAndCreatedDateTimeAfterAndCreatedDateTimeBefore
            (String applicationName, List<String> overallStatusList, Date afterDateTime, Date beforeDateTime);

    @Query(nativeQuery = true, value = "" +
            "SELECT origin_workflow_record_id " +
            "FROM workflow_record " +
            "WHERE id=?1 ")
    Long findOriginWorkflowRecordIdById(Long id);

    @Query(nativeQuery = true, value = "" +
            "SELECT id " +
            "FROM workflow_record " +
            "WHERE origin_workflow_record_id=?1 ")
    List<Long> findIdsByOriginWorkflowRecordId(Long originWorkflowRecordId);

    @Query(nativeQuery = true, value = "" +
            "SELECT id " +
            "FROM workflow_record " +
            "WHERE request_correlation_id=?1 AND application_name=?2")
    List<Long> findIdsByRequestCorrelationIdAndApplicationName(String requestCorrelationId, String applicationName);

    @Query(nativeQuery = true, value = "" +
            "SELECT id, tracking_number, created_date_time " +
            "FROM workflow_record " +
            "WHERE application_name=?1 " +
            "AND created_date_time >= ?2 " +
            "AND created_date_time <= ?3 " +
            "AND tracking_number is not null " +
            "AND ( (NOT (sms_status IN ?4 OR email_status IN ?5 OR push_notification_status IN ?6)) OR (sms_status is null AND email_status is null AND push_notification_status is null))"
    )
    List<Object[]> findByNotFinalStatusRecord(
            String applicationName,
            Date startDateTime,
            Date endDateTime,
            List<String> smsIgnoreStatus,
            List<String> emailIgnoreStatus,
            List<String> pushIgnoreStatus
    );
}
