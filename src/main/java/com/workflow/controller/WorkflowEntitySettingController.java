package com.workflow.controller;

import com.querydsl.core.types.Predicate;
import com.workflow.common.exception.ApiBusinessException;
import com.workflow.common.exception.ApiErrorCatalog;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.history.Revision;
import org.springframework.data.querydsl.binding.QuerydslPredicate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/api/")
@Tag(
        name = "Entity Setting Query API",
        description = "Business-facing entity setting APIs: QueryDSL fuzzy search and Envers revision history."
)
@Validated
@RequiredArgsConstructor
public class WorkflowEntitySettingController {

    private final WorkflowEntitySettingRepository workflowEntitySettingRepository;

    @Operation(
            summary = "Query entity settings (QueryDSL)",
            description = """
                    Query entity settings via QueryDSL predicate parameters.
                    Supports fuzzy applicationName search using contains-ignore-case.
                                        
                    Example:
                    - /api/workflow/entity-setting?applicationName=itest&page=0&size=20
                    - /api/workflow/entity-setting?applicationName=APP_2&sort=applicationName,asc
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entity settings loaded")
    })
    @Parameters(value = {
            @Parameter(name = "applicationName", description = "Fuzzy search keyword for applicationName (contains, ignore case)", example = "itest"),
            @Parameter(name = "page", description = "Zero-based page index", example = "0"),
            @Parameter(name = "size", description = "Page size", example = "20"),
            @Parameter(name = "sort", description = "Sort field and direction, format: field,asc|desc", example = "applicationName,asc")
    })
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workflow/entity-setting",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Page<WorkflowEntitySetting> searchWorkflowEntitySetting(
            @QuerydslPredicate(root = WorkflowEntitySetting.class) Predicate predicate,
            @ParameterObject Pageable pageable) {
        return workflowEntitySettingRepository.findAll(predicate, pageable);
    }

    @Operation(
            summary = "Get entity setting revision history",
            description = """
                    Returns Envers revision history for the entity setting identified by exact applicationName.
                    applicationName must match exactly one record.
                                        
                    Example:
                    - /api/workflow/entity-setting/history?applicationName=ITEST_APP&page=0&size=20
                    """
    )
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
            @ParameterObject Pageable pageable) {
        List<WorkflowEntitySetting> entitySettings =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(applicationName);
        if (entitySettings.size() != 1) {
            throw new ApiBusinessException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCatalog.ENTITY_SETTING_HISTORY_APP_NOT_UNIQUE,
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
