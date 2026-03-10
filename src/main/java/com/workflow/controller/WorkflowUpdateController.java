package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.*;
import com.workflow.common.util.Base64Util;
import io.swagger.v3.oas.annotations.Parameter;
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

    private final WorkflowEntitySettingRepository workflowEntitySettingRepository;
    private final WorkflowEntityAndLinkingIdMappingRepository workflowEntityAndLinkingIdMappingRepository;
    private final WorkflowRuleAndTypeRepository workflowRuleAndTypeRepository;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final WorkflowTypeRepository workflowTypeRepository;
    private final WorkflowGetController workflowGetController;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/workflow", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkFlow updateWorkFlow(
            @RequestParam(required = true) @Parameter(example = "UK_DRFI", required = true,
                    description = "Application identifier for the workflow") @NotNull String applicationName,
            @RequestBody(required = false) @Valid WorkFlow workFlow) {

        List<WorkflowEntitySetting> entitySettingList =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(applicationName);

        WorkflowEntitySetting entitySetting;
        if (entitySettingList.isEmpty()) {
            if (workFlow == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Workflow body is required when creating a new workflow for application: " + applicationName);
            }
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
        boolean duplicateKeyExceptionGoingOn = true;
        while (duplicateKeyExceptionGoingOn) {
            try {
                deleteAndAddWorkFlow(workFlow, entitySetting);
                duplicateKeyExceptionGoingOn = false;
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate key value violates unique constraint: {}", e.getCause());
            }
        }
        return workflowGetController.getWorkFlow(applicationName);
    }

    @Transactional
    protected void deleteAndAddWorkFlow(WorkFlow workFlow, WorkflowEntitySetting entitySetting) {
        try {
            entitySetting.setWorkflow(Base64Util.base64Encode(objectMapper.writeValueAsString(workFlow), true, objectMapper));
        } catch (Exception e) {
            entitySetting.setWorkflow(null);
        }
        workflowEntitySettingRepository.saveAndFlush(entitySetting);

        List<WorkflowEntityAndLinkingIdMapping> linkingIdMappingList =
                workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(entitySetting.getId());

        Set<WorkflowRule> deleteRuleSet = new HashSet<>();
        Set<WorkflowType> deleteTypeSet = new HashSet<>();
        Set<WorkflowRuleAndType> deleteRuleAndTypeSet = new HashSet<>();

        log.info("Begin delete action and rule information");
        for (WorkflowEntityAndLinkingIdMapping mapping : linkingIdMappingList) {
            String linkingId = mapping.getWorkflowRuleAndTypeMapping() != null ? mapping.getWorkflowRuleAndTypeMapping().getLinkingId() : null;
            if (linkingId == null) continue;
            List<WorkflowRuleAndType> ruleAndTypeList = workflowRuleAndTypeRepository.getAllByLinkingId(linkingId);
            for (WorkflowRuleAndType rt : ruleAndTypeList) {
                deleteRuleSet.add(rt.getWorkflowRule());
                deleteTypeSet.add(rt.getWorkflowType());
            }
            deleteRuleAndTypeSet.addAll(ruleAndTypeList);
        }

        List<Long> deleteMappingIds = linkingIdMappingList.stream().map(WorkflowEntityAndLinkingIdMapping::getId).toList();
        log.info("Going to delete entity and linking relationship, ID list: {}", deleteMappingIds);
        workflowEntityAndLinkingIdMappingRepository.deleteAllByIdInBatch(deleteMappingIds);

        List<Long> deleteRuleAndTypeIds = deleteRuleAndTypeSet.stream().map(WorkflowRuleAndType::getId).toList();
        log.info("Going to delete rule and action relationship, ID list: {}", deleteRuleAndTypeIds);
        workflowRuleAndTypeRepository.deleteAllByIdInBatch(deleteRuleAndTypeIds);

        List<Long> deleteRuleIds = deleteRuleSet.stream().map(WorkflowRule::getId).toList();
        log.info("Going to delete rule, ID list: {}", deleteRuleIds);
        workflowRuleRepository.deleteAllByIdInBatch(deleteRuleIds);

        List<Long> deleteTypeIds = deleteTypeSet.stream().map(WorkflowType::getId).toList();
        log.info("Going to delete action, ID list: {}", deleteTypeIds);
        workflowTypeRepository.deleteAllByIdInBatch(deleteTypeIds);
        log.info("End delete action and rule information");

        List<WorkflowRuleAndType> savedRuleAndTypeList = new ArrayList<>();
        List<WorkflowEntityAndLinkingIdMapping> savedLinkingIdMappingList = new ArrayList<>();

        for (Plugin plugin : workFlow.getPluginList()) {
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

                WorkflowType type = plugin.getAction();
                WorkflowType copyType = WorkflowType.builder().build();
                org.springframework.beans.BeanUtils.copyProperties(type, copyType);
                copyType.setId(null);
                copyType.setElseLogic(Base64Util.base64Encode(copyType.getElseLogic(), true, objectMapper));
                copyType.setHttpRequestUrlWithQueryParameter(Base64Util.base64Encode(copyType.getHttpRequestUrlWithQueryParameter(), false, objectMapper));
                copyType.setInternalHttpRequestUrlWithQueryParameter(Base64Util.base64Encode(copyType.getInternalHttpRequestUrlWithQueryParameter(), false, objectMapper));
                copyType.setHttpRequestHeaders(Base64Util.base64Encode(copyType.getHttpRequestHeaders(), true, objectMapper));
                copyType.setHttpRequestBody(Base64Util.base64Encode(copyType.getHttpRequestBody(), true, objectMapper));
                copyType.setTrackingNumberSchemaInHttpResponse(Base64Util.base64Encode(copyType.getTrackingNumberSchemaInHttpResponse(), true, objectMapper));

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
                WorkflowRuleAndType ruleAndTypeToLink = savedRuleAndTypeList.get(savedRuleAndTypeList.size() - 1);
                savedLinkingIdMappingList.add(WorkflowEntityAndLinkingIdMapping.builder()
                        .logicOrder(plugin.getId())
                        .workflowEntitySetting(entitySetting)
                        .workflowRuleAndTypeMapping(ruleAndTypeToLink)
                        .remark(plugin.getDescription())
                        .build());
                log.info("Save entity and linking: {} {}", entitySetting.getApplicationName(), linkingId);
            } else {
                WorkflowRule emptyRule = workflowRuleRepository.saveAndFlush(WorkflowRule.builder().key("").build());
                WorkflowType savedType = workflowTypeRepository.saveAndFlush(
                        plugin.getAction() != null ? plugin.getAction() : WorkflowType.builder().build());
                String linkingId = entitySetting.getId() + "_" + savedType.getId() + "_" + plugin.getId();
                savedRuleAndTypeList.add(WorkflowRuleAndType.builder()
                        .workflowRule(emptyRule)
                        .workflowType(savedType)
                        .linkingId(linkingId)
                        .build());
                WorkflowRuleAndType ruleAndTypeToLink = savedRuleAndTypeList.get(savedRuleAndTypeList.size() - 1);
                savedLinkingIdMappingList.add(WorkflowEntityAndLinkingIdMapping.builder()
                        .logicOrder(plugin.getId())
                        .workflowEntitySetting(entitySetting)
                        .workflowRuleAndTypeMapping(ruleAndTypeToLink)
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
}
