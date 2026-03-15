package com.workflow.controller;

import com.querydsl.core.types.Predicate;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.history.Revision;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEntitySettingControllerTest {

    @Mock
    private WorkflowEntitySettingRepository workflowEntitySettingRepository;

    private WorkflowEntitySettingController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkflowEntitySettingController(workflowEntitySettingRepository);
    }

    @Test
    void searchWorkflowEntitySettingShouldUseQuerydslPredicate() {
        Predicate predicate = mock(Predicate.class);
        Pageable pageable = PageRequest.of(0, 10);
        Page<WorkflowEntitySetting> expected = new PageImpl<>(List.of());
        when(workflowEntitySettingRepository.findAll(predicate, pageable)).thenReturn(expected);

        Page<WorkflowEntitySetting> result = controller.searchWorkflowEntitySetting(predicate, pageable);

        assertSame(expected, result);
    }

    @Test
    void getWorkflowEntitySettingHistoryShouldReturnRevisionPage() {
        WorkflowEntitySetting entity = WorkflowEntitySetting.builder().id(10L).applicationName("APP_A").build();
        Pageable inputPageable = PageRequest.of(1, 20, Sort.by("applicationName").ascending());
        @SuppressWarnings("unchecked")
        Page<Revision<Integer, WorkflowEntitySetting>> expected = (Page<Revision<Integer, WorkflowEntitySetting>>) mock(Page.class);

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("APP_A")).thenReturn(List.of(entity));
        when(workflowEntitySettingRepository.findRevisions(eq(10L), any(Pageable.class))).thenReturn(expected);

        Page<Revision<Integer, WorkflowEntitySetting>> result =
                controller.getWorkflowEntitySettingHistory("APP_A", inputPageable);

        assertSame(expected, result);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(workflowEntitySettingRepository).findRevisions(eq(10L), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.by("applicationName").ascending(), captor.getValue().getSort());
    }

    @Test
    void getWorkflowEntitySettingHistoryShouldThrowBadRequestWhenApplicationNameNotUnique() {
        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("APP_A"))
                .thenReturn(List.of(
                        WorkflowEntitySetting.builder().id(1L).applicationName("APP_A").build(),
                        WorkflowEntitySetting.builder().id(2L).applicationName("APP_A").build()
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getWorkflowEntitySettingHistory("APP_A", PageRequest.of(0, 10))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
