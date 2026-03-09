package com.workflow.controller;

import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMapping;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMappingRepository;
import com.workflow.dao.repository.WorkflowReportRepository;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Workflow Delete API", description = "Delete workflow by application name")
@Validated
@RequiredArgsConstructor
public class WorkflowDeleteController {

    private final WorkflowEntitySettingRepository workflowEntitySettingRepository;
    private final WorkflowEntityAndLinkingIdMappingRepository workflowEntityAndLinkingIdMappingRepository;
    private final WorkflowReportRepository workflowReportRepository;

    @DeleteMapping(value = "/workflow", produces = MediaType.APPLICATION_JSON_VALUE)
    public void deleteWorkFlow(
            @RequestParam(required = true) @Parameter(example = "UK_DRFI", required = true,
                    description = "Application identifier for the workflow") @NotNull String applicationName) {

        List<WorkflowEntitySetting> entitySettingList =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(applicationName);

        if (entitySettingList.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Application name must exist exactly once; found: " + entitySettingList.size());
        }

        Long entitySettingId = entitySettingList.get(0).getId();
        if (!workflowReportRepository.findByWorkflowEntitySetting_Id(entitySettingId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete workflow: reports exist for this application");
        }

        List<Long> mappingIds = workflowEntityAndLinkingIdMappingRepository
                .findAllByWorkflowEntitySettingId(entitySettingId)
                .stream()
                .map(WorkflowEntityAndLinkingIdMapping::getId)
                .toList();
        log.info("Going to delete entity and linking id: {}", mappingIds);
        workflowEntityAndLinkingIdMappingRepository.deleteAllByIdInBatch(mappingIds);
        log.info("Going to delete entity: {} {}", applicationName, entitySettingId);
        workflowEntitySettingRepository.deleteById(entitySettingId);
        //TODO: delete workflow rule and type and linking id mapping
    }
}
