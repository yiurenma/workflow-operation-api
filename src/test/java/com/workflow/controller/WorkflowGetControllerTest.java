package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.util.Base64Util;
import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMapping;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMappingRepository;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import com.workflow.dao.repository.WorkflowRule;
import com.workflow.dao.repository.WorkflowRuleAndType;
import com.workflow.dao.repository.WorkflowRuleAndTypeRepository;
import com.workflow.dao.repository.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowGetControllerTest {

    @Mock
    private WorkflowEntitySettingRepository workflowEntitySettingRepository;
    @Mock
    private WorkflowEntityAndLinkingIdMappingRepository workflowEntityAndLinkingIdMappingRepository;
    @Mock
    private WorkflowRuleAndTypeRepository workflowRuleAndTypeRepository;

    private ObjectMapper objectMapper;
    private WorkflowGetController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new WorkflowGetController(
                workflowEntitySettingRepository,
                workflowEntityAndLinkingIdMappingRepository,
                workflowRuleAndTypeRepository,
                objectMapper
        );
    }

    @Test
    void getWorkFlowShouldThrowBadRequestWhenApplicationDoesNotExistExactlyOnce() {
        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getWorkFlow("app")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getWorkFlowShouldBuildPluginsUsingCurrentWorkflowAndDefaultBranch() throws Exception {
        Map<String, Object> customUiMap = new LinkedHashMap<>();
        customUiMap.put("id", "CUSTOM_1");
        customUiMap.put("type", "CUSTOM");
        customUiMap.put("position", Map.of("x", 9, "y", 9));

        WorkFlow current = WorkFlow.builder()
                .pluginList(List.of(Plugin.builder().id(1).uiMap(customUiMap).build()))
                .uiMapList(List.of(Map.of("keep", true)))
                .build();

        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder()
                .id(1L)
                .applicationName("app")
                .workflow(Base64Util.base64Encode(objectMapper.writeValueAsString(current), true, objectMapper))
                .build();

        WorkflowEntityAndLinkingIdMapping mapping2 = WorkflowEntityAndLinkingIdMapping.builder()
                .id(12L)
                .logicOrder(2)
                .remark("second")
                .linkingId("L2")
                .build();
        WorkflowEntityAndLinkingIdMapping mapping1 = WorkflowEntityAndLinkingIdMapping.builder()
                .id(11L)
                .logicOrder(1)
                .remark("first")
                .linkingId("L1")
                .build();

        WorkflowType encodedType = WorkflowType.builder()
                .id(100L)
                .type("Action")
                .elseLogic(Base64Util.base64Encode("{\"ok\":true}", true, objectMapper))
                .httpRequestUrlWithQueryParameter(Base64Util.base64Encode("https://a", false, objectMapper))
                .internalHttpRequestUrlWithQueryParameter(Base64Util.base64Encode("https://b", false, objectMapper))
                .httpRequestHeaders(Base64Util.base64Encode("{\"h\":1}", true, objectMapper))
                .httpRequestBody(Base64Util.base64Encode("{\"body\":1}", true, objectMapper))
                .trackingNumberSchemaInHttpResponse(Base64Util.base64Encode("{\"track\":1}", true, objectMapper))
                .build();
        WorkflowRuleAndType ruleAndType = WorkflowRuleAndType.builder()
                .id(200L)
                .linkingId("L1")
                .workflowType(encodedType)
                .workflowRule(WorkflowRule.builder().id(300L).key("$.a").build())
                .build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app")).thenReturn(List.of(entitySetting));
        when(workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(1L))
                .thenReturn(new ArrayList<>(List.of(mapping2, mapping1)));
        when(workflowRuleAndTypeRepository.findAllByLinkingIdIn(anyList())).thenReturn(List.of(ruleAndType));

        WorkFlow result = controller.getWorkFlow("app");

        assertNotNull(result);
        assertEquals(2, result.getPluginList().size());
        assertEquals(1, result.getPluginList().get(0).getId());
        assertEquals(customUiMap, result.getPluginList().get(0).getUiMap());
        assertEquals("{\"ok\":true}", result.getPluginList().get(0).getAction().getElseLogic());
        assertEquals("https://a", result.getPluginList().get(0).getAction().getHttpRequestUrlWithQueryParameter());

        assertEquals(2, result.getPluginList().get(1).getId());
        assertNull(result.getPluginList().get(1).getAction());
        assertEquals("Unknown", ((Map<?, ?>) result.getPluginList().get(1).getUiMap()).get("type"));
        assertEquals(List.of(Map.of("keep", true)), result.getUiMapList());
    }

    @Test
    void getWorkFlowShouldGenerateDefaultUiConnectionsWhenStoredWorkflowCannotBeParsed() {
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder()
                .id(2L)
                .applicationName("app2")
                .workflow("%%%invalid-base64%%%")
                .build();

        WorkflowEntityAndLinkingIdMapping mapping1 = WorkflowEntityAndLinkingIdMapping.builder()
                .id(21L)
                .logicOrder(1)
                .remark("first")
                .linkingId("A")
                .build();
        WorkflowEntityAndLinkingIdMapping mapping2 = WorkflowEntityAndLinkingIdMapping.builder()
                .id(22L)
                .logicOrder(2)
                .remark("second")
                .linkingId("B")
                .build();

        WorkflowRuleAndType rtA = WorkflowRuleAndType.builder()
                .id(401L)
                .linkingId("A")
                .workflowRule(WorkflowRule.builder().id(501L).key("$.a").build())
                .workflowType(WorkflowType.builder().id(601L).type("TYPE_A").build())
                .build();
        WorkflowRuleAndType rtB = WorkflowRuleAndType.builder()
                .id(402L)
                .linkingId("B")
                .workflowRule(WorkflowRule.builder().id(502L).key("$.b").build())
                .workflowType(WorkflowType.builder().id(602L).type("TYPE_B").build())
                .build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app2")).thenReturn(List.of(entitySetting));
        when(workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(2L))
                .thenReturn(new ArrayList<>(List.of(mapping1, mapping2)));
        when(workflowRuleAndTypeRepository.findAllByLinkingIdIn(anyList())).thenReturn(List.of(rtA, rtB));

        WorkFlow result = controller.getWorkFlow("app2");

        assertEquals(2, result.getPluginList().size());
        assertEquals(1, result.getUiMapList().size());

        Map<?, ?> edge = (Map<?, ?>) result.getUiMapList().get(0);
        assertEquals("TYPE_A_1", edge.get("source"));
        assertEquals("TYPE_B_2", edge.get("target"));
    }

    @Test
    void getWorkFlowShouldHandleBlankWorkflowAndNullLogicOrder() {
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder()
                .id(3L)
                .applicationName("app3")
                .workflow("")
                .build();

        WorkflowEntityAndLinkingIdMapping mapping = WorkflowEntityAndLinkingIdMapping.builder()
                .id(31L)
                .logicOrder(null)
                .remark("nullable-order")
                .linkingId("NULL_LINK")
                .build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app3")).thenReturn(List.of(entitySetting));
        when(workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(3L))
                .thenReturn(new ArrayList<>(List.of(mapping)));
        when(workflowRuleAndTypeRepository.findAllByLinkingIdIn(anyList())).thenReturn(List.of());

        WorkFlow result = controller.getWorkFlow("app3");

        assertEquals(1, result.getPluginList().size());
        assertEquals(null, result.getPluginList().get(0).getId());
        Map<?, ?> uiMap = (Map<?, ?>) result.getPluginList().get(0).getUiMap();
        Map<?, ?> position = (Map<?, ?>) uiMap.get("position");
        assertEquals(0, position.get("y"));
        assertEquals(List.of(), result.getUiMapList());
    }

    @Test
    void getWorkFlowShouldBuildEdgesWhenUiMapsAreNotMapObjects() throws Exception {
        WorkFlow current = WorkFlow.builder()
                .pluginList(List.of(
                        Plugin.builder().id(null).uiMap("NULL_ID").build(),
                        Plugin.builder().id(999).uiMap("NO_MATCH").build(),
                        Plugin.builder().id(1).uiMap("UI_1").build(),
                        Plugin.builder().id(2).uiMap("UI_2").build()
                ))
                .uiMapList(List.of())
                .build();
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder()
                .id(4L)
                .applicationName("app4")
                .workflow(Base64Util.base64Encode(objectMapper.writeValueAsString(current), true, objectMapper))
                .build();

        WorkflowEntityAndLinkingIdMapping mapping1 = WorkflowEntityAndLinkingIdMapping.builder()
                .id(41L).logicOrder(1).linkingId("L1").build();
        WorkflowEntityAndLinkingIdMapping mapping2 = WorkflowEntityAndLinkingIdMapping.builder()
                .id(42L).logicOrder(2).linkingId("L2").build();
        WorkflowEntityAndLinkingIdMapping mapping3 = WorkflowEntityAndLinkingIdMapping.builder()
                .id(43L).logicOrder(3).linkingId("L3").build();

        WorkflowRuleAndType rt1 = WorkflowRuleAndType.builder()
                .id(901L).linkingId("L1").workflowRule(WorkflowRule.builder().id(1L).key("$.1").build())
                .workflowType(WorkflowType.builder().id(11L).type("TYPE_A").build()).build();
        WorkflowRuleAndType rt2 = WorkflowRuleAndType.builder()
                .id(902L).linkingId("L2").workflowRule(WorkflowRule.builder().id(2L).key("$.2").build())
                .workflowType(WorkflowType.builder().id(12L).type("TYPE_B").build()).build();
        WorkflowRuleAndType rt3 = WorkflowRuleAndType.builder()
                .id(903L).linkingId("L3").workflowRule(WorkflowRule.builder().id(3L).key("$.3").build())
                .workflowType(WorkflowType.builder().id(13L).type("TYPE_C").build()).build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app4")).thenReturn(List.of(entitySetting));
        when(workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(4L))
                .thenReturn(new ArrayList<>(List.of(mapping1, mapping2, mapping3)));
        when(workflowRuleAndTypeRepository.findAllByLinkingIdIn(anyList())).thenReturn(List.of(rt1, rt2, rt3));

        WorkFlow result = controller.getWorkFlow("app4");

        assertEquals(3, result.getPluginList().size());
        assertEquals(2, result.getUiMapList().size());
        Map<?, ?> edge1 = (Map<?, ?>) result.getUiMapList().get(0);
        assertEquals("", edge1.get("source"));
        assertEquals("", edge1.get("target"));
    }

    @Test
    void getWorkFlowShouldFallbackToDefaultUiMapWhenCurrentWorkflowPluginListIsNull() throws Exception {
        WorkFlow current = WorkFlow.builder().pluginList(null).uiMapList(null).build();
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder()
                .id(5L)
                .applicationName("app5")
                .workflow(Base64Util.base64Encode(objectMapper.writeValueAsString(current), true, objectMapper))
                .build();

        WorkflowEntityAndLinkingIdMapping mapping = WorkflowEntityAndLinkingIdMapping.builder()
                .id(51L).logicOrder(1).linkingId("L5").build();
        WorkflowRuleAndType rt = WorkflowRuleAndType.builder()
                .id(951L).linkingId("L5").workflowRule(WorkflowRule.builder().id(9L).key("$.x").build())
                .workflowType(WorkflowType.builder().id(19L).type("TYPE_X").build()).build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app5")).thenReturn(List.of(entitySetting));
        when(workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(5L))
                .thenReturn(new ArrayList<>(List.of(mapping)));
        when(workflowRuleAndTypeRepository.findAllByLinkingIdIn(anyList())).thenReturn(List.of(rt));

        WorkFlow result = controller.getWorkFlow("app5");

        Map<?, ?> uiMap = (Map<?, ?>) result.getPluginList().get(0).getUiMap();
        assertEquals("TYPE_X_1", uiMap.get("id"));
        assertEquals(List.of(), result.getUiMapList());
    }

    @Test
    void getWorkFlowShouldHandleMissingRuleTypeMappingReference() {
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder()
                .id(6L)
                .applicationName("app6")
                .workflow(null)
                .build();

        WorkflowEntityAndLinkingIdMapping mapping = WorkflowEntityAndLinkingIdMapping.builder()
                .id(61L)
                .logicOrder(1)
                .remark("no-linking-ref")
                .linkingId(null)
                .build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app6")).thenReturn(List.of(entitySetting));
        when(workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(6L))
                .thenReturn(new ArrayList<>(List.of(mapping)));

        WorkFlow result = controller.getWorkFlow("app6");

        assertEquals(1, result.getPluginList().size());
        assertEquals(null, result.getPluginList().get(0).getLinkingIdOfRuleListAndAction());
        assertEquals("Unknown", ((Map<?, ?>) result.getPluginList().get(0).getUiMap()).get("type"));
    }

    @Test
    void getWorkFlowShouldFillDefaultUiMapWhenMatchedPluginUiMapIsNull() throws Exception {
        WorkFlow current = WorkFlow.builder()
                .pluginList(List.of(Plugin.builder().id(1).uiMap(null).build()))
                .uiMapList(null)
                .build();
        WorkflowEntitySetting entitySetting = WorkflowEntitySetting.builder()
                .id(7L)
                .applicationName("app7")
                .workflow(Base64Util.base64Encode(objectMapper.writeValueAsString(current), true, objectMapper))
                .build();

        WorkflowEntityAndLinkingIdMapping mapping = WorkflowEntityAndLinkingIdMapping.builder()
                .id(71L)
                .logicOrder(1)
                .remark("fill-ui-map")
                .linkingId("L7")
                .build();
        WorkflowRuleAndType rt = WorkflowRuleAndType.builder()
                .id(971L)
                .linkingId("L7")
                .workflowRule(WorkflowRule.builder().id(97L).key("$.k").build())
                .workflowType(WorkflowType.builder().id(197L).type("TYPE_7").build())
                .build();

        when(workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName("app7")).thenReturn(List.of(entitySetting));
        when(workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(7L))
                .thenReturn(new ArrayList<>(List.of(mapping)));
        when(workflowRuleAndTypeRepository.findAllByLinkingIdIn(anyList())).thenReturn(List.of(rt));

        WorkFlow result = controller.getWorkFlow("app7");

        Map<?, ?> uiMap = (Map<?, ?>) result.getPluginList().get(0).getUiMap();
        assertEquals("TYPE_7_1", uiMap.get("id"));
    }
}
