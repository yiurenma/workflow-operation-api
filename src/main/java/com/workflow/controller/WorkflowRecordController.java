package com.workflow.controller;

import com.workflow.common.exception.ApiBusinessException;
import com.workflow.common.exception.ApiErrorCatalog;
import com.workflow.controller.domain.WorkflowRecordDetail;
import com.workflow.dao.repository.WorkflowRecord;
import com.workflow.dao.repository.WorkflowRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(
        name = "Workflow Record API",
        description = "Execution record query APIs: paginated list with filters and single-record detail with children."
)
@Validated
@RequiredArgsConstructor
public class WorkflowRecordController {

    private final WorkflowRecordRepository workflowRecordRepository;

    @Operation(
            summary = "List workflow execution records",
            description = """
                    Returns a paginated list of WORKFLOW_RECORD rows.
                    All filter parameters are optional and combinable.

                    Examples:
                    - GET /api/workflow/records?applicationName=APP_A&page=0&size=20
                    - GET /api/workflow/records?overallStatus=FB_ALL_SUCCESS&from=2026-01-01T00:00:00&to=2026-04-05T23:59:59
                    - GET /api/workflow/records?trackingNumber=TRK123&customerId=C001
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Records loaded")
    })
    @GetMapping(value = "/workflow/records", produces = MediaType.APPLICATION_JSON_VALUE)
    public Page<WorkflowRecord> listRecords(
            @RequestParam(required = false) @Parameter(description = "Filter by application name (exact match)") String applicationName,
            @RequestParam(required = false) @Parameter(description = "Filter by overall status (e.g. FB_ALL_SUCCESS, GI_FAIL)") String overallStatus,
            @RequestParam(required = false) @Parameter(description = "Filter by transaction confirmation number (exact match)") String transactionConfirmationNumber,
            @RequestParam(required = false) @Parameter(description = "Filter by tracking number (exact match)") String trackingNumber,
            @RequestParam(required = false) @Parameter(description = "Filter by customer ID (exact match)") String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Filter records created on or after this timestamp (ISO-8601, e.g. 2026-01-01T00:00:00)") Date from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Filter records created on or before this timestamp (ISO-8601, e.g. 2026-04-05T23:59:59)") Date to,
            @ParameterObject Pageable pageable) {

        log.info("Listing workflow records — applicationName={}, overallStatus={}, from={}, to={}",
                applicationName, overallStatus, from, to);
        return workflowRecordRepository.findByFilters(
                applicationName, overallStatus, transactionConfirmationNumber, trackingNumber, customerId, from, to, pageable
        );
    }

    @Operation(
            summary = "Get workflow record detail with children",
            description = """
                    Returns a single WORKFLOW_RECORD by ID, along with all child records
                    (rows where originWorkflowRecordId = {id}).
                    Children include MESSAGE dispatch steps and retry chain entries.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Record detail loaded"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not found. Error code: WF-404-000 (record not found for given id)."
            )
    })
    @GetMapping(value = "/workflow/records/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkflowRecordDetail getRecord(
            @PathVariable @Parameter(description = "Workflow record primary key", example = "42") Long id) {

        WorkflowRecord record = workflowRecordRepository.findById(id)
                .orElseThrow(() -> new ApiBusinessException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCatalog.NOT_FOUND,
                        "Workflow record not found for id: " + id
                ));

        List<WorkflowRecord> children = workflowRecordRepository.findByOriginWorkflowRecordId(id);
        log.info("Fetched record id={} with {} child(ren)", id, children.size());

        return WorkflowRecordDetail.builder()
                .record(record)
                .children(children)
                .build();
    }
}
