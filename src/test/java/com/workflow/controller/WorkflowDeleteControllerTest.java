package com.workflow.controller;

import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMapping;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMappingRepository;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import com.workflow.dao.repository.WorkflowReport;
import com.workflow.dao.repository.WorkflowReportRepository;
import com.workflow.dao.repository.WorkflowRule;
import com.workflow.dao.repository.WorkflowRuleAndType;
import com.workflow.dao.repository.WorkflowRuleAndTypeRepository;
import com.workflow.dao.repository.WorkflowRuleRepository;
import com.workflow.dao.repository.WorkflowType;
import com.workflow.dao.repository.WorkflowTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDeleteControllerTest {

    @Mock
    private WorkflowEntitySettingRepository workflowEntitySettingRepository;
    @Mock
    private WorkflowEntityAndLinkingIdMappingRepository workflowEntityAndLinkingIdMappingRepository;
    @Mock
    private WorkflowReportRepository workflowReportRepository;
    @Mock
    private WorkflowRuleAndTypeRepository workflowRuleAndTypeRepository;
    @Mock
    private WorkflowRuleRepository workflowRuleRepository;
    @Mock
    private WorkflowTypeRepository workflowTypeRepository;

    private WorkflowDeleteController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkflowDeleteController(
                workflowEntitySettingRepository,
                workflowEntityAndLinkingIdMappingRepository,
                workflowReportRepository,
                workflowRuleAndTypeRepository,
                workflowRuleRepository,
                workflowTypeRepository
        );
    }

    @Test
    void deleteWorkFlowShouldThrowBadRequestWhenApplicationDoesNotExistExactlyOnce() {
        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.deleteWorkFlow("app")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void deleteWorkFlowShouldThrowConflictWhenReportExists() {
        WorkflowEntitySetting setting = WorkflowEntitySetting.builder().id(10L).applicationName("app").build();
        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of(setting));
        when(workflowReportRepository.findByWorkflowEntitySetting_Id(10L)).thenReturn(List.of(WorkflowReport.builder().id(1L).build()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.deleteWorkFlow("app")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void deleteWorkFlowShouldDeleteMappingsRulesTypesAndEntityWhenNoReports() {
        WorkflowEntitySetting setting = WorkflowEntitySetting.builder().id(11L).applicationName("app").build();
        WorkflowRuleAndType link1 = WorkflowRuleAndType.builder().linkingId("L1").build();
        WorkflowRuleAndType link2 = WorkflowRuleAndType.builder().linkingId("L2").build();
        WorkflowEntityAndLinkingIdMapping mapping1 = WorkflowEntityAndLinkingIdMapping.builder()
                .id(101L).workflowRuleAndTypeMapping(link1).build();
        WorkflowEntityAndLinkingIdMapping mapping2 = WorkflowEntityAndLinkingIdMapping.builder()
                .id(102L).workflowRuleAndTypeMapping(link2).build();

        WorkflowRule rule1 = WorkflowRule.builder().id(201L).build();
        WorkflowRule rule2 = WorkflowRule.builder().id(202L).build();
        WorkflowType type1 = WorkflowType.builder().id(301L).build();
        WorkflowType type2 = WorkflowType.builder().id(302L).build();

        WorkflowRuleAndType rt1 = WorkflowRuleAndType.builder().id(401L).linkingId("L1").workflowRule(rule1).workflowType(type1).build();
        WorkflowRuleAndType rt2 = WorkflowRuleAndType.builder().id(402L).linkingId("L1").workflowRule(rule2).workflowType(type2).build();
        WorkflowRuleAndType rt3 = WorkflowRuleAndType.builder().id(403L).linkingId("L2").workflowRule(rule1).workflowType(type1).build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of(setting));
        when(workflowReportRepository.findByWorkflowEntitySetting_Id(11L)).thenReturn(List.of());
        when(workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(11L)).thenReturn(List.of(mapping1, mapping2));
        when(workflowRuleAndTypeRepository.getAllByLinkingId("L1")).thenReturn(List.of(rt1, rt2));
        when(workflowRuleAndTypeRepository.getAllByLinkingId("L2")).thenReturn(List.of(rt3));

        controller.deleteWorkFlow("app");

        ArgumentCaptor<List<Long>> mappingIds = ArgumentCaptor.forClass(List.class);
        verify(workflowEntityAndLinkingIdMappingRepository).deleteAllByIdInBatch(mappingIds.capture());
        assertEquals(List.of(101L, 102L), mappingIds.getValue());

        verify(workflowEntitySettingRepository).deleteById(11L);

        ArgumentCaptor<List<Long>> relationIds = ArgumentCaptor.forClass(List.class);
        verify(workflowRuleAndTypeRepository).deleteAllByIdInBatch(relationIds.capture());
        assertEquals(Set.of(401L), Set.copyOf(relationIds.getValue()));

        ArgumentCaptor<List<Long>> ruleIds = ArgumentCaptor.forClass(List.class);
        verify(workflowRuleRepository).deleteAllByIdInBatch(ruleIds.capture());
        assertEquals(Set.of(201L), Set.copyOf(ruleIds.getValue()));

        ArgumentCaptor<List<Long>> typeIds = ArgumentCaptor.forClass(List.class);
        verify(workflowTypeRepository).deleteAllByIdInBatch(typeIds.capture());
        assertEquals(Set.of(301L), Set.copyOf(typeIds.getValue()));
    }

    @Test
    void deleteWorkFlowShouldSkipRuleTypeLookupWhenMappingRelationMissing() {
        WorkflowEntitySetting setting = WorkflowEntitySetting.builder().id(12L).applicationName("app").build();
        WorkflowEntityAndLinkingIdMapping mapping = WorkflowEntityAndLinkingIdMapping.builder()
                .id(103L)
                .workflowRuleAndTypeMapping(null)
                .build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of(setting));
        when(workflowReportRepository.findByWorkflowEntitySetting_Id(12L)).thenReturn(List.of());
        when(workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(12L)).thenReturn(List.of(mapping));

        controller.deleteWorkFlow("app");

        verify(workflowRuleAndTypeRepository, never()).getAllByLinkingId(org.mockito.ArgumentMatchers.anyString());
        verify(workflowRuleAndTypeRepository).deleteAllByIdInBatch(List.of());
        verify(workflowRuleRepository).deleteAllByIdInBatch(List.of());
        verify(workflowTypeRepository).deleteAllByIdInBatch(List.of());
    }
}
