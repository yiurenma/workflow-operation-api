package com.workflow.controller.domain;

import com.workflow.dao.repository.WorkflowRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Workflow record detail with child records (MESSAGE steps and retry chain)")
public class WorkflowRecordDetail {

    @Schema(description = "The requested workflow record")
    private WorkflowRecord record;

    @Schema(description = "Child records where originWorkflowRecordId = record.id (MESSAGE dispatch steps and retries)")
    private List<WorkflowRecord> children;
}
