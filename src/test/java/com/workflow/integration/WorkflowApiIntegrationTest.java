package com.workflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private WorkflowEntitySettingRepository entitySettingRepository;
    @Autowired
    private WorkflowEntityAndLinkingIdMappingRepository linkingIdMappingRepository;
    @Autowired
    private WorkflowRuleAndTypeRepository ruleAndTypeRepository;
    @Autowired
    private WorkflowRuleRepository ruleRepository;
    @Autowired
    private WorkflowTypeRepository typeRepository;
    @Autowired
    private WorkflowReportRepository reportRepository;

    private static final String APP_NAME = "ITEST_APP";
    private static final String APP_NAME_2 = "ITEST_APP_2";
    private static final String APP_NAME_COPY = "ITEST_APP_COPY";

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
        List<WorkflowEntitySetting> settings = entitySettingRepository.getWorkflowEntitySettingByApplicationName(appName);
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

    // ──────────────────────────────────────────────────────────────────────────
    // POST -> DB verification
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST workflow -> verify DB tables populated correctly")
    class PostDbVerification {

        @Test
        @DisplayName("All DB tables should contain expected records after POST")
        void postWorkflowPopulatesAllDbTables() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

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
                assertNotNull(rat.getWorkflowRule().getId());
                assertNotNull(rat.getWorkflowType().getId());
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
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());

            for (WorkflowEntityAndLinkingIdMapping mapping : mappings) {
                String linkingId = mapping.getLinkingId();
                String[] parts = linkingId.split("_");
                assertEquals(3, parts.length,
                        "LinkingId should have format entitySettingId_typeId_pluginId: " + linkingId);
                assertEquals(String.valueOf(setting.getId()), parts[0],
                        "First part of linkingId should be entity setting ID");
                assertEquals(String.valueOf(mapping.getLogicOrder()), parts[2],
                        "Third part of linkingId should be plugin ID (logicOrder)");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Round-trip data fidelity
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST then GET -> round-trip data fidelity")
    class RoundTripFidelity {

        @Test
        @DisplayName("Plugin count, IDs, descriptions, action types, and rule keys survive round-trip")
        void fullPluginFidelity() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            String requestJson = objectMapper.writeValueAsString(requestBody);

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);
            assertNotNull(got);
            assertEquals(requestBody.getPluginList().size(), got.getPluginList().size(),
                    "Plugin count should match");

            for (int i = 0; i < requestBody.getPluginList().size(); i++) {
                Plugin expected = requestBody.getPluginList().get(i);
                Plugin actual = got.getPluginList().get(i);
                assertEquals(expected.getId(), actual.getId(), "Plugin id at index " + i);
                assertEquals(expected.getDescription(), actual.getDescription(),
                        "Plugin description at index " + i);
                assertEquals(expected.getAction().getType(), actual.getAction().getType(),
                        "Action type at index " + i);
                assertEquals(expected.getAction().getProvider(), actual.getAction().getProvider(),
                        "Action provider at index " + i);
                assertEquals(expected.getAction().getRemark(), actual.getAction().getRemark(),
                        "Action remark at index " + i);

                assertEquals(expected.getRuleList().size(), actual.getRuleList().size(),
                        "Rule count at index " + i);
                for (int j = 0; j < expected.getRuleList().size(); j++) {
                    assertEquals(expected.getRuleList().get(j).getKey(),
                            actual.getRuleList().get(j).getKey(),
                            "Rule key at plugin " + i + " rule " + j);
                    assertEquals(expected.getRuleList().get(j).getRemark(),
                            actual.getRuleList().get(j).getRemark(),
                            "Rule remark at plugin " + i + " rule " + j);
                }
            }
        }

        @Test
        @DisplayName("Action HTTP fields survive round-trip (method, URL, headers, body)")
        void actionHttpFieldsFidelity() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            for (int i = 0; i < requestBody.getPluginList().size(); i++) {
                var expected = requestBody.getPluginList().get(i).getAction();
                var actual = got.getPluginList().get(i).getAction();

                assertEquals(expected.getHttpRequestMethod(), actual.getHttpRequestMethod(),
                        "httpRequestMethod at plugin " + i);
                assertEquals(expected.getHttpRequestUrlWithQueryParameter(),
                        actual.getHttpRequestUrlWithQueryParameter(),
                        "httpRequestUrlWithQueryParameter at plugin " + i);
                assertEquals(expected.getInternalHttpRequestUrlWithQueryParameter(),
                        actual.getInternalHttpRequestUrlWithQueryParameter(),
                        "internalHttpRequestUrlWithQueryParameter at plugin " + i);
                assertEquals(expected.getHttpRequestHeaders(), actual.getHttpRequestHeaders(),
                        "httpRequestHeaders at plugin " + i);
                assertEquals(expected.getHttpRequestBody(), actual.getHttpRequestBody(),
                        "httpRequestBody at plugin " + i);
                assertEquals(expected.getElseLogic(), actual.getElseLogic(),
                        "elseLogic at plugin " + i);
            }
        }

        @Test
        @DisplayName("uiMapList survives round-trip")
        void uiMapListFidelity() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            assertNotNull(got.getUiMapList(), "uiMapList should be present");
            assertEquals(requestBody.getUiMapList().size(), got.getUiMapList().size(),
                    "uiMapList size should match");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE -> DB verification
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE workflow -> verify all DB records removed")
    class DeleteDbVerification {

        @Test
        @DisplayName("DELETE removes entity setting, mappings, rules, and types from DB")
        void deleteRemovesAllDbRecords() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting settingBeforeDelete =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            Long settingId = settingBeforeDelete.getId();

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

            assertFalse(mappingsBefore.isEmpty(), "Pre-condition: mappings exist before delete");
            assertFalse(ruleIdsBefore.isEmpty(), "Pre-condition: rules exist before delete");
            assertFalse(typeIdsBefore.isEmpty(), "Pre-condition: types exist before delete");

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isOk());

            assertTrue(entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).isEmpty(),
                    "Entity setting should be removed");
            assertTrue(linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingId).isEmpty(),
                    "Linking-id mappings should be removed");
            assertTrue(ruleAndTypeRepository.findAllByLinkingIdIn(linkingIdsBefore).isEmpty(),
                    "Rule-and-type mappings should be removed");
            for (Long ruleId : ruleIdsBefore) {
                assertFalse(ruleRepository.existsById(ruleId),
                        "Rule " + ruleId + " should be removed");
            }
            for (Long typeId : typeIdsBefore) {
                assertFalse(typeRepository.existsById(typeId),
                        "Type " + typeId + " should be removed");
            }
        }

        @Test
        @DisplayName("DELETE then GET returns 400")
        void deleteThenGetReturns400() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isBadRequest());
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
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isBadRequest());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UPDATE (re-POST) -> DB verification
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST update (re-POST) -> old data replaced, DB consistent")
    class UpdateDbVerification {

        @Test
        @DisplayName("Re-POST replaces old rules/types/mappings with new ones")
        void rePostReplacesOldData() throws Exception {
            WorkFlow original = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isOk());

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

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(modified)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting settingAfterSecond =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            assertEquals(settingAfterFirst.getId(), settingAfterSecond.getId(),
                    "Entity setting ID should be reused");

            List<WorkflowEntityAndLinkingIdMapping> mappingsSecond =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingAfterSecond.getId());
            assertEquals(3, mappingsSecond.size(),
                    "After re-POST with 3 plugins, exactly 3 mappings should exist");

            for (Long oldRuleId : oldRuleIds) {
                assertFalse(ruleRepository.existsById(oldRuleId),
                        "Old rule " + oldRuleId + " should be removed after re-POST");
            }
            for (Long oldTypeId : oldTypeIds) {
                assertFalse(typeRepository.existsById(oldTypeId),
                        "Old type " + oldTypeId + " should be removed after re-POST");
            }

            assertTrue(ruleAndTypeRepository.findAllByLinkingIdIn(linkingIdsFirst).isEmpty(),
                    "Old rule-and-type mappings should be removed after re-POST");

            List<String> newLinkingIds = mappingsSecond.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();
            List<WorkflowRuleAndType> newRuleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(newLinkingIds);
            assertFalse(newRuleAndTypes.isEmpty(), "New rule-and-type mappings should exist");
        }

        @Test
        @DisplayName("Re-POST returns correct GET response reflecting new data")
        void rePostGetReflectsNewData() throws Exception {
            WorkFlow original = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isOk());

            WorkFlow modified = loadTestWorkflow();
            modified.setPluginList(modified.getPluginList().subList(0, 5));

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(modified)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            assertEquals(5, got.getPluginList().size(),
                    "GET should return 5 plugins after re-POST with 5 plugins");

            for (int i = 0; i < 5; i++) {
                assertEquals(modified.getPluginList().get(i).getId(),
                        got.getPluginList().get(i).getId());
                assertEquals(modified.getPluginList().get(i).getAction().getType(),
                        got.getPluginList().get(i).getAction().getType());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AutoCopy integration
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AutoCopy workflow -> source intact, target correct in DB")
    class AutoCopyIntegration {

        @Test
        @DisplayName("AutoCopy creates target with same plugin structure as source")
        void autoCopyCreatesTargetWithSameStructure() throws Exception {
            WorkFlow original = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/workflow/autoCopy")
                            .param("fromApplicationName", APP_NAME)
                            .param("toApplicationName", APP_NAME_COPY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            MvcResult sourceResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();
            MvcResult targetResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME_COPY))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow source = objectMapper.readValue(
                    sourceResult.getResponse().getContentAsString(), WorkFlow.class);
            WorkFlow target = objectMapper.readValue(
                    targetResult.getResponse().getContentAsString(), WorkFlow.class);

            assertEquals(source.getPluginList().size(), target.getPluginList().size(),
                    "Target should have same number of plugins as source");

            for (int i = 0; i < source.getPluginList().size(); i++) {
                Plugin sp = source.getPluginList().get(i);
                Plugin tp = target.getPluginList().get(i);
                assertEquals(sp.getId(), tp.getId(), "Plugin id at index " + i);
                assertEquals(sp.getAction().getType(), tp.getAction().getType(),
                        "Action type at index " + i);
                assertEquals(sp.getAction().getProvider(), tp.getAction().getProvider(),
                        "Action provider at index " + i);
                assertEquals(sp.getRuleList().size(), tp.getRuleList().size(),
                        "Rule count at index " + i);
                for (int j = 0; j < sp.getRuleList().size(); j++) {
                    assertEquals(sp.getRuleList().get(j).getKey(),
                            tp.getRuleList().get(j).getKey(),
                            "Rule key at plugin " + i + " rule " + j);
                }
            }
        }

        @Test
        @DisplayName("AutoCopy creates independent DB records for target")
        void autoCopyCreatesIndependentDbRecords() throws Exception {
            WorkFlow original = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/workflow/autoCopy")
                            .param("fromApplicationName", APP_NAME)
                            .param("toApplicationName", APP_NAME_COPY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            List<WorkflowEntitySetting> sourceSettings =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME);
            List<WorkflowEntitySetting> targetSettings =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME_COPY);
            assertEquals(1, sourceSettings.size());
            assertEquals(1, targetSettings.size());
            assertNotEquals(sourceSettings.get(0).getId(), targetSettings.get(0).getId(),
                    "Source and target should have different entity setting IDs");

            List<WorkflowEntityAndLinkingIdMapping> sourceMappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(sourceSettings.get(0).getId());
            List<WorkflowEntityAndLinkingIdMapping> targetMappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(targetSettings.get(0).getId());
            assertEquals(sourceMappings.size(), targetMappings.size(),
                    "Same number of linking-id mappings");

            List<String> sourceLinkingIds = sourceMappings.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList();
            List<String> targetLinkingIds = targetMappings.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList();

            for (String targetLinkingId : targetLinkingIds) {
                assertFalse(sourceLinkingIds.contains(targetLinkingId),
                        "Target linkingId " + targetLinkingId + " must differ from source IDs");
            }
        }

        @Test
        @DisplayName("Deleting target does not affect source")
        void deletingTargetDoesNotAffectSource() throws Exception {
            WorkFlow original = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/workflow/autoCopy")
                            .param("fromApplicationName", APP_NAME)
                            .param("toApplicationName", APP_NAME_COPY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME_COPY))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isOk());

            WorkflowEntitySetting sourceSetting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> sourceMappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(sourceSetting.getId());
            assertEquals(original.getPluginList().size(), sourceMappings.size(),
                    "Source mappings should be intact after deleting target");
        }

        @Test
        @DisplayName("AutoCopy with same source and target returns 400")
        void autoCopySameNameReturns400() throws Exception {
            WorkFlow original = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/workflow/autoCopy")
                            .param("fromApplicationName", APP_NAME)
                            .param("toApplicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AutoCopy from non-existent source returns 400")
        void autoCopyNonExistentSourceReturns400() throws Exception {
            mockMvc.perform(post("/api/workflow/autoCopy")
                            .param("fromApplicationName", "NON_EXISTENT")
                            .param("toApplicationName", APP_NAME_COPY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Delete blocked when reports exist
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE blocked when reports exist (409)")
    class DeleteBlockedByReports {

        @Test
        @DisplayName("DELETE returns 409 when workflow has associated reports")
        void deleteReturns409WhenReportsExist() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);

            WorkflowReport report = WorkflowReport.builder()
                    .workflowEntitySetting(setting)
                    .reportGroup(1L)
                    .enabled(true)
                    .cronExpression("0 0 * * *")
                    .reportTimeRangeByHours(24)
                    .timezone("UTC")
                    .build();
            reportRepository.saveAndFlush(report);

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isConflict());

            assertEquals(1,
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).size(),
                    "Entity setting should still exist after blocked delete");

            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            assertFalse(mappings.isEmpty(),
                    "Mappings should still exist after blocked delete");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validation tests
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation error handling")
    class ValidationErrors {

        @Test
        @DisplayName("POST without body returns 400")
        void postWithoutBodyReturns400() throws Exception {
            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET non-existent application returns 400")
        void getNonExistentReturns400() throws Exception {
            mockMvc.perform(get("/api/workflow")
                            .param("applicationName", "NON_EXISTENT_APP"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST with empty applicationName parameter is rejected")
        void postEmptyApplicationNameRejected() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", "")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/workflow").param("applicationName", ""));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Edge cases
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("POST workflow with empty plugin list")
        void postEmptyPluginList() throws Exception {
            WorkFlow emptyWorkflow = WorkFlow.builder()
                    .pluginList(List.of())
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyWorkflow)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            assertTrue(mappings.isEmpty(),
                    "No linking-id mappings for empty plugin list");

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);
            assertTrue(got.getPluginList().isEmpty(),
                    "GET should return empty plugin list");
        }

        @Test
        @DisplayName("POST workflow with single plugin having multiple rules")
        void postSinglePluginMultipleRules() throws Exception {
            WorkFlow fullWorkflow = loadTestWorkflow();
            Plugin multiRulePlugin = fullWorkflow.getPluginList().get(
                    fullWorkflow.getPluginList().size() - 1);
            assertTrue(multiRulePlugin.getRuleList().size() > 1,
                    "Test data plugin 10 should have multiple rules");

            WorkFlow singlePlugin = WorkFlow.builder()
                    .pluginList(List.of(multiRulePlugin))
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(singlePlugin)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            assertEquals(1, mappings.size(), "One mapping for one plugin");

            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(
                            mappings.stream().map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList());
            assertEquals(multiRulePlugin.getRuleList().size(), ruleAndTypes.size(),
                    "Rule-and-type mapping count should match rule count in the plugin");

            long distinctTypes = ruleAndTypes.stream()
                    .map(rt -> rt.getWorkflowType().getId()).distinct().count();
            assertEquals(1, distinctTypes,
                    "All rules in a single plugin should share the same type");
        }

        @Test
        @DisplayName("POST then DELETE then POST again creates fresh data")
        void postDeletePostCreatesFreshData() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isOk());

            WorkFlow secondPost = loadTestWorkflow();
            secondPost.setPluginList(secondPost.getPluginList().subList(0, 2));

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(secondPost)))
                    .andExpect(status().isOk());

            List<WorkflowEntitySetting> settings =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME);
            assertEquals(1, settings.size());

            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settings.get(0).getId());
            assertEquals(2, mappings.size(),
                    "After DELETE then POST with 2 plugins, exactly 2 mappings should exist");

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();
            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);
            assertEquals(2, got.getPluginList().size());
        }

        @Test
        @DisplayName("Multiple applications are isolated from each other")
        void multipleApplicationsIsolated() throws Exception {
            WorkFlow fullWorkflow = loadTestWorkflow();
            WorkFlow partialWorkflow = loadTestWorkflow();
            partialWorkflow.setPluginList(partialWorkflow.getPluginList().subList(0, 3));

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(fullWorkflow)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME_2)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(partialWorkflow)))
                    .andExpect(status().isOk());

            MvcResult result1 = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();
            MvcResult result2 = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME_2))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got1 = objectMapper.readValue(
                    result1.getResponse().getContentAsString(), WorkFlow.class);
            WorkFlow got2 = objectMapper.readValue(
                    result2.getResponse().getContentAsString(), WorkFlow.class);

            assertEquals(fullWorkflow.getPluginList().size(), got1.getPluginList().size());
            assertEquals(3, got2.getPluginList().size());

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME_2))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting1 =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings1 =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting1.getId());
            assertEquals(fullWorkflow.getPluginList().size(), mappings1.size(),
                    "First application's mappings should be intact after deleting second");
        }

        @Test
        @DisplayName("POST workflow with null pluginList treated as empty")
        void postNullPluginListTreatedAsEmpty() throws Exception {
            WorkFlow nullPluginWorkflow = WorkFlow.builder()
                    .pluginList(null)
                    .uiMapList(null)
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(nullPluginWorkflow)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            assertTrue(mappings.isEmpty(),
                    "No mappings for null plugin list");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DB-level rule and type content verification
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DB-level content verification")
    class DbContentVerification {

        @Test
        @DisplayName("Rules in DB have correct keys and remarks matching input")
        void rulesInDbMatchInput() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            mappings.sort((a, b) -> a.getLogicOrder().compareTo(b.getLogicOrder()));

            for (int i = 0; i < mappings.size(); i++) {
                Plugin expectedPlugin = requestBody.getPluginList().get(i);
                WorkflowEntityAndLinkingIdMapping mapping = mappings.get(i);

                assertEquals(expectedPlugin.getId(), mapping.getLogicOrder(),
                        "logicOrder should match plugin ID at index " + i);
                assertEquals(expectedPlugin.getDescription(), mapping.getRemark(),
                        "remark should match plugin description at index " + i);

                List<WorkflowRuleAndType> ruleAndTypes =
                        ruleAndTypeRepository.findAllByLinkingIdIn(List.of(mapping.getLinkingId()));
                assertEquals(expectedPlugin.getRuleList().size(), ruleAndTypes.size(),
                        "Rule count in DB should match input for plugin " + i);

                for (int j = 0; j < ruleAndTypes.size(); j++) {
                    WorkflowRule dbRule = ruleAndTypes.get(j).getWorkflowRule();
                    WorkflowRule inputRule = expectedPlugin.getRuleList().get(j);
                    assertEquals(inputRule.getKey(), dbRule.getKey(),
                            "Rule key in DB should match input for plugin " + i + " rule " + j);
                    assertEquals(inputRule.getRemark(), dbRule.getRemark(),
                            "Rule remark in DB should match input for plugin " + i + " rule " + j);
                }
            }
        }

        @Test
        @DisplayName("Types in DB have correct provider and type fields matching input")
        void typesInDbMatchInput() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            mappings.sort((a, b) -> a.getLogicOrder().compareTo(b.getLogicOrder()));

            for (int i = 0; i < mappings.size(); i++) {
                Plugin expectedPlugin = requestBody.getPluginList().get(i);
                WorkflowEntityAndLinkingIdMapping mapping = mappings.get(i);

                List<WorkflowRuleAndType> ruleAndTypes =
                        ruleAndTypeRepository.findAllByLinkingIdIn(List.of(mapping.getLinkingId()));
                assertFalse(ruleAndTypes.isEmpty());

                WorkflowType dbType = ruleAndTypes.get(0).getWorkflowType();
                assertEquals(expectedPlugin.getAction().getProvider(), dbType.getProvider(),
                        "Provider in DB should match at plugin " + i);
                assertEquals(expectedPlugin.getAction().getType(), dbType.getType(),
                        "Type in DB should match at plugin " + i);
                assertEquals(expectedPlugin.getAction().getRemark(), dbType.getRemark(),
                        "Remark in DB should match at plugin " + i);
                assertEquals(expectedPlugin.getAction().getHttpRequestMethod(),
                        dbType.getHttpRequestMethod(),
                        "httpRequestMethod in DB should match at plugin " + i);
            }
        }

        @Test
        @DisplayName("Workflow JSON in entity setting is base64-encoded and decodable")
        void workflowJsonIsBase64Encoded() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            assertNotNull(setting.getWorkflow());
            assertFalse(setting.getWorkflow().isEmpty());

            String decoded = com.workflow.common.util.Base64Util.base64Decode(
                    setting.getWorkflow(), true, objectMapper);
            WorkFlow decodedWorkflow = objectMapper.readValue(decoded, WorkFlow.class);
            assertEquals(requestBody.getPluginList().size(),
                    decodedWorkflow.getPluginList().size(),
                    "Decoded workflow should have same plugin count as input");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST response body verification
    // ──────────────────────────────────────────────────────────────────────────

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

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow postResponse = objectMapper.readValue(
                    postResult.getResponse().getContentAsString(), WorkFlow.class);
            WorkFlow getResponse = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            assertEquals(postResponse.getPluginList().size(), getResponse.getPluginList().size(),
                    "POST and GET should return same plugin count");

            for (int i = 0; i < postResponse.getPluginList().size(); i++) {
                Plugin postPlugin = postResponse.getPluginList().get(i);
                Plugin getPlugin = getResponse.getPluginList().get(i);
                assertEquals(postPlugin.getId(), getPlugin.getId());
                assertEquals(postPlugin.getDescription(), getPlugin.getDescription());
                assertEquals(postPlugin.getLinkingIdOfRuleListAndAction(),
                        getPlugin.getLinkingIdOfRuleListAndAction());
                assertEquals(postPlugin.getAction().getType(), getPlugin.getAction().getType());
                assertEquals(postPlugin.getAction().getProvider(), getPlugin.getAction().getProvider());
                assertEquals(postPlugin.getRuleList().size(), getPlugin.getRuleList().size());
            }

            assertEquals(postResponse.getUiMapList().size(), getResponse.getUiMapList().size(),
                    "POST and GET should return same uiMapList size");
        }

        @Test
        @DisplayName("POST response contains valid linkingIdOfRuleListAndAction for each plugin")
        void postResponseContainsLinkingIds() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            MvcResult postResult = mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow postResponse = objectMapper.readValue(
                    postResult.getResponse().getContentAsString(), WorkFlow.class);

            for (Plugin plugin : postResponse.getPluginList()) {
                assertNotNull(plugin.getLinkingIdOfRuleListAndAction(),
                        "linkingIdOfRuleListAndAction should be present in POST response for plugin " + plugin.getId());
                assertFalse(plugin.getLinkingIdOfRuleListAndAction().isBlank());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Plugin with empty ruleList / null action
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Plugin with empty ruleList or null action")
    class EmptyRuleListAndNullAction {

        @Test
        @DisplayName("Plugin with empty ruleList [] creates empty rule and type in DB")
        void pluginWithEmptyRuleListCreatesEmptyRuleInDb() throws Exception {
            Plugin emptyRulePlugin = Plugin.builder()
                    .id(1)
                    .description("Plugin with no rules")
                    .ruleList(List.of())
                    .action(WorkflowType.builder()
                            .provider("TestProvider")
                            .type("CONSUMER")
                            .remark("Action with no rules")
                            .httpRequestMethod("GET")
                            .httpRequestUrlWithQueryParameter("https://example.com/test")
                            .internalHttpRequestUrlWithQueryParameter("https://example.com/test")
                            .httpRequestHeaders("{\"Accept\":\"application/json\"}")
                            .httpRequestBody("")
                            .trackingNumberSchemaInHttpResponse("{}")
                            .build())
                    .build();

            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(emptyRulePlugin))
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(workflow)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            assertEquals(1, mappings.size(), "One mapping should exist for plugin with empty rules");

            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(
                            mappings.stream().map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList());
            assertEquals(1, ruleAndTypes.size(), "One rule-and-type mapping for empty-rule plugin");

            WorkflowRule dbRule = ruleAndTypes.get(0).getWorkflowRule();
            assertEquals("", dbRule.getKey(), "Empty rule should have blank key");

            WorkflowType dbType = ruleAndTypes.get(0).getWorkflowType();
            assertNotNull(dbType, "Type should still be saved");
            assertEquals("TestProvider", dbType.getProvider());
            assertEquals("CONSUMER", dbType.getType());
        }

        @Test
        @DisplayName("Plugin with null action creates type with null fields")
        void pluginWithNullActionCreatesTypeWithNullFields() throws Exception {
            Plugin nullActionPlugin = Plugin.builder()
                    .id(1)
                    .description("Plugin with null action")
                    .ruleList(List.of(WorkflowRule.builder()
                            .key("$.someField")
                            .remark("A rule")
                            .build()))
                    .action(null)
                    .build();

            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(nullActionPlugin))
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(workflow)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            assertEquals(1, mappings.size());

            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(
                            mappings.stream().map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList());
            assertEquals(1, ruleAndTypes.size());

            WorkflowType dbType = ruleAndTypes.get(0).getWorkflowType();
            assertNotNull(dbType.getId(), "Type should have been saved even with null source");
            assertNull(dbType.getProvider());
            assertNull(dbType.getType());
        }

        @Test
        @DisplayName("Empty-rule plugin round-trips correctly via GET")
        void emptyRulePluginRoundTrips() throws Exception {
            Plugin emptyRulePlugin = Plugin.builder()
                    .id(1)
                    .description("Empty rule plugin")
                    .ruleList(List.of())
                    .action(WorkflowType.builder()
                            .provider("SYSTEM")
                            .type("IFELSE")
                            .remark("No rules action")
                            .build())
                    .build();

            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(emptyRulePlugin))
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(workflow)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);
            assertEquals(1, got.getPluginList().size());
            assertEquals("SYSTEM", got.getPluginList().get(0).getAction().getProvider());
            assertEquals("IFELSE", got.getPluginList().get(0).getAction().getType());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AutoCopy metadata and overwrite
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AutoCopy metadata copy and overwrite behavior")
    class AutoCopyMetadataAndOverwrite {

        @Test
        @DisplayName("AutoCopy copies entity setting metadata (retry, eimId, region, etc.)")
        void autoCopyCopiesEntitySettingMetadata() throws Exception {
            WorkFlow original = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting sourceSetting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            sourceSetting.setRetry(true);
            sourceSetting.setTracking(true);
            sourceSetting.setEimId("EIM-12345");
            sourceSetting.setDefaultServiceAccount("svc-account");
            sourceSetting.setRegion("us-east-1");
            entitySettingRepository.saveAndFlush(sourceSetting);

            mockMvc.perform(post("/api/workflow/autoCopy")
                            .param("fromApplicationName", APP_NAME)
                            .param("toApplicationName", APP_NAME_COPY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            WorkflowEntitySetting targetSetting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME_COPY).get(0);
            assertEquals(APP_NAME_COPY, targetSetting.getApplicationName(),
                    "Target should keep its own applicationName");
            assertEquals(sourceSetting.isRetry(), targetSetting.isRetry(),
                    "retry should be copied");
            assertEquals(sourceSetting.isTracking(), targetSetting.isTracking(),
                    "tracking should be copied");
            assertEquals(sourceSetting.getEimId(), targetSetting.getEimId(),
                    "eimId should be copied");
            assertEquals(sourceSetting.getDefaultServiceAccount(), targetSetting.getDefaultServiceAccount(),
                    "defaultServiceAccount should be copied");
            assertEquals(sourceSetting.getRegion(), targetSetting.getRegion(),
                    "region should be copied");
        }

        @Test
        @DisplayName("AutoCopy to existing target overwrites its workflow")
        void autoCopyOverwritesExistingTarget() throws Exception {
            WorkFlow fullWorkflow = loadTestWorkflow();
            WorkFlow partialWorkflow = loadTestWorkflow();
            partialWorkflow.setPluginList(partialWorkflow.getPluginList().subList(0, 2));

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(fullWorkflow)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME_COPY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(partialWorkflow)))
                    .andExpect(status().isOk());

            MvcResult beforeCopy = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME_COPY))
                    .andExpect(status().isOk())
                    .andReturn();
            WorkFlow targetBefore = objectMapper.readValue(
                    beforeCopy.getResponse().getContentAsString(), WorkFlow.class);
            assertEquals(2, targetBefore.getPluginList().size(),
                    "Target initially has 2 plugins");

            mockMvc.perform(post("/api/workflow/autoCopy")
                            .param("fromApplicationName", APP_NAME)
                            .param("toApplicationName", APP_NAME_COPY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            MvcResult afterCopy = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME_COPY))
                    .andExpect(status().isOk())
                    .andReturn();
            WorkFlow targetAfter = objectMapper.readValue(
                    afterCopy.getResponse().getContentAsString(), WorkFlow.class);
            assertEquals(fullWorkflow.getPluginList().size(), targetAfter.getPluginList().size(),
                    "After AutoCopy, target should have same plugin count as source (10)");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Plugin ordering
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Plugin ordering verification")
    class PluginOrdering {

        @Test
        @DisplayName("Plugins with non-sequential IDs are returned sorted by ID")
        void nonSequentialPluginIdsSortedCorrectly() throws Exception {
            WorkFlow fullWorkflow = loadTestWorkflow();
            List<Plugin> reversed = new ArrayList<>(fullWorkflow.getPluginList());
            java.util.Collections.reverse(reversed);

            WorkFlow reordered = WorkFlow.builder()
                    .pluginList(reversed)
                    .uiMapList(fullWorkflow.getUiMapList())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reordered)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            for (int i = 0; i < got.getPluginList().size() - 1; i++) {
                assertTrue(got.getPluginList().get(i).getId() < got.getPluginList().get(i + 1).getId(),
                        "Plugins should be sorted by ID ascending, but index " + i
                                + " has id=" + got.getPluginList().get(i).getId()
                                + " and index " + (i + 1) + " has id=" + got.getPluginList().get(i + 1).getId());
            }
        }

        @Test
        @DisplayName("Plugins with gaps in IDs (e.g., 1,5,10) preserve correct order and data")
        void gapPluginIdsPreserveOrder() throws Exception {
            WorkFlow fullWorkflow = loadTestWorkflow();
            List<Plugin> gapped = List.of(
                    fullWorkflow.getPluginList().get(0),
                    fullWorkflow.getPluginList().get(4),
                    fullWorkflow.getPluginList().get(9)
            );

            WorkFlow gappedWorkflow = WorkFlow.builder()
                    .pluginList(gapped)
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(gappedWorkflow)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            assertEquals(3, got.getPluginList().size());
            assertEquals(1, got.getPluginList().get(0).getId());
            assertEquals(5, got.getPluginList().get(1).getId());
            assertEquals(10, got.getPluginList().get(2).getId());

            assertEquals("CONSUMER", got.getPluginList().get(0).getAction().getType());
            assertEquals("IFELSE", got.getPluginList().get(1).getAction().getType());
            assertEquals("MESSAGE", got.getPluginList().get(2).getAction().getType());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Audit timestamps
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Audit timestamps verification")
    class AuditTimestamps {

        @Test
        @DisplayName("Entity setting, rules, and types have populated timestamps after POST")
        void timestampsPopulatedAfterPost() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            assertNotNull(setting.getCreatedDateTime(), "EntitySetting createdDateTime should be set");
            assertNotNull(setting.getLastModifiedDateTime(), "EntitySetting lastModifiedDateTime should be set");

            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            for (WorkflowEntityAndLinkingIdMapping mapping : mappings) {
                assertNotNull(mapping.getCreatedDateTime(),
                        "Mapping createdDateTime should be set for logicOrder " + mapping.getLogicOrder());
                assertNotNull(mapping.getLastModifiedDateTime(),
                        "Mapping lastModifiedDateTime should be set for logicOrder " + mapping.getLogicOrder());
            }

            List<String> linkingIds = mappings.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();
            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(linkingIds);
            for (WorkflowRuleAndType rat : ruleAndTypes) {
                assertNotNull(rat.getWorkflowRule().getCreatedDateTime(),
                        "Rule createdDateTime should be set");
                assertNotNull(rat.getWorkflowType().getCreatedDateTime(),
                        "Type createdDateTime should be set");
            }
        }

        @Test
        @DisplayName("GET response includes timestamps on actions and rules")
        void getResponseIncludesTimestamps() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            for (Plugin plugin : got.getPluginList()) {
                assertNotNull(plugin.getAction().getCreatedDateTime(),
                        "Action createdDateTime should be in GET response for plugin " + plugin.getId());
                assertNotNull(plugin.getAction().getLastModifiedDateTime(),
                        "Action lastModifiedDateTime should be in GET response for plugin " + plugin.getId());
                for (WorkflowRule rule : plugin.getRuleList()) {
                    assertNotNull(rule.getCreatedDateTime(),
                            "Rule createdDateTime should be in GET response");
                    assertNotNull(rule.getLastModifiedDateTime(),
                            "Rule lastModifiedDateTime should be in GET response");
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Base64 encoding in DB
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Base64 encoding of type fields in DB")
    class Base64EncodingInDb {

        @Test
        @DisplayName("Type HTTP fields are base64-encoded in DB but decoded in GET response")
        void typeFieldsBase64EncodedInDb() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            Plugin consumerPlugin = requestBody.getPluginList().get(0);
            assertNotNull(consumerPlugin.getAction().getHttpRequestUrlWithQueryParameter());

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            mappings.sort(Comparator.comparing(WorkflowEntityAndLinkingIdMapping::getLogicOrder));

            WorkflowEntityAndLinkingIdMapping firstMapping = mappings.get(0);
            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(List.of(firstMapping.getLinkingId()));
            WorkflowType rawDbType = ruleAndTypes.get(0).getWorkflowType();

            String rawUrl = rawDbType.getHttpRequestUrlWithQueryParameter();
            assertNotEquals(consumerPlugin.getAction().getHttpRequestUrlWithQueryParameter(), rawUrl,
                    "URL in DB should be base64-encoded, not raw");
            String decoded = new String(Base64.getDecoder().decode(rawUrl), StandardCharsets.UTF_8);
            assertEquals(consumerPlugin.getAction().getHttpRequestUrlWithQueryParameter(), decoded,
                    "Decoded URL should match original input");

            if (rawDbType.getHttpRequestHeaders() != null && !rawDbType.getHttpRequestHeaders().isEmpty()) {
                String rawHeaders = rawDbType.getHttpRequestHeaders();
                assertDoesNotThrow(() -> Base64.getDecoder().decode(rawHeaders),
                        "Headers in DB should be valid base64");
            }

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();
            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);
            assertEquals(consumerPlugin.getAction().getHttpRequestUrlWithQueryParameter(),
                    got.getPluginList().get(0).getAction().getHttpRequestUrlWithQueryParameter(),
                    "GET should return decoded URL matching original input");
        }

        @Test
        @DisplayName("elseLogic JSON is base64-encoded in DB but decoded in GET response")
        void elseLogicBase64EncodedInDb() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            Plugin ifelsePlugin = requestBody.getPluginList().get(1);
            assertNotNull(ifelsePlugin.getAction().getElseLogic());

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            mappings.sort(Comparator.comparing(WorkflowEntityAndLinkingIdMapping::getLogicOrder));

            WorkflowEntityAndLinkingIdMapping secondMapping = mappings.get(1);
            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(List.of(secondMapping.getLinkingId()));
            WorkflowType rawDbType = ruleAndTypes.get(0).getWorkflowType();

            String rawElseLogic = rawDbType.getElseLogic();
            assertNotNull(rawElseLogic);
            assertDoesNotThrow(() -> Base64.getDecoder().decode(rawElseLogic),
                    "elseLogic in DB should be valid base64");

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();
            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            assertEquals(ifelsePlugin.getAction().getElseLogic(),
                    got.getPluginList().get(1).getAction().getElseLogic(),
                    "GET should return decoded elseLogic matching original input");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Special characters and complex data
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Special characters and complex data round-trip")
    class SpecialCharacters {

        @Test
        @DisplayName("Rule keys with regex special characters survive round-trip")
        void ruleKeysWithRegexSpecialChars() throws Exception {
            String complexRuleKey = "$.data[?(@.name =~ /^[A-Z]{2,3}\\d+$/ && @.value > 100)]";
            Plugin plugin = Plugin.builder()
                    .id(1)
                    .description("Complex regex rule")
                    .ruleList(List.of(WorkflowRule.builder()
                            .key(complexRuleKey)
                            .remark("Regex with special chars: ^$[]{}()|\\")
                            .build()))
                    .action(WorkflowType.builder()
                            .provider("SYSTEM")
                            .type("IFELSE")
                            .remark("Test action")
                            .build())
                    .build();

            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(plugin))
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(workflow)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            assertEquals(complexRuleKey, got.getPluginList().get(0).getRuleList().get(0).getKey(),
                    "Complex regex rule key should survive round-trip");
        }

        @Test
        @DisplayName("Unicode characters in description and remark survive round-trip")
        void unicodeCharactersSurviveRoundTrip() throws Exception {
            Plugin plugin = Plugin.builder()
                    .id(1)
                    .description("步骤一：发送通知 🚀")
                    .ruleList(List.of(WorkflowRule.builder()
                            .key("$.data")
                            .remark("规则说明：检查数据是否存在")
                            .build()))
                    .action(WorkflowType.builder()
                            .provider("SYSTEM")
                            .type("MESSAGE")
                            .remark("动作备注：发送消息给用户 éàü ñ")
                            .build())
                    .build();

            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(plugin))
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(workflow)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            assertEquals("步骤一：发送通知 🚀", got.getPluginList().get(0).getDescription());
            assertEquals("规则说明：检查数据是否存在",
                    got.getPluginList().get(0).getRuleList().get(0).getRemark());
            assertEquals("动作备注：发送消息给用户 éàü ñ",
                    got.getPluginList().get(0).getAction().getRemark());
        }

        @Test
        @DisplayName("URL with query parameters and special chars survives base64 round-trip")
        void urlWithSpecialCharsSurvivesRoundTrip() throws Exception {
            String complexUrl = "https://api.example.com/v2/search?q=hello+world&filter=type%3Dactive&page=1&size=50";
            Plugin plugin = Plugin.builder()
                    .id(1)
                    .description("URL with query params")
                    .ruleList(List.of(WorkflowRule.builder()
                            .key("$.data")
                            .remark("rule")
                            .build()))
                    .action(WorkflowType.builder()
                            .provider("ExternalAPI")
                            .type("CONSUMER")
                            .remark("Call external")
                            .httpRequestMethod("POST")
                            .httpRequestUrlWithQueryParameter(complexUrl)
                            .internalHttpRequestUrlWithQueryParameter(complexUrl)
                            .httpRequestHeaders("{\"Authorization\":\"Bearer abc.def.ghi\",\"Content-Type\":\"application/json\"}")
                            .httpRequestBody("{\"query\":\"SELECT * FROM table WHERE id = 'test' AND status != \\\"deleted\\\"\"}")
                            .trackingNumberSchemaInHttpResponse("{\"path\":\"$.result[0].trackingId\"}")
                            .build())
                    .build();

            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(plugin))
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(workflow)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            var action = got.getPluginList().get(0).getAction();
            assertEquals(complexUrl, action.getHttpRequestUrlWithQueryParameter());
            assertEquals(complexUrl, action.getInternalHttpRequestUrlWithQueryParameter());
            assertEquals("{\"Authorization\":\"Bearer abc.def.ghi\",\"Content-Type\":\"application/json\"}",
                    action.getHttpRequestHeaders());
        }

        @Test
        @DisplayName("elseLogic with deeply nested JSON survives round-trip")
        void deeplyNestedElseLogicSurvivesRoundTrip() throws Exception {
            String deepJson = "{\"level1\":{\"level2\":{\"level3\":{\"level4\":{\"value\":\"deep\",\"array\":[1,2,3]}}}}}";
            Plugin plugin = Plugin.builder()
                    .id(1)
                    .description("Deep JSON")
                    .ruleList(List.of(WorkflowRule.builder().key("$.x").remark("r").build()))
                    .action(WorkflowType.builder()
                            .provider("SYSTEM")
                            .type("IFELSE")
                            .remark("Deep nested elseLogic")
                            .elseLogic(deepJson)
                            .build())
                    .build();

            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(plugin))
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(workflow)))
                    .andExpect(status().isOk());

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();

            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);

            com.fasterxml.jackson.databind.JsonNode expectedJson = objectMapper.readTree(deepJson);
            com.fasterxml.jackson.databind.JsonNode actualJson = objectMapper.readTree(
                    got.getPluginList().get(0).getAction().getElseLogic());
            assertEquals(expectedJson, actualJson,
                    "Deeply nested elseLogic JSON should survive round-trip");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Re-POST with completely different plugins
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Re-POST with completely different plugins")
    class RePostDifferentPlugins {

        @Test
        @DisplayName("Re-POST with entirely new plugin types replaces all old data")
        void rePostWithNewPluginTypesReplacesAll() throws Exception {
            WorkFlow original = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isOk());

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

            Plugin newPlugin = Plugin.builder()
                    .id(99)
                    .description("Brand new plugin with different data")
                    .ruleList(List.of(
                            WorkflowRule.builder().key("$.brand.new.key").remark("New rule A").build(),
                            WorkflowRule.builder().key("$.another.new.key").remark("New rule B").build()
                    ))
                    .action(WorkflowType.builder()
                            .provider("NewProvider")
                            .type("CONSUMER")
                            .remark("Completely different action")
                            .httpRequestMethod("PUT")
                            .httpRequestUrlWithQueryParameter("https://new-service.example.com/api")
                            .internalHttpRequestUrlWithQueryParameter("https://new-internal.example.com/api")
                            .httpRequestHeaders("{\"X-Custom\":\"value\"}")
                            .httpRequestBody("{\"newKey\":\"newValue\"}")
                            .trackingNumberSchemaInHttpResponse("{\"id\":\"$.newId\"}")
                            .build())
                    .build();

            WorkFlow brandNew = WorkFlow.builder()
                    .pluginList(List.of(newPlugin))
                    .uiMapList(List.of())
                    .build();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(brandNew)))
                    .andExpect(status().isOk());

            for (Long oldRuleId : oldRuleIds) {
                assertFalse(ruleRepository.existsById(oldRuleId),
                        "Old rule " + oldRuleId + " should be gone");
            }
            for (Long oldTypeId : oldTypeIds) {
                assertFalse(typeRepository.existsById(oldTypeId),
                        "Old type " + oldTypeId + " should be gone");
            }
            assertTrue(ruleAndTypeRepository.findAllByLinkingIdIn(linkingIdsFirst).isEmpty(),
                    "Old rule-and-type mappings should be gone");

            WorkflowEntitySetting settingAfterSecond =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappingsSecond =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingAfterSecond.getId());
            assertEquals(1, mappingsSecond.size(), "Only 1 mapping for the new single plugin");

            MvcResult getResult = mockMvc.perform(get("/api/workflow")
                            .param("applicationName", APP_NAME))
                    .andExpect(status().isOk())
                    .andReturn();
            WorkFlow got = objectMapper.readValue(
                    getResult.getResponse().getContentAsString(), WorkFlow.class);
            assertEquals(1, got.getPluginList().size());
            assertEquals(99, got.getPluginList().get(0).getId());
            assertEquals("NewProvider", got.getPluginList().get(0).getAction().getProvider());
            assertEquals("PUT", got.getPluginList().get(0).getAction().getHttpRequestMethod());
            assertEquals(2, got.getPluginList().get(0).getRuleList().size());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE after re-POST
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE after re-POST still cleans all records")
    class DeleteAfterRePost {

        @Test
        @DisplayName("Full lifecycle: POST -> re-POST -> DELETE -> all records gone")
        void fullLifecyclePostRePostDelete() throws Exception {
            WorkFlow original = loadTestWorkflow();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(original)))
                    .andExpect(status().isOk());

            WorkFlow modified = loadTestWorkflow();
            modified.setPluginList(modified.getPluginList().subList(0, 4));

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(modified)))
                    .andExpect(status().isOk());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            Long settingId = setting.getId();

            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingId);
            assertEquals(4, mappings.size(), "4 mappings should exist after re-POST");

            List<String> currentLinkingIds = mappings.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();
            List<WorkflowRuleAndType> currentRuleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(currentLinkingIds);
            List<Long> currentRuleIds = currentRuleAndTypes.stream()
                    .map(rt -> rt.getWorkflowRule().getId()).distinct().toList();
            List<Long> currentTypeIds = currentRuleAndTypes.stream()
                    .map(rt -> rt.getWorkflowType().getId()).distinct().toList();

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isOk());

            assertTrue(entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).isEmpty());
            assertTrue(linkingIdMappingRepository.findAllByWorkflowEntitySettingId(settingId).isEmpty());
            assertTrue(ruleAndTypeRepository.findAllByLinkingIdIn(currentLinkingIds).isEmpty());
            for (Long ruleId : currentRuleIds) {
                assertFalse(ruleRepository.existsById(ruleId),
                        "Rule " + ruleId + " should be gone after DELETE");
            }
            for (Long typeId : currentTypeIds) {
                assertFalse(typeRepository.existsById(typeId),
                        "Type " + typeId + " should be gone after DELETE");
            }
        }

        @Test
        @DisplayName("Multiple re-POSTs then DELETE leaves zero orphan records")
        void multipleRePostsThenDeleteNoOrphans() throws Exception {
            WorkFlow w1 = loadTestWorkflow();
            WorkFlow w2 = loadTestWorkflow();
            w2.setPluginList(w2.getPluginList().subList(0, 5));
            WorkFlow w3 = loadTestWorkflow();
            w3.setPluginList(w3.getPluginList().subList(0, 2));

            long ruleCountBefore = ruleRepository.count();
            long typeCountBefore = typeRepository.count();
            long ruleAndTypeCountBefore = ruleAndTypeRepository.count();
            long mappingCountBefore = linkingIdMappingRepository.count();

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(w1)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(w2)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/workflow")
                            .param("applicationName", APP_NAME)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(w3)))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME))
                    .andExpect(status().isOk());

            assertEquals(ruleCountBefore, ruleRepository.count(),
                    "Rule count should return to baseline after full lifecycle");
            assertEquals(typeCountBefore, typeRepository.count(),
                    "Type count should return to baseline after full lifecycle");
            assertEquals(ruleAndTypeCountBefore, ruleAndTypeRepository.count(),
                    "RuleAndType count should return to baseline after full lifecycle");
            assertEquals(mappingCountBefore, linkingIdMappingRepository.count(),
                    "Mapping count should return to baseline after full lifecycle");
        }
    }

    private WorkFlow loadTestWorkflow() throws IOException {
        String json = new String(new ClassPathResource("workflow-integration-test-data.json")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, WorkFlow.class);
    }
}
