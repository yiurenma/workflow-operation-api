package com.workflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMapping;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMappingRepository;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import com.workflow.dao.repository.WorkflowReport;
import com.workflow.dao.repository.WorkflowReportRepository;
import com.workflow.dao.repository.WorkflowRule;
import com.workflow.dao.repository.WorkflowRuleAndTypeRepository;
import com.workflow.dao.repository.WorkflowRuleRepository;
import com.workflow.dao.repository.WorkflowType;
import com.workflow.dao.repository.WorkflowTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private WorkflowEntitySettingRepository workflowEntitySettingRepository;
    @Autowired
    private WorkflowEntityAndLinkingIdMappingRepository workflowEntityAndLinkingIdMappingRepository;
    @Autowired
    private WorkflowRuleAndTypeRepository workflowRuleAndTypeRepository;
    @Autowired
    private WorkflowRuleRepository workflowRuleRepository;
    @Autowired
    private WorkflowTypeRepository workflowTypeRepository;
    @Autowired
    private WorkflowReportRepository workflowReportRepository;

    @BeforeEach
    void cleanDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        workflowEntityAndLinkingIdMappingRepository.deleteAllInBatch();
        workflowReportRepository.deleteAllInBatch();
        workflowEntitySettingRepository.deleteAllInBatch();
        workflowRuleAndTypeRepository.deleteAllInBatch();
        workflowRuleRepository.deleteAllInBatch();
        workflowTypeRepository.deleteAllInBatch();
    }

    @Test
    void postGetDeleteShouldPersistAndCleanupAllWorkflowData() throws Exception {
        String app = "it_app_flow";
        WorkFlow request = buildFlowWithTwoPlugins();

        mockMvc.perform(post("/api/workflow")
                        .queryParam("applicationName", app)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pluginList", hasSize(1)));

        WorkflowEntitySetting setting = getSingleSetting(app);
        assertNotNull(setting.getWorkflow());
        List<WorkflowEntityAndLinkingIdMapping> mappings =
                workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
        assertEquals(1, mappings.size());
        assertEquals(1L, workflowRuleAndTypeRepository.count());
        assertEquals(1L, workflowRuleRepository.count());
        assertEquals(1L, workflowTypeRepository.count());

        mockMvc.perform(get("/api/workflow").queryParam("applicationName", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pluginList", hasSize(1)))
                .andExpect(jsonPath("$.pluginList[0].action").exists());

        mockMvc.perform(delete("/api/workflow").queryParam("applicationName", app))
                .andExpect(status().isOk());

        assertEquals(0, workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(app).size());
        assertEquals(0L, workflowEntityAndLinkingIdMappingRepository.count());
        assertEquals(0L, workflowRuleAndTypeRepository.count());
        assertEquals(0L, workflowRuleRepository.count());
        assertEquals(0L, workflowTypeRepository.count());
    }

    @Test
    void postWorkflowShouldReplaceExistingRowsNotAppend() throws Exception {
        String app = "it_app_replace";

        mockMvc.perform(post("/api/workflow")
                        .queryParam("applicationName", app)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFlowWithTwoPlugins())))
                .andExpect(status().isOk());
        assertEquals(1L, workflowEntityAndLinkingIdMappingRepository.count());
        assertEquals(1L, workflowRuleAndTypeRepository.count());
        assertEquals(1L, workflowRuleRepository.count());
        assertEquals(1L, workflowTypeRepository.count());

        mockMvc.perform(post("/api/workflow")
                        .queryParam("applicationName", app)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFlowWithOnePlugin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pluginList", hasSize(1)));

        WorkflowEntitySetting setting = getSingleSetting(app);
        assertEquals(1, workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId()).size());
        assertEquals(1L, workflowRuleAndTypeRepository.count());
        assertEquals(1L, workflowRuleRepository.count());
        assertEquals(1L, workflowTypeRepository.count());
    }

    @Test
    void autoCopyShouldCreateTargetWorkflowAndData() throws Exception {
        String source = "it_app_src";
        String target = "it_app_dst";

        mockMvc.perform(post("/api/workflow")
                        .queryParam("applicationName", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFlowWithTwoPlugins())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/workflow/autoCopy")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("fromApplicationName", source)
                        .queryParam("toApplicationName", target))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pluginList", hasSize(1)));

        WorkflowEntitySetting sourceSetting = getSingleSetting(source);
        WorkflowEntitySetting targetSetting = getSingleSetting(target);
        assertFalse(sourceSetting.getId().equals(targetSetting.getId()));
        assertEquals(1, workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(targetSetting.getId()).size());

        mockMvc.perform(get("/api/workflow").queryParam("applicationName", target))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pluginList", hasSize(1)));
    }

    @Test
    void deleteShouldReturnConflictWhenReportsExistAndKeepRows() throws Exception {
        String app = "it_app_report_conflict";

        mockMvc.perform(post("/api/workflow")
                        .queryParam("applicationName", app)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFlowWithTwoPlugins())))
                .andExpect(status().isOk());

        WorkflowEntitySetting setting = getSingleSetting(app);
        workflowReportRepository.saveAndFlush(WorkflowReport.builder()
                .reportGroup(1L)
                .enabled(true)
                .workflowEntitySetting(setting)
                .cronExpression("0 0 * * * *")
                .reportTimeRangeByHours(24)
                .timezone("UTC")
                .build());

        mockMvc.perform(delete("/api/workflow").queryParam("applicationName", app))
                .andExpect(status().isConflict());

        assertEquals(1, workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(app).size());
        assertEquals(1, workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId()).size());
        assertEquals(1, workflowReportRepository.findByWorkflowEntitySetting_Id(setting.getId()).size());
    }

    @Test
    void createWithoutBodyAndDeleteMissingShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/workflow")
                        .queryParam("applicationName", "it_app_no_body")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/workflow").queryParam("applicationName", "it_app_missing"))
                .andExpect(status().isBadRequest());
    }

    private WorkflowEntitySetting getSingleSetting(String appName) {
        List<WorkflowEntitySetting> settings = workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(appName);
        assertEquals(1, settings.size());
        return settings.get(0);
    }

    private WorkFlow buildFlowWithTwoPlugins() {
        return WorkFlow.builder()
                .pluginList(List.of(
                        Plugin.builder()
                                .id(1)
                                .description("rule-plugin")
                                .ruleList(List.of(WorkflowRule.builder().key("$.payload[?(@.ok == true)]").remark("rule-1").build()))
                                .action(WorkflowType.builder()
                                        .provider("IT_PROVIDER")
                                        .type("IFELSE")
                                        .remark("action-1")
                                        .elseLogic("{\"step\":1}")
                                        .httpRequestMethod("POST")
                                        .httpRequestUrlWithQueryParameter("https://example.org/a")
                                        .internalHttpRequestUrlWithQueryParameter("https://internal.example.org/a")
                                        .httpRequestHeaders("{\"x\":\"1\"}")
                                        .httpRequestBody("{\"body\":\"a\"}")
                                        .trackingNumberSchemaInHttpResponse("{\"tracking\":\"$.id\"}")
                                        .build())
                                .uiMap(buildUiMap("IFELSE_1", "IFELSE", 100))
                                .build()
                ))
                .uiMapList(List.of())
                .build();
    }

    private WorkFlow buildFlowWithOnePlugin() {
        return WorkFlow.builder()
                .pluginList(List.of(
                        Plugin.builder()
                                .id(1)
                                .description("single-plugin")
                                .ruleList(List.of(WorkflowRule.builder().key("$.payload[?(@.ok == false)]").remark("rule-single").build()))
                                .action(WorkflowType.builder()
                                        .provider("IT_PROVIDER")
                                        .type("IFELSE")
                                        .remark("single-action")
                                        .elseLogic("{\"single\":true}")
                                        .httpRequestMethod("POST")
                                        .build())
                                .uiMap(buildUiMap("IFELSE_1", "IFELSE", 100))
                                .build()
                ))
                .uiMapList(List.of())
                .build();
    }

    private Map<String, Object> buildUiMap(String id, String type, int y) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("type", type);
        map.put("position", Map.of("x", 100, "y", y));
        return map;
    }
}
