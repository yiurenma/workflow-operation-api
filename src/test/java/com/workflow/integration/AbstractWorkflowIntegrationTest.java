package com.workflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractWorkflowIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected WorkflowEntitySettingRepository entitySettingRepository;
    @Autowired
    protected WorkflowEntityAndLinkingIdMappingRepository linkingIdMappingRepository;
    @Autowired
    protected WorkflowRuleAndTypeRepository ruleAndTypeRepository;
    @Autowired
    protected WorkflowRuleRepository ruleRepository;
    @Autowired
    protected WorkflowTypeRepository typeRepository;
    @Autowired
    protected WorkflowReportRepository reportRepository;

    protected static final String APP_NAME = "ITEST_APP";
    protected static final String APP_NAME_2 = "ITEST_APP_2";
    protected static final String APP_NAME_COPY = "ITEST_APP_COPY";

    @BeforeEach
    void cleanup() throws Exception {
        cleanupApp(APP_NAME_COPY);
        cleanupApp(APP_NAME_2);
        cleanupApp(APP_NAME);
    }

    @AfterEach
    void teardown() throws Exception {
        cleanupApp(APP_NAME_COPY);
        cleanupApp(APP_NAME_2);
        cleanupApp(APP_NAME);
    }

    private void cleanupApp(String appName) throws Exception {
        List<WorkflowEntitySetting> settings =
                entitySettingRepository.getWorkflowEntitySettingByApplicationName(appName);
        for (WorkflowEntitySetting setting : settings) {
            reportRepository.findByWorkflowEntitySetting_Id(setting.getId())
                    .forEach(r -> reportRepository.deleteById(r.getId()));
            reportRepository.flush();
        }
        try {
            mockMvc.perform(delete("/api/workflow").param("applicationName", appName));
        } catch (Exception ignored) {
        }
    }

    protected WorkFlow loadTestWorkflow() throws IOException {
        String json = new String(new ClassPathResource("workflow-integration-test-data.json")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, WorkFlow.class);
    }

    protected String postWorkflow(String appName, WorkFlow workflow) throws Exception {
        return mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/workflow")
                                .param("applicationName", appName)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(workflow)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    protected WorkFlow getWorkflow(String appName) throws Exception {
        String json = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/workflow")
                                .param("applicationName", appName))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, WorkFlow.class);
    }
}
