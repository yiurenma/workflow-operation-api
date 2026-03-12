package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.*;
import com.workflow.common.util.Base64Util;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "Workflow Update API", description = "Create or update workflow (delete then create)")
@Validated
@RequiredArgsConstructor
public class WorkflowUpdateController {

    private static final int MAX_RETRY_ON_DUPLICATE_KEY = 3;

    private final WorkflowEntitySettingRepository workflowEntitySettingRepository;
    private final WorkflowEntityAndLinkingIdMappingRepository workflowEntityAndLinkingIdMappingRepository;
    private final WorkflowRuleAndTypeRepository workflowRuleAndTypeRepository;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final WorkflowTypeRepository workflowTypeRepository;
    private final WorkflowGetController workflowGetController;
    private final WorkflowDeleteController workflowDeleteController;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "Create or update workflow",
            description = "Upserts workflow by application name. Existing workflow internals are cleared and rebuilt."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workflow saved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid application/workflow payload"),
            @ApiResponse(responseCode = "409", description = "Workflow update failed after duplicate key retries")
    })
    @Transactional
    @PostMapping(value = "/workflow", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkFlow updateWorkFlow(
            @RequestParam(required = true) @Parameter(example = "UK_DRFI", required = true,
                    description = "Application identifier for the workflow") @NotNull String applicationName,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Workflow definition with ordered plugin list and optional uiMapList",
                    content = @Content(schema = @Schema(implementation = WorkFlow.class))
            )
            @RequestBody(required = false) @Valid WorkFlow workFlow) {
        if (workFlow == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Workflow body is required for update operation");
        }

        List<WorkflowEntitySetting> entitySettingList =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(applicationName);

        WorkflowEntitySetting entitySetting;
        if (entitySettingList.isEmpty()) {
            entitySetting = WorkflowEntitySetting.builder()
                    .applicationName(applicationName)
                    .enabled(true)
                    .build();
        } else if (entitySettingList.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Application name must exist exactly once; found: " + entitySettingList.size());
        } else {
            entitySetting = entitySettingList.get(0);
        }
        for (int attempt = 1; attempt <= MAX_RETRY_ON_DUPLICATE_KEY; attempt++) {
            try {
                deleteAndAddWorkFlow(workFlow, entitySetting);
                return workflowGetController.getWorkFlow(applicationName);
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate key during workflow update (attempt {}/{}): {}",
                        attempt, MAX_RETRY_ON_DUPLICATE_KEY, e.getMessage());
                if (attempt == MAX_RETRY_ON_DUPLICATE_KEY) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Failed to update workflow after " + MAX_RETRY_ON_DUPLICATE_KEY + " retries due to duplicate key conflicts",
                            e
                    );
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Failed to update workflow");
    }

    @Transactional
    protected void deleteAndAddWorkFlow(WorkFlow workFlow, WorkflowEntitySetting entitySetting) {
        try {
            entitySetting.setWorkflow(Base64Util.base64Encode(objectMapper.writeValueAsString(workFlow), true, objectMapper));
        } catch (Exception e) {
            entitySetting.setWorkflow(null);
        }
        workflowEntitySettingRepository.saveAndFlush(entitySetting);

        workflowDeleteController.deleteWorkflowRulesMappingsAndTypes(entitySetting);

        List<WorkflowRuleAndType> savedRuleAndTypeList = new ArrayList<>();
        List<WorkflowEntityAndLinkingIdMapping> savedLinkingIdMappingList = new ArrayList<>();

        List<Plugin> pluginList = workFlow.getPluginList() != null ? workFlow.getPluginList() : List.of();
        for (Plugin plugin : pluginList) {
            log.info("Plugin order: {}", plugin.getId());
            if (plugin.getRuleList() != null && !plugin.getRuleList().isEmpty()) {
                List<WorkflowRule> savedRuleList = new ArrayList<>();
                for (WorkflowRule rule : plugin.getRuleList()) {
                    WorkflowRule copyRule = WorkflowRule.builder().build();
                    org.springframework.beans.BeanUtils.copyProperties(rule, copyRule);
                    copyRule.setId(null);
                    WorkflowRule savedRule = workflowRuleRepository.saveAndFlush(copyRule);
                    log.info("Store rule success, rule id: {}", savedRule.getId());
                    savedRuleList.add(savedRule);
                }

                WorkflowType copyType = encodeWorkflowTypeForPersistence(plugin.getAction());

                WorkflowType savedType = workflowTypeRepository.saveAndFlush(copyType);
                log.info("Store action success, action id: {}", savedType.getId());

                String linkingId = entitySetting.getId() + "_" + savedType.getId() + "_" + plugin.getId();
                for (WorkflowRule savedRule : savedRuleList) {
                    savedRuleAndTypeList.add(WorkflowRuleAndType.builder()
                            .workflowRule(savedRule)
                            .workflowType(savedType)
                            .linkingId(linkingId)
                            .build());
                    log.info("Save rule and action linking: {} {} {}", savedRule.getId(), savedType.getId(), linkingId);
                }
                savedLinkingIdMappingList.add(WorkflowEntityAndLinkingIdMapping.builder()
                        .logicOrder(plugin.getId())
                        .workflowEntitySetting(entitySetting)
                        .linkingId(linkingId)
                        .remark(plugin.getDescription())
                        .build());
                log.info("Save entity and linking: {} {}", entitySetting.getApplicationName(), linkingId);
            } else {
                WorkflowRule emptyRule = workflowRuleRepository.saveAndFlush(WorkflowRule.builder().key("").build());
                WorkflowType savedType = workflowTypeRepository.saveAndFlush(encodeWorkflowTypeForPersistence(plugin.getAction()));
                String linkingId = entitySetting.getId() + "_" + savedType.getId() + "_" + plugin.getId();
                savedRuleAndTypeList.add(WorkflowRuleAndType.builder()
                        .workflowRule(emptyRule)
                        .workflowType(savedType)
                        .linkingId(linkingId)
                        .build());
                savedLinkingIdMappingList.add(WorkflowEntityAndLinkingIdMapping.builder()
                        .logicOrder(plugin.getId())
                        .workflowEntitySetting(entitySetting)
                        .linkingId(linkingId)
                        .remark(plugin.getDescription())
                        .build());
            }
        }

        log.info("Going to store rule and action mapping: {}", savedRuleAndTypeList);
        workflowRuleAndTypeRepository.saveAll(savedRuleAndTypeList);
        workflowRuleAndTypeRepository.flush();

        log.info("Going to store entity and linking mapping: {}", savedLinkingIdMappingList);
        workflowEntityAndLinkingIdMappingRepository.saveAll(savedLinkingIdMappingList);
        workflowEntityAndLinkingIdMappingRepository.flush();
    }

    private WorkflowType encodeWorkflowTypeForPersistence(WorkflowType source) {
        WorkflowType copyType = WorkflowType.builder().build();
        if (source != null) {
            org.springframework.beans.BeanUtils.copyProperties(source, copyType);
        }
        copyType.setId(null);
        copyType.setElseLogic(Base64Util.base64Encode(copyType.getElseLogic(), true, objectMapper));
        copyType.setHttpRequestUrlWithQueryParameter(Base64Util.base64Encode(copyType.getHttpRequestUrlWithQueryParameter(), false, objectMapper));
        copyType.setInternalHttpRequestUrlWithQueryParameter(Base64Util.base64Encode(copyType.getInternalHttpRequestUrlWithQueryParameter(), false, objectMapper));
        copyType.setHttpRequestHeaders(Base64Util.base64Encode(copyType.getHttpRequestHeaders(), true, objectMapper));
        copyType.setHttpRequestBody(Base64Util.base64Encode(copyType.getHttpRequestBody(), true, objectMapper));
        copyType.setTrackingNumberSchemaInHttpResponse(Base64Util.base64Encode(copyType.getTrackingNumberSchemaInHttpResponse(), true, objectMapper));
        return copyType;
    }
}
