package com.workflow.dao.repository;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(exported = false)
@Tag(name = "DB Repository", description = "[Execution] Workflow Record")
public interface WorkflowRecordRepository
        extends JpaRepository<WorkflowRecord, Long>, JpaSpecificationExecutor<WorkflowRecord> {

    List<WorkflowRecord> findByOriginWorkflowRecordId(Long originWorkflowRecordId);

    @Query("SELECT r.id FROM WorkflowRecord r WHERE r.requestCorrelationId = :requestId AND r.applicationName = :applicationName")
    List<Long> findIdsByRequestCorrelationIdAndApplicationName(
            @Param("requestId") String requestId,
            @Param("applicationName") String applicationName
    );

    @Query("SELECT r.id FROM WorkflowRecord r WHERE r.originWorkflowRecordId = :originId")
    List<Long> findIdsByOriginWorkflowRecordId(@Param("originId") Long originId);
}
