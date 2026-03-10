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

        deleteWorkflowRulesMappingsAndTypes(entitySetting);

        log.info("Going to delete entity: {} {}", applicationName, entitySettingId);
        workflowEntitySettingRepository.delete(entitySetting);

        log.info("Workflow and entity removed for application: {} (report/record kept)", applicationName);
    }

    /**
     * Deletes workflow rules, mappings, and types for the given entity setting.
     * Does not delete the entity setting itself. Used by update (clear before re-add) and delete (before removing entity).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRED)
    public void deleteWorkflowRulesMappingsAndTypes(WorkflowEntitySetting entitySetting) {
        List<WorkflowEntityAndLinkingIdMapping> mappings =
                workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(entitySetting.getId());

        for (WorkflowEntityAndLinkingIdMapping m : mappings) {
            if (m.getLinkingId() == null || m.getLinkingId().isBlank()) {
                log.error("Workflow entity and linking mapping has null or blank linkingId. Mapping: id={}, logicOrder={}, remark={}",
                        m.getId(), m.getLogicOrder(), m.getRemark());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Workflow entity and linking mapping (id=" + m.getId() + ") has null or blank linkingId");
            }
        }
        List<String> linkingIds = mappings.stream().map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();

        List<WorkflowRuleAndType> allRuleAndTypes = workflowRuleAndTypeRepository.findAllByLinkingIdIn(linkingIds);

        workflowEntityAndLinkingIdMappingRepository.deleteAll(mappings);
        workflowEntityAndLinkingIdMappingRepository.flush();

        List<Long> ruleAndTypeIds = allRuleAndTypes.stream().map(WorkflowRuleAndType::getId).toList();
        workflowRuleAndTypeRepository.deleteAllByIdInBatch(ruleAndTypeIds);

        List<Long> ruleIds = allRuleAndTypes.stream().map(rt -> rt.getWorkflowRule().getId()).distinct().toList();
        workflowRuleRepository.deleteAllByIdInBatch(ruleIds);

        List<Long> typeIds = allRuleAndTypes.stream().map(rt -> rt.getWorkflowType().getId()).distinct().toList();
        workflowTypeRepository.deleteAllByIdInBatch(typeIds);
        
        entitySetting.setTrackingServiceProviderActionId(null);
        entitySetting.setTrackingServiceProviderActionId2(null);
        workflowEntitySettingRepository.saveAndFlush(entitySetting);
    }
}
