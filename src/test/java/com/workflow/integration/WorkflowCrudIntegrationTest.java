package com.workflow.integration;

import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowCrudIntegrationTest extends AbstractWorkflowIntegrationTest {

    @Nested
    @DisplayName("POST workflow -> verify DB tables populated correctly")
    class PostDbVerification {

        @Test
        @DisplayName("All DB tables should contain expected records after POST")
        void postWorkflowPopulatesAllDbTables() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            postWorkflow(APP_NAME, requestBody);

            List<WorkflowEntitySetting> entitySettings =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME);
            assertEquals(1, entitySettings.size(), "Exactly one entity setting should exist");

            WorkflowEntitySetting setting = entitySettings.get(0);
            assertNotNull(setting.getId());
            assertEquals(APP_NAME, setting.getApplicationName());
            assertTrue(setting.isEnabled(), "Entity setting should be enabled");
            assertNotNull(setting.getWorkflow(), "Workflow JSON should be stored");

            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            assertEquals(requestBody.getPluginList().size(), mappings.size(),
                    "One linking-id mapping per plugin");

            for (WorkflowEntityAndLinkingIdMapping mapping : mappings) {
                assertNotNull(mapping.getLinkingId(), "linkingId must not be null");
                assertFalse(mapping.getLinkingId().isBlank(), "linkingId must not be blank");
                assertNotNull(mapping.getLogicOrder(), "logicOrder must not be null");
                assertNotNull(mapping.getRemark(), "remark (description) must not be null");
            }

            List<String> linkingIds = mappings.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();
            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(linkingIds);
            assertFalse(ruleAndTypes.isEmpty(), "Rule-and-type mappings must exist");

            int expectedTotalRules = requestBody.getPluginList().stream()
                    .mapToInt(p -> p.getRuleList() != null && !p.getRuleList().isEmpty()
                            ? p.getRuleList().size() : 1)
                    .sum();
            assertEquals(expectedTotalRules, ruleAndTypes.size(),
                    "Total rule-and-type mappings should match total rules across all plugins");

            for (WorkflowRuleAndType rat : ruleAndTypes) {
                assertNotNull(rat.getWorkflowRule(), "Rule reference must not be null");
                assertNotNull(rat.getWorkflowType(), "Type reference must not be null");
                assertTrue(ruleRepository.existsById(rat.getWorkflowRule().getId()),
                        "Rule should exist in rule table");
                assertTrue(typeRepository.existsById(rat.getWorkflowType().getId()),
                        "Type should exist in type table");
            }

            long distinctTypeIds = ruleAndTypes.stream()
                    .map(rt -> rt.getWorkflowType().getId()).distinct().count();
            assertEquals(requestBody.getPluginList().size(), distinctTypeIds,
                    "One distinct type per plugin");
        }

        @Test
        @DisplayName("LinkingId format should include entitySettingId, typeId, and pluginId")
        void linkingIdFormatIsCorrect() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());

            for (WorkflowEntityAndLinkingIdMapping mapping : mappings) {
                String[] parts = mapping.getLinkingId().split("_");
                assertEquals(3, parts.length,
                        "LinkingId should have format entitySettingId_typeId_pluginId: " + mapping.getLinkingId());
                assertEquals(String.valueOf(setting.getId()), parts[0]);
                assertEquals(String.valueOf(mapping.getLogicOrder()), parts[2]);
            }
        }
    }

    @Nested
    @DisplayName("POST response body verification")
    class PostResponseBody {

        @Test
        @DisplayName("POST response body matches subsequent GET response")
        void postResponseMatchesGet() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            MvcResult postResult = mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow postResponse = objectMapper.readValue(
                    postResult.getResponse().getContentAsString(), WorkFlow.class);
            WorkFlow getResponse = getWorkflow(APP_NAME);

            assertEquals(postResponse.getPluginList().size(), getResponse.getPluginList().size());

            for (int i = 0; i < postResponse.getPluginList().size(); i++) {
                Plugin pp = postResponse.getPluginList().get(i);
                Plugin gp = getResponse.getPluginList().get(i);
                assertEquals(pp.getId(), gp.getId());
                assertEquals(pp.getDescription(), gp.getDescription());
                assertEquals(pp.getLinkingIdOfRuleListAndAction(), gp.getLinkingIdOfRuleListAndAction());
                assertEquals(pp.getAction().getType(), gp.getAction().getType());
                assertEquals(pp.getRuleList().size(), gp.getRuleList().size());
            }

            assertEquals(postResponse.getUiMapList().size(), getResponse.getUiMapList().size());
        }

        @Test
        @DisplayName("POST response contains valid linkingIdOfRuleListAndAction for each plugin")
        void postResponseContainsLinkingIds() throws Exception {
            String json = postWorkflow(APP_NAME, loadTestWorkflow());
            WorkFlow postResponse = objectMapper.readValue(json, WorkFlow.class);

            for (Plugin plugin : postResponse.getPluginList()) {
                assertNotNull(plugin.getLinkingIdOfRuleListAndAction(),
                        "linkingId should be present for plugin " + plugin.getId());
                assertFalse(plugin.getLinkingIdOfRuleListAndAction().isBlank());
            }
        }
    }

    @Nested
    @DisplayName("POST then GET -> round-trip data fidelity")
    class RoundTripFidelity {

        @Test
        @DisplayName("Plugin count, IDs, descriptions, action types, and rule keys survive round-trip")
        void fullPluginFidelity() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            postWorkflow(APP_NAME, requestBody);
            WorkFlow got = getWorkflow(APP_NAME);

            assertEquals(requestBody.getPluginList().size(), got.getPluginList().size());

            for (int i = 0; i < requestBody.getPluginList().size(); i++) {
                Plugin expected = requestBody.getPluginList().get(i);
                Plugin actual = got.getPluginList().get(i);
                assertEquals(expected.getId(), actual.getId(), "Plugin id at index " + i);
                assertEquals(expected.getDescription(), actual.getDescription(), "Description at index " + i);
                assertEquals(expected.getAction().getType(), actual.getAction().getType(), "Action type at index " + i);
                assertEquals(expected.getAction().getProvider(), actual.getAction().getProvider(), "Provider at index " + i);
                assertEquals(expected.getAction().getRemark(), actual.getAction().getRemark(), "Remark at index " + i);
                assertEquals(expected.getRuleList().size(), actual.getRuleList().size(), "Rule count at index " + i);
                for (int j = 0; j < expected.getRuleList().size(); j++) {
                    assertEquals(expected.getRuleList().get(j).getKey(), actual.getRuleList().get(j).getKey());
                    assertEquals(expected.getRuleList().get(j).getRemark(), actual.getRuleList().get(j).getRemark());
                }
            }
        }

        @Test
        @DisplayName("Action HTTP fields survive round-trip (method, URL, headers, body, elseLogic)")
        void actionHttpFieldsFidelity() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            postWorkflow(APP_NAME, requestBody);
            WorkFlow got = getWorkflow(APP_NAME);

            for (int i = 0; i < requestBody.getPluginList().size(); i++) {
                var expected = requestBody.getPluginList().get(i).getAction();
                var actual = got.getPluginList().get(i).getAction();
                assertEquals(expected.getHttpRequestMethod(), actual.getHttpRequestMethod(), "httpRequestMethod at " + i);
                assertEquals(expected.getHttpRequestUrlWithQueryParameter(), actual.getHttpRequestUrlWithQueryParameter(), "URL at " + i);
                assertEquals(expected.getInternalHttpRequestUrlWithQueryParameter(), actual.getInternalHttpRequestUrlWithQueryParameter(), "internalURL at " + i);
                assertEquals(expected.getHttpRequestHeaders(), actual.getHttpRequestHeaders(), "headers at " + i);
                assertEquals(expected.getHttpRequestBody(), actual.getHttpRequestBody(), "body at " + i);
                assertEquals(expected.getElseLogic(), actual.getElseLogic(), "elseLogic at " + i);
            }
        }

        @Test
        @DisplayName("uiMapList survives round-trip")
        void uiMapListFidelity() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            postWorkflow(APP_NAME, requestBody);
            WorkFlow got = getWorkflow(APP_NAME);
            assertNotNull(got.getUiMapList(), "uiMapList should be present");
            assertEquals(requestBody.getUiMapList().size(), got.getUiMapList().size());
        }
    }

    @Nested
    @DisplayName("DELETE workflow -> verify all DB records removed")
    class DeleteDbVerification {

        @Test
        @DisplayName("DELETE removes entity setting, mappings, rules, and types from DB")
        void deleteRemovesAllDbRecords() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            Long settingId = setting.getId();
            List<WorkflowEntityAndLinkingIdMapping> mappingsBefore =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingId);
            List<String> linkingIdsBefore = mappingsBefore.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();
            List<WorkflowRuleAndType> ruleAndTypesBefore =
                    ruleAndTypeRepository.findAllByLinkingIdIn(linkingIdsBefore);
            List<Long> ruleIdsBefore = ruleAndTypesBefore.stream()
                    .map(rt -> rt.getWorkflowRule().getId()).distinct().toList();
            List<Long> typeIdsBefore = ruleAndTypesBefore.stream()
                    .map(rt -> rt.getWorkflowType().getId()).distinct().toList();

            assertFalse(mappingsBefore.isEmpty());
            assertFalse(ruleIdsBefore.isEmpty());
            assertFalse(typeIdsBefore.isEmpty());

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isOk());

            assertTrue(entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).isEmpty());
            assertTrue(linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingId).isEmpty());
            assertTrue(ruleAndTypeRepository.findAllByLinkingIdIn(linkingIdsBefore).isEmpty());
            for (Long ruleId : ruleIdsBefore) assertFalse(ruleRepository.existsById(ruleId));
            for (Long typeId : typeIdsBefore) assertFalse(typeRepository.existsById(typeId));
        }

        @Test
        @DisplayName("DELETE then GET returns 400")
        void deleteThenGetReturns400() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());
            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME)).andExpect(status().isOk());
            mockMvc.perform(get("/api/workflow").param("applicationName", APP_NAME)).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE non-existent application returns 400")
        void deleteNonExistentReturns400() throws Exception {
            mockMvc.perform(delete("/api/workflow").param("applicationName", "NON_EXISTENT_APP"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Double DELETE - second DELETE returns 400")
        void doubleDeleteReturns400OnSecond() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());
            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME)).andExpect(status().isOk());
            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME)).andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST update (re-POST) -> old data replaced, DB consistent")
    class UpdateDbVerification {

        @Test
        @DisplayName("Re-POST replaces old rules/types/mappings with new ones")
        void rePostReplacesOldData() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());

            WorkflowEntitySetting settingAfterFirst =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappingsFirst =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingAfterFirst.getId());
            List<String> linkingIdsFirst = mappingsFirst.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();
            List<WorkflowRuleAndType> ruleAndTypesFirst =
                    ruleAndTypeRepository.findAllByLinkingIdIn(linkingIdsFirst);
            List<Long> oldRuleIds = ruleAndTypesFirst.stream()
                    .map(rt -> rt.getWorkflowRule().getId()).distinct().toList();
            List<Long> oldTypeIds = ruleAndTypesFirst.stream()
                    .map(rt -> rt.getWorkflowType().getId()).distinct().toList();

            WorkFlow modified = loadTestWorkflow();
            modified.setPluginList(modified.getPluginList().subList(0, 3));
            postWorkflow(APP_NAME, modified);

            WorkflowEntitySetting settingAfterSecond =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            assertEquals(settingAfterFirst.getId(), settingAfterSecond.getId(), "Entity setting ID should be reused");

            List<WorkflowEntityAndLinkingIdMapping> mappingsSecond =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingAfterSecond.getId());
            assertEquals(3, mappingsSecond.size());

            for (Long id : oldRuleIds) assertFalse(ruleRepository.existsById(id), "Old rule " + id + " should be removed");
            for (Long id : oldTypeIds) assertFalse(typeRepository.existsById(id), "Old type " + id + " should be removed");
            assertTrue(ruleAndTypeRepository.findAllByLinkingIdIn(linkingIdsFirst).isEmpty());
        }

        @Test
        @DisplayName("Re-POST with entirely new plugin types replaces all old data")
        void rePostWithNewPluginTypesReplacesAll() throws Exception {
            WorkFlow original = loadTestWorkflow();
            postWorkflow(APP_NAME, original);

            WorkflowEntitySetting settingFirst =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappingsFirst =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingFirst.getId());
            List<String> linkingIdsFirst = mappingsFirst.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();
            List<WorkflowRuleAndType> ruleAndTypesFirst =
                    ruleAndTypeRepository.findAllByLinkingIdIn(linkingIdsFirst);
            List<Long> oldRuleIds = ruleAndTypesFirst.stream()
                    .map(rt -> rt.getWorkflowRule().getId()).distinct().toList();
            List<Long> oldTypeIds = ruleAndTypesFirst.stream()
                    .map(rt -> rt.getWorkflowType().getId()).distinct().toList();

            Plugin newPlugin = Plugin.builder()
                    .id(99)
                    .description("Brand new plugin")
                    .ruleList(List.of(
                            WorkflowRule.builder().key("$.brand.new.key").remark("New rule A").build(),
                            WorkflowRule.builder().key("$.another.new.key").remark("New rule B").build()))
                    .action(WorkflowType.builder().provider("NewProvider").type("CONSUMER")
                            .remark("Different action").httpRequestMethod("PUT")
                            .httpRequestUrlWithQueryParameter("https://new-service.example.com/api")
                            .internalHttpRequestUrlWithQueryParameter("https://new-internal.example.com/api")
                            .httpRequestHeaders("{\"X-Custom\":\"value\"}")
                            .httpRequestBody("{\"newKey\":\"newValue\"}")
                            .trackingNumberSchemaInHttpResponse("{\"id\":\"$.newId\"}").build())
                    .build();

            postWorkflow(APP_NAME, WorkFlow.builder().pluginList(List.of(newPlugin)).uiMapList(List.of()).build());

            for (Long id : oldRuleIds) assertFalse(ruleRepository.existsById(id));
            for (Long id : oldTypeIds) assertFalse(typeRepository.existsById(id));
            assertTrue(ruleAndTypeRepository.findAllByLinkingIdIn(linkingIdsFirst).isEmpty());

            WorkFlow got = getWorkflow(APP_NAME);
            assertEquals(1, got.getPluginList().size());
            assertEquals(99, got.getPluginList().get(0).getId());
            assertEquals("NewProvider", got.getPluginList().get(0).getAction().getProvider());
            assertEquals(2, got.getPluginList().get(0).getRuleList().size());
        }

        @Test
        @DisplayName("Re-POST returns correct GET response reflecting new data")
        void rePostGetReflectsNewData() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());

            WorkFlow modified = loadTestWorkflow();
            modified.setPluginList(modified.getPluginList().subList(0, 5));
            postWorkflow(APP_NAME, modified);

            WorkFlow got = getWorkflow(APP_NAME);
            assertEquals(5, got.getPluginList().size());
            for (int i = 0; i < 5; i++) {
                assertEquals(modified.getPluginList().get(i).getId(), got.getPluginList().get(i).getId());
                assertEquals(modified.getPluginList().get(i).getAction().getType(), got.getPluginList().get(i).getAction().getType());
            }
        }
    }
}
