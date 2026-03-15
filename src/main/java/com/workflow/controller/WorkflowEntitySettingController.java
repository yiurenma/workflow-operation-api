package com.workflow.controller;

import com.querydsl.core.types.Predicate;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.history.Revision;
import org.springframework.data.querydsl.binding.QuerydslPredicate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/api/")
@Hidden
@Tag(name = "DB Repository", description = "[Tracking] Workflow Entity Setting")
@Validated
@RequiredArgsConstructor
public class WorkflowEntitySettingController {

    private final WorkflowEntitySettingRepository workflowEntitySettingRepository;

    @Operation(hidden = true)
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workflow/entity-setting",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Page<WorkflowEntitySetting> searchWorkflowEntitySetting(
            @QuerydslPredicate(root = WorkflowEntitySetting.class) Predicate predicate,
            Pageable pageable) {
        return workflowEntitySettingRepository.findAll(predicate, pageable);
    }

    @Operation(summary = "Get entity setting history by applicationName")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Revision history loaded"),
            @ApiResponse(responseCode = "400", description = "applicationName must match exactly one entity setting")
    })
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workflow/entity-setting/history",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Page<Revision<Integer, WorkflowEntitySetting>> getWorkflowEntitySettingHistory(
            @RequestParam @NotBlank @Parameter(example = "APP_A", description = "Application identifier") String applicationName,
            Pageable pageable) {
        List<WorkflowEntitySetting> entitySettings =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(applicationName);
        if (entitySettings.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Application name must exist exactly once; found: " + entitySettings.size()
            );
        }
        WorkflowEntitySetting entitySetting = entitySettings.get(0);
        Pageable revisionPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );
        return workflowEntitySettingRepository.findRevisions(entitySetting.getId(), revisionPageable);
    }
}
