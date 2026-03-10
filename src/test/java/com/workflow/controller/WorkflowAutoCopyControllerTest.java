package com.workflow.controller;

import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import com.workflow.dao.repository.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowAutoCopyControllerTest {

    @Mock
    private WorkflowEntitySettingRepository workflowEntitySettingRepository;
    @Mock
    private WorkflowGetController workflowGetController;
    @Mock
    private WorkflowUpdateController workflowUpdateController;

    private WorkflowAutoCopyController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkflowAutoCopyController(
                workflowEntitySettingRepository,
                workflowGetController,
                workflowUpdateController
        );
    }

    @Test
    void autoCopyWorkFlowShouldThrowBadRequestWhenSourceDoesNotExistExactlyOnce() {
        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("from")).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.autoCopyWorkFlow("application/json", "from", "to")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void autoCopyWorkFlowShouldCopyEntityAndDelegateToUpdateController() {
        WorkflowType trackingAction = WorkflowType.builder().id(77L).type("TRACK").build();
        WorkflowEntitySetting original = WorkflowEntitySetting.builder()
                .id(1L)
                .applicationName("from")
                .trackingServiceProviderActionId(trackingAction)
                .build();

        WorkFlow source = WorkFlow.builder().pluginList(List.of()).uiMapList(List.of()).build();
        WorkFlow expected = WorkFlow.builder().pluginList(List.of()).uiMapList(List.of("ok")).build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("from")).thenReturn(List.of(original));
        when(workflowEntitySettingRepository.save(any(WorkflowEntitySetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowGetController.getWorkFlow("from")).thenReturn(source);
        when(workflowUpdateController.updateWorkFlow("to", source)).thenReturn(expected);

        WorkFlow result = controller.autoCopyWorkFlow("application/json", "from", "to");

        assertEquals(expected, result);

        ArgumentCaptor<WorkflowEntitySetting> captor = ArgumentCaptor.forClass(WorkflowEntitySetting.class);
        verify(workflowEntitySettingRepository).save(captor.capture());
        WorkflowEntitySetting saved = captor.getValue();

        assertEquals(null, saved.getId());
        assertEquals("to", saved.getApplicationName());
        assertEquals(trackingAction, saved.getTrackingServiceProviderActionId());
    }
}
