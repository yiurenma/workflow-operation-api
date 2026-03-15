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

@DisplayName("Workflow edge case integration tests")
class WorkflowEdgeCaseIntegrationTest extends AbstractWorkflowIntegrationTest {

    @Nested
    @DisplayName("Plugin with empty ruleList or null action")
    class EmptyRuleListAndNullAction {

        @Test
        @DisplayName("Plugin with empty ruleList [] creates empty rule and type in DB")
        void pluginWithEmptyRuleListCreatesEmptyRuleInDb() throws Exception {
            Plugin emptyRulePlugin = Plugin.builder()
                    .id(1).description("Plugin with no rules").ruleList(List.of())
                    .action(WorkflowType.builder().provider("TestProvider").type("CONSUMER")
                            .remark("Action with no rules").httpRequestMethod("GET")
                            .httpRequestUrlWithQueryParameter("https://example.com/test")
                            .internalHttpRequestUrlWithQueryParameter("https://example.com/test")
                            .httpRequestHeaders("{\"Accept\":\"application/json\"}")
                            .httpRequestBody("").trackingNumberSchemaInHttpResponse("{}").build())
                    .build();

            postWorkflow(APP_NAME, WorkFlow.builder().pluginList(List.of(emptyRulePlugin)).uiMapList(List.of()).build());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            assertEquals(1, mappings.size());

            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(
                            mappings.stream().map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList());
            assertEquals(1, ruleAndTypes.size());
            assertEquals("", ruleAndTypes.get(0).getWorkflowRule().getKey(), "Empty rule should have blank key");
            assertEquals("TestProvider", ruleAndTypes.get(0).getWorkflowType().getProvider());
        }

        @Test
        @DisplayName("Plugin with null action creates type with null fields")
        void pluginWithNullActionCreatesTypeWithNullFields() throws Exception {
            Plugin nullActionPlugin = Plugin.builder()
                    .id(1).description("Plugin with null action")
                    .ruleList(List.of(WorkflowRule.builder().key("$.someField").remark("A rule").build()))
                    .action(null).build();

            postWorkflow(APP_NAME, WorkFlow.builder().pluginList(List.of(nullActionPlugin)).uiMapList(List.of()).build());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(
                            mappings.stream().map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList());
            assertEquals(1, ruleAndTypes.size());

            WorkflowType dbType = ruleAndTypes.get(0).getWorkflowType();
            assertNotNull(dbType.getId());
            assertNull(dbType.getProvider());
            assertNull(dbType.getType());
        }

        @Test
        @DisplayName("Empty-rule plugin round-trips correctly via GET")
        void emptyRulePluginRoundTrips() throws Exception {
            postWorkflow(APP_NAME, WorkFlow.builder()
                    .pluginList(List.of(Plugin.builder().id(1).description("Empty rule plugin").ruleList(List.of())
                            .action(WorkflowType.builder().provider("SYSTEM").type("IFELSE")
                                    .remark("No rules action").build()).build()))
                    .uiMapList(List.of()).build());

            WorkFlow got = getWorkflow(APP_NAME);
            assertEquals(1, got.getPluginList().size());
            assertEquals("SYSTEM", got.getPluginList().get(0).getAction().getProvider());
            assertEquals("IFELSE", got.getPluginList().get(0).getAction().getType());
        }
    }

    @Nested
    @DisplayName("Validation error handling")
    class ValidationErrors {

        @Test
        @DisplayName("POST without body returns 400")
        void postWithoutBodyReturns400() throws Exception {
            mockMvc.perform(post("/api/workflow").param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET non-existent application returns 400")
        void getNonExistentReturns400() throws Exception {
            mockMvc.perform(get("/api/workflow").param("applicationName", "NON_EXISTENT_APP"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST with empty applicationName parameter is accepted")
        void postEmptyApplicationNameAccepted() throws Exception {
            postWorkflow("", loadTestWorkflow());
            mockMvc.perform(delete("/api/workflow").param("applicationName", ""));
        }
    }

    @Nested
    @DisplayName("DELETE blocked when reports exist (409)")
    class DeleteBlockedByReports {

        @Test
        @DisplayName("DELETE returns 409 when workflow has associated reports")
        void deleteReturns409WhenReportsExist() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            reportRepository.saveAndFlush(WorkflowReport.builder()
                    .workflowEntitySetting(setting).reportGroup(1L).enabled(true)
                    .cronExpression("0 0 * * *").reportTimeRangeByHours(24).timezone("UTC").build());

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isConflict());

            assertEquals(1, entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).size(),
                    "Entity setting should still exist after blocked delete");
            assertFalse(linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId()).isEmpty(),
                    "Mappings should still exist after blocked delete");
        }
    }

    @Nested
    @DisplayName("Empty and null plugin list handling")
    class EmptyAndNullPluginList {

        @Test
        @DisplayName("POST workflow with empty plugin list")
        void postEmptyPluginList() throws Exception {
            postWorkflow(APP_NAME, WorkFlow.builder().pluginList(List.of()).uiMapList(List.of()).build());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            assertTrue(linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId()).isEmpty());
            assertTrue(getWorkflow(APP_NAME).getPluginList().isEmpty());
        }

        @Test
        @DisplayName("POST workflow with null pluginList treated as empty")
        void postNullPluginListTreatedAsEmpty() throws Exception {
            postWorkflow(APP_NAME, WorkFlow.builder().pluginList(null).uiMapList(null).build());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            assertTrue(linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId()).isEmpty());
        }
    }

    @Nested
    @DisplayName("Lifecycle and isolation tests")
    class LifecycleAndIsolation {

        @Test
        @DisplayName("POST workflow with single plugin having multiple rules")
        void postSinglePluginMultipleRules() throws Exception {
            WorkFlow fullWorkflow = loadTestWorkflow();
            Plugin multiRulePlugin = fullWorkflow.getPluginList().get(fullWorkflow.getPluginList().size() - 1);
            assertTrue(multiRulePlugin.getRuleList().size() > 1);

            postWorkflow(APP_NAME, WorkFlow.builder().pluginList(List.of(multiRulePlugin)).uiMapList(List.of()).build());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            assertEquals(1, mappings.size());

            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(
                            mappings.stream().map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList());
            assertEquals(multiRulePlugin.getRuleList().size(), ruleAndTypes.size());
            assertEquals(1, ruleAndTypes.stream().map(rt -> rt.getWorkflowType().getId()).distinct().count(),
                    "All rules should share the same type");
        }

        @Test
        @DisplayName("POST then DELETE then POST again creates fresh data")
        void postDeletePostCreatesFreshData() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());
            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME)).andExpect(status().isOk());

            WorkFlow secondPost = loadTestWorkflow();
            secondPost.setPluginList(secondPost.getPluginList().subList(0, 2));
            postWorkflow(APP_NAME, secondPost);

            List<WorkflowEntitySetting> settings =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME);
            assertEquals(1, settings.size());
            assertEquals(2, linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settings.get(0).getId()).size());
            assertEquals(2, getWorkflow(APP_NAME).getPluginList().size());
        }

        @Test
        @DisplayName("Multiple applications are isolated from each other")
        void multipleApplicationsIsolated() throws Exception {
            WorkFlow fullWorkflow = loadTestWorkflow();
            WorkFlow partialWorkflow = loadTestWorkflow();
            partialWorkflow.setPluginList(partialWorkflow.getPluginList().subList(0, 3));

            postWorkflow(APP_NAME, fullWorkflow);
            postWorkflow(APP_NAME_2, partialWorkflow);

            assertEquals(fullWorkflow.getPluginList().size(), getWorkflow(APP_NAME).getPluginList().size());
            assertEquals(3, getWorkflow(APP_NAME_2).getPluginList().size());

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME_2)).andExpect(status().isOk());
            getWorkflow(APP_NAME);

            WorkflowEntitySetting setting1 =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            assertEquals(fullWorkflow.getPluginList().size(),
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting1.getId()).size());
        }

        @Test
        @DisplayName("Full lifecycle: POST -> re-POST -> DELETE -> all records gone")
        void fullLifecyclePostRePostDelete() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());

            WorkFlow modified = loadTestWorkflow();
            modified.setPluginList(modified.getPluginList().subList(0, 4));
            postWorkflow(APP_NAME, modified);

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            Long settingId = setting.getId();
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingId);
            assertEquals(4, mappings.size());

            List<String> linkingIds = mappings.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();
            List<WorkflowRuleAndType> ruleAndTypes = ruleAndTypeRepository.findAllByLinkingIdIn(linkingIds);
            List<Long> ruleIds = ruleAndTypes.stream().map(rt -> rt.getWorkflowRule().getId()).distinct().toList();
            List<Long> typeIds = ruleAndTypes.stream().map(rt -> rt.getWorkflowType().getId()).distinct().toList();

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME)).andExpect(status().isOk());

            assertTrue(entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).isEmpty());
            assertTrue(linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingId).isEmpty());
            assertTrue(ruleAndTypeRepository.findAllByLinkingIdIn(linkingIds).isEmpty());
            for (Long id : ruleIds) assertFalse(ruleRepository.existsById(id));
            for (Long id : typeIds) assertFalse(typeRepository.existsById(id));
        }

        @Test
        @DisplayName("Multiple re-POSTs then DELETE leaves zero orphan records")
        void multipleRePostsThenDeleteNoOrphans() throws Exception {
            long ruleCountBefore = ruleRepository.count();
            long typeCountBefore = typeRepository.count();
            long ruleAndTypeCountBefore = ruleAndTypeRepository.count();
            long mappingCountBefore = linkingIdMappingRepository.count();

            postWorkflow(APP_NAME, loadTestWorkflow());

            WorkFlow w2 = loadTestWorkflow();
            w2.setPluginList(w2.getPluginList().subList(0, 5));
            postWorkflow(APP_NAME, w2);

            WorkFlow w3 = loadTestWorkflow();
            w3.setPluginList(w3.getPluginList().subList(0, 2));
            postWorkflow(APP_NAME, w3);

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME)).andExpect(status().isOk());

            assertEquals(ruleCountBefore, ruleRepository.count(), "Rule count should return to baseline");
            assertEquals(typeCountBefore, typeRepository.count(), "Type count should return to baseline");
            assertEquals(ruleAndTypeCountBefore, ruleAndTypeRepository.count(), "RuleAndType count should return to baseline");
            assertEquals(mappingCountBefore, linkingIdMappingRepository.count(), "Mapping count should return to baseline");
        }
    }
}
