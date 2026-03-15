package com.workflow.controller;

import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(
        name = "Workflow API",
        description = "Core workflow management APIs: query, create/update, delete, and copy by application name."
)
@Validated
@RequiredArgsConstructor
public class WorkflowAutoCopyController {

    private final WorkflowEntitySettingRepository workflowEntitySettingRepository;
    private final WorkflowGetController workflowGetController;
    private final WorkflowUpdateController workflowUpdateController;

    @Operation(
            summary = "Copy workflow between applications",
            description = "Copies source entity setting metadata and workflow content from one application to another."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workflow copied successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid source/target application names")
    })
    @PostMapping(value = "/workflow/autoCopy", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkFlow autoCopyWorkFlow(
            @RequestParam(required = true) @Parameter(example = "APP_A", required = true,
                    description = "Source application name") @NotNull String fromApplicationName,
            @RequestParam(required = true) @Parameter(example = "APP_A_COPY", required = true,
                    description = "Target application name") @NotNull String toApplicationName) {

        if (fromApplicationName.equals(toApplicationName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Source and target application names must be different");
        }

        List<WorkflowEntitySetting> entitySettingList =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(fromApplicationName);

        if (entitySettingList.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Source application name must exist exactly once; found: " + entitySettingList.size());
        }

        WorkflowEntitySetting originalSetting = entitySettingList.get(0);
        List<WorkflowEntitySetting> targetSettingList =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(toApplicationName);
        if (targetSettingList.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Target application name must exist at most once; found: " + targetSettingList.size());
        }

        WorkflowEntitySetting targetSetting = targetSettingList.isEmpty()
                ? new WorkflowEntitySetting()
                : targetSettingList.get(0);

        // Copy non-ID metadata to keep target config aligned with source while preserving target app name.
        org.springframework.beans.BeanUtils.copyProperties(
                originalSetting,
                targetSetting,
                "id",
                "applicationName",
                "workflow"
        );
        targetSetting.setApplicationName(toApplicationName);
        workflowEntitySettingRepository.saveAndFlush(targetSetting);

        WorkFlow workFlow = workflowGetController.getWorkFlow(fromApplicationName);
        return workflowUpdateController.updateWorkFlow(toApplicationName, workFlow);
    }
}
