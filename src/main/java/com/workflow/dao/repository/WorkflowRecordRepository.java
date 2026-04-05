package com.workflow.dao.repository;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(exported = false)
@Tag(name = "DB Repository", description = "[Execution] Workflow Record")
public interface WorkflowRecordRepository extends JpaRepository<WorkflowRecord, Long> {

    @Query("""
            SELECT r FROM WorkflowRecord r
            WHERE (:applicationName IS NULL OR r.applicationName = :applicationName)
              AND (:overallStatus   IS NULL OR r.overallStatus = :overallStatus)
              AND (:transactionConfirmationNumber IS NULL OR r.transactionConfirmationNumber = :transactionConfirmationNumber)
              AND (:trackingNumber  IS NULL OR r.trackingNumber = :trackingNumber)
              AND (:customerId      IS NULL OR r.customerId = :customerId)
              AND (:from            IS NULL OR r.createdDateTime >= :from)
              AND (:to              IS NULL OR r.createdDateTime <= :to)
            """)
    Page<WorkflowRecord> findByFilters(
            @Param("applicationName") String applicationName,
            @Param("overallStatus") String overallStatus,
            @Param("transactionConfirmationNumber") String transactionConfirmationNumber,
            @Param("trackingNumber") String trackingNumber,
            @Param("customerId") String customerId,
            @Param("from") Date from,
            @Param("to") Date to,
            Pageable pageable
    );

    List<WorkflowRecord> findByOriginWorkflowRecordId(Long originWorkflowRecordId);
}
