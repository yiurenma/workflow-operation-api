package com.workflow.controller;

import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMapping;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMappingRepository;
import com.workflow.dao.repository.WorkflowReportRepository;
import com.workflow.dao.repository.WorkflowRule;
import com.workflow.dao.repository.WorkflowRuleAndType;
import com.workflow.dao.repository.WorkflowRuleAndTypeRepository;
import com.workflow.dao.repository.WorkflowRuleRepository;
import com.workflow.dao.repository.WorkflowType;
import com.workflow.dao.repository.WorkflowTypeRepository;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final WorkflowRuleAndTypeRepository workflowRuleAndTypeRepository;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final WorkflowTypeRepository workflowTypeRepository;

    @Transactional
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

        WorkflowEntitySetting entitySetting = entitySettingList.get(0);
        Long entitySettingId = entitySetting.getId();
        if (!workflowReportRepository.findByWorkflowEntitySetting_Id(entitySettingId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete workflow: reports exist for this application");
        }

        List<WorkflowEntityAndLinkingIdMapping> linkingIdMappingList =
                workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(entitySettingId);

        Set<WorkflowRule> deleteRuleSet = new HashSet<>();
        Set<WorkflowType> deleteTypeSet = new HashSet<>();
        Set<WorkflowRuleAndType> deleteRuleAndTypeSet = new HashSet<>();

        for (WorkflowEntityAndLinkingIdMapping mapping : linkingIdMappingList) {
            List<WorkflowRuleAndType> ruleAndTypeList =
                    workflowRuleAndTypeRepository.getAllByLinkingId(mapping.getWorkflowRuleAndTypeLinkingId());
            for (WorkflowRuleAndType rt : ruleAndTypeList) {
                deleteRuleSet.add(rt.getWorkflowRule());
                deleteTypeSet.add(rt.getWorkflowType());
            }
            deleteRuleAndTypeSet.addAll(ruleAndTypeList);
        }

        List<Long> mappingIds = linkingIdMappingList.stream().map(WorkflowEntityAndLinkingIdMapping::getId).toList();
        log.info("Going to delete entity and linking relationship, ID list: {}", mappingIds);
        workflowEntityAndLinkingIdMappingRepository.deleteAllByIdInBatch(mappingIds);

        List<Long> ruleAndTypeIds = deleteRuleAndTypeSet.stream().map(WorkflowRuleAndType::getId).toList();
        log.info("Going to delete rule and action relationship, ID list: {}", ruleAndTypeIds);
        workflowRuleAndTypeRepository.deleteAllByIdInBatch(ruleAndTypeIds);

        List<Long> ruleIds = deleteRuleSet.stream().map(WorkflowRule::getId).toList();
        log.info("Going to delete rule, ID list: {}", ruleIds);
        workflowRuleRepository.deleteAllByIdInBatch(ruleIds);

        log.info("Going to delete entity: {} {}", applicationName, entitySettingId);
        workflowEntitySettingRepository.deleteById(entitySettingId);

        List<Long> typeIds = deleteTypeSet.stream().map(WorkflowType::getId).toList();
        log.info("Going to delete action, ID list: {}", typeIds);
        workflowTypeRepository.deleteAllByIdInBatch(typeIds);

        log.info("Workflow and entity removed for application: {} (report/record kept)", applicationName);
    }
}
