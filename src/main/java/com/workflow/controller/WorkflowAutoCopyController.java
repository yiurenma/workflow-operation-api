package com.workflow.controller;

import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "Workflow AutoCopy API", description = "Copy workflow from one application to another")
@Validated
@RequiredArgsConstructor
public class WorkflowAutoCopyController {

    private final WorkflowEntitySettingRepository workflowEntitySettingRepository;
    private final WorkflowGetController workflowGetController;
    private final WorkflowUpdateController workflowUpdateController;

    @PostMapping(value = "/workflow/autoCopy", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkFlow autoCopyWorkFlow(
            @RequestHeader(HttpHeaders.CONTENT_TYPE) @Parameter(example = "application/json", required = true) @NotNull String contentType,
            @RequestParam(required = true) @Parameter(example = "UK_DRFI") @NotNull String fromApplicationName,
            @RequestParam(required = true) @Parameter(example = "UK_DRFI") @NotNull String toApplicationName) {

        List<WorkflowEntitySetting> entitySettingList =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(fromApplicationName);

        if (entitySettingList.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Source application name must exist exactly once; found: " + entitySettingList.size());
        }

        WorkflowEntitySetting originalSetting = entitySettingList.get(0);
        WorkflowEntitySetting newSetting = new WorkflowEntitySetting();
        org.springframework.beans.BeanUtils.copyProperties(originalSetting, newSetting);
        newSetting.setId(null);
        newSetting.setApplicationName(toApplicationName);
        newSetting.setTrackingServiceProviderActionId(originalSetting.getTrackingServiceProviderActionId());

        workflowEntitySettingRepository.save(newSetting);

        WorkFlow workFlow = workflowGetController.getWorkFlow(fromApplicationName);
        return workflowUpdateController.updateWorkFlow(newSetting.getApplicationName(), workFlow);
    }
}
