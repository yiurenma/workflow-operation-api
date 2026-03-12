package com.workflow.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMapping;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMappingRepository;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowUpdateControllerTest {

    @Mock
    private WorkflowEntitySettingRepository workflowEntitySettingRepository;
    @Mock
    private WorkflowEntityAndLinkingIdMappingRepository workflowEntityAndLinkingIdMappingRepository;
    @Mock
    private WorkflowRuleAndTypeRepository workflowRuleAndTypeRepository;
    @Mock
    private WorkflowRuleRepository workflowRuleRepository;
    @Mock
    private WorkflowTypeRepository workflowTypeRepository;
    @Mock
    private WorkflowGetController workflowGetController;
    @Mock
    private WorkflowDeleteController workflowDeleteController;

    private ObjectMapper objectMapper;
    private WorkflowUpdateController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new WorkflowUpdateController(
                workflowEntitySettingRepository,
                workflowEntityAndLinkingIdMappingRepository,
                workflowRuleAndTypeRepository,
                workflowRuleRepository,
                workflowTypeRepository,
                workflowGetController,
                workflowDeleteController,
                objectMapper
        );
    }

    @Test
    void updateWorkFlowShouldThrowBadRequestWhenCreatingWithoutBody() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateWorkFlow("app", null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void updateWorkFlowShouldCreateEntityWhenApplicationMissingAndBodyProvided() {
        WorkFlow input = WorkFlow.builder().pluginList(List.of()).build();
        WorkFlow expected = WorkFlow.builder().pluginList(List.of()).uiMapList(List.of("ok")).build();
        WorkflowUpdateController spyController = spy(controller);

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of());
        doNothing().when(spyController).deleteAndAddWorkFlow(eq(input), any(WorkflowEntitySetting.class));
        when(workflowGetController.getWorkFlow("app")).thenReturn(expected);

        WorkFlow result = spyController.updateWorkFlow("app", input);

        assertEquals(expected, result);
        ArgumentCaptor<WorkflowEntitySetting> captor = ArgumentCaptor.forClass(WorkflowEntitySetting.class);
        verify(spyController).deleteAndAddWorkFlow(eq(input), captor.capture());
        assertEquals("app", captor.getValue().getApplicationName());
    }

    @Test
    void updateWorkFlowShouldThrowBadRequestWhenApplicationExistsMoreThanOnce() {
        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of(
                WorkflowEntitySetting.builder().id(1L).applicationName("app").build(),
                WorkflowEntitySetting.builder().id(2L).applicationName("app").build()
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateWorkFlow("app", WorkFlow.builder().pluginList(List.of()).build())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void updateWorkFlowShouldRetryWhenDuplicateKeyHappens() {
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder().id(1L).applicationName("app").build();
        WorkFlow input = WorkFlow.builder().pluginList(List.of()).build();
        WorkFlow expected = WorkFlow.builder().pluginList(List.of()).uiMapList(List.of("ok")).build();

        WorkflowUpdateController spyController = spy(controller);
        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of(entitySetting));
        doThrow(new DataIntegrityViolationException("dup"))
                .doNothing()
                .when(spyController).deleteAndAddWorkFlow(eq(input), eq(entitySetting));
        when(workflowGetController.getWorkFlow("app")).thenReturn(expected);

        WorkFlow result = spyController.updateWorkFlow("app", input);

        assertEquals(expected, result);
        verify(spyController, times(2)).deleteAndAddWorkFlow(input, entitySetting);
    }

    @Test
    void updateWorkFlowShouldThrowConflictWhenDuplicateKeyKeepsFailing() {
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder().id(1L).applicationName("app").build();
        WorkFlow input = WorkFlow.builder().pluginList(List.of()).build();

        WorkflowUpdateController spyController = spy(controller);
        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of(entitySetting));
        doThrow(new DataIntegrityViolationException("dup-1"))
                .doThrow(new DataIntegrityViolationException("dup-2"))
                .doThrow(new DataIntegrityViolationException("dup-3"))
                .when(spyController).deleteAndAddWorkFlow(eq(input), eq(entitySetting));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> spyController.updateWorkFlow("app", input)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(spyController, times(3)).deleteAndAddWorkFlow(input, entitySetting);
    }

    @Test
    void deleteAndAddWorkFlowShouldDeleteOldDataAndSaveNewMappings() {
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder().id(10L).applicationName("app").build();

        AtomicLong ruleId = new AtomicLong(1000L);
        when(workflowRuleRepository.saveAndFlush(any(WorkflowRule.class))).thenAnswer(invocation -> {
            WorkflowRule in = invocation.getArgument(0);
            in.setId(ruleId.incrementAndGet());
            return in;
        });
        AtomicLong typeId = new AtomicLong(2000L);
        when(workflowTypeRepository.saveAndFlush(any(WorkflowType.class))).thenAnswer(invocation -> {
            WorkflowType in = invocation.getArgument(0);
            in.setId(typeId.incrementAndGet());
            return in;
        });

        when(workflowRuleAndTypeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowEntityAndLinkingIdMappingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Plugin pluginWithRules = Plugin.builder()
                .id(1)
                .description("first")
                .ruleList(List.of(WorkflowRule.builder().key("$.a").remark("r1").build()))
                .action(WorkflowType.builder()
                        .type("TYPE_A")
                        .elseLogic("{\"x\":1}")
                        .httpRequestUrlWithQueryParameter("https://external")
                        .internalHttpRequestUrlWithQueryParameter("https://internal")
                        .httpRequestHeaders("{\"h\":1}")
                        .httpRequestBody("{\"b\":1}")
                        .trackingNumberSchemaInHttpResponse("{\"t\":1}")
                        .build())
                .build();
        Plugin pluginWithoutRules = Plugin.builder()
                .id(2)
                .description("second")
                .ruleList(List.of())
                .action(null)
                .build();
        Plugin pluginWithoutRulesButWithAction = Plugin.builder()
                .id(3)
                .description("third")
                .ruleList(null)
                .action(WorkflowType.builder().type("TYPE_C").build())
                .build();
        WorkFlow workFlow = WorkFlow.builder()
                .pluginList(List.of(pluginWithRules, pluginWithoutRules, pluginWithoutRulesButWithAction))
                .build();

        controller.deleteAndAddWorkFlow(workFlow, entitySetting);

        assertNotNull(entitySetting.getWorkflow());

        verify(workflowEntitySettingRepository).saveAndFlush(entitySetting);
        verify(workflowDeleteController).deleteWorkflowRulesMappingsAndTypes(entitySetting);
        verify(workflowRuleAndTypeRepository).flush();
        verify(workflowEntityAndLinkingIdMappingRepository).flush();

        ArgumentCaptor<List<WorkflowRuleAndType>> rtCaptor = ArgumentCaptor.forClass(List.class);
        verify(workflowRuleAndTypeRepository).saveAll(rtCaptor.capture());
        assertEquals(3, rtCaptor.getValue().size());
        Set<String> linkingIds = rtCaptor.getValue().stream().map(WorkflowRuleAndType::getLinkingId).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("10_2001_1", "10_2002_2", "10_2003_3"), linkingIds);

        ArgumentCaptor<List<WorkflowEntityAndLinkingIdMapping>> mappingCaptor = ArgumentCaptor.forClass(List.class);
        verify(workflowEntityAndLinkingIdMappingRepository).saveAll(mappingCaptor.capture());
        assertEquals(3, mappingCaptor.getValue().size());
        assertEquals(Set.of("10_2001_1", "10_2002_2", "10_2003_3"),
                mappingCaptor.getValue().stream()
                        .map(WorkflowEntityAndLinkingIdMapping::getLinkingId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void deleteAndAddWorkFlowShouldSetWorkflowNullWhenSerializeFails() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        WorkflowUpdateController failingController = new WorkflowUpdateController(
                workflowEntitySettingRepository,
                workflowEntityAndLinkingIdMappingRepository,
                workflowRuleAndTypeRepository,
                workflowRuleRepository,
                workflowTypeRepository,
                workflowGetController,
                workflowDeleteController,
                failingMapper
        );

        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder().id(20L).applicationName("app").build();
        WorkFlow workFlow = WorkFlow.builder().pluginList(List.of()).build();

        when(workflowRuleAndTypeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowEntityAndLinkingIdMappingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(workflowRuleAndTypeRepository).flush();
        doNothing().when(workflowEntityAndLinkingIdMappingRepository).flush();

        failingController.deleteAndAddWorkFlow(workFlow, entitySetting);

        assertNull(entitySetting.getWorkflow());
        verify(workflowEntitySettingRepository).saveAndFlush(entitySetting);
    }

    @Test
    void deleteAndAddWorkFlowShouldDelegateDeleteToDeleteController() {
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder().id(30L).applicationName("app").build();
        WorkFlow workFlow = WorkFlow.builder().pluginList(List.of()).build();

        when(workflowRuleAndTypeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowEntityAndLinkingIdMappingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        controller.deleteAndAddWorkFlow(workFlow, entitySetting);

        verify(workflowDeleteController).deleteWorkflowRulesMappingsAndTypes(entitySetting);
    }
}
