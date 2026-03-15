package com.workflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Entity setting ID exposure integration tests")
class WorkflowEntitySettingIdExposureIntegrationTest extends AbstractWorkflowIntegrationTest {

    @Test
    @DisplayName("Repository endpoints should expose id field")
    void repositoryEndpointsShouldExposeIdField() throws Exception {
        postWorkflow(APP_NAME, loadTestWorkflow());
        Long expectedId = entitySettingRepository
                .getWorkflowEntitySettingByApplicationName(APP_NAME)
                .get(0)
                .getId();

        MvcResult listResult = mockMvc.perform(get("/entity-setting")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listRoot = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode itemInList = findEntitySettingByApplicationName(listRoot, APP_NAME);
        assertNotNull(itemInList, "Expected APP_NAME item in /entity-setting response");
        assertTrue(itemInList.has("id"), "Repository list response should expose id");
        assertEquals(expectedId.longValue(), itemInList.get("id").asLong());

        MvcResult byIdResult = mockMvc.perform(get("/entity-setting/{id}", expectedId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode byIdRoot = objectMapper.readTree(byIdResult.getResponse().getContentAsString());
        assertTrue(byIdRoot.has("id"), "Repository get-by-id response should expose id");
        assertEquals(expectedId.longValue(), byIdRoot.get("id").asLong());
        assertEquals(APP_NAME, byIdRoot.path("applicationName").asText());
    }

    @Test
    @DisplayName("Business query endpoint should expose id field")
    void businessQueryEndpointShouldExposeIdField() throws Exception {
        postWorkflow(APP_NAME, loadTestWorkflow());
        Long expectedId = entitySettingRepository
                .getWorkflowEntitySettingByApplicationName(APP_NAME)
                .get(0)
                .getId();

        MvcResult result = mockMvc.perform(get("/api/workflow/entity-setting")
                        .param("applicationName", APP_NAME)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode item = findEntitySettingByApplicationName(root, APP_NAME);
        assertNotNull(item, "Expected APP_NAME item in /api/workflow/entity-setting response");
        assertTrue(item.has("id"), "Business query response should expose id");
        assertEquals(expectedId.longValue(), item.get("id").asLong());
    }

    private JsonNode findEntitySettingByApplicationName(JsonNode node, String applicationName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode appNameNode = node.get("applicationName");
            if (appNameNode != null && applicationName.equals(appNameNode.asText())) {
                return node;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode found = findEntitySettingByApplicationName(entry.getValue(), applicationName);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode found = findEntitySettingByApplicationName(item, applicationName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
