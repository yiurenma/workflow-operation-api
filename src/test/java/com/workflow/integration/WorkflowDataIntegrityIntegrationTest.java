package com.workflow.integration;

import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Workflow data integrity integration tests")
class WorkflowDataIntegrityIntegrationTest extends AbstractWorkflowIntegrationTest {

    @Nested
    @DisplayName("DB-level content verification")
    class DbContentVerification {

        @Test
        @DisplayName("Rules in DB have correct keys and remarks matching input")
        void rulesInDbMatchInput() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            postWorkflow(APP_NAME, requestBody);

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            mappings.sort(Comparator.comparing(WorkflowEntityAndLinkingIdMapping::getLogicOrder));

            for (int i = 0; i < mappings.size(); i++) {
                Plugin expectedPlugin = requestBody.getPluginList().get(i);
                WorkflowEntityAndLinkingIdMapping mapping = mappings.get(i);

                assertEquals(expectedPlugin.getId(), mapping.getLogicOrder());
                assertEquals(expectedPlugin.getDescription(), mapping.getRemark());

                List<WorkflowRuleAndType> ruleAndTypes =
                        ruleAndTypeRepository.findAllByLinkingIdIn(List.of(mapping.getLinkingId()));
                assertEquals(expectedPlugin.getRuleList().size(), ruleAndTypes.size());

                for (int j = 0; j < ruleAndTypes.size(); j++) {
                    assertEquals(expectedPlugin.getRuleList().get(j).getKey(),
                            ruleAndTypes.get(j).getWorkflowRule().getKey());
                    assertEquals(expectedPlugin.getRuleList().get(j).getRemark(),
                            ruleAndTypes.get(j).getWorkflowRule().getRemark());
                }
            }
        }

        @Test
        @DisplayName("Types in DB have correct provider and type fields matching input")
        void typesInDbMatchInput() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            postWorkflow(APP_NAME, requestBody);

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            mappings.sort(Comparator.comparing(WorkflowEntityAndLinkingIdMapping::getLogicOrder));

            for (int i = 0; i < mappings.size(); i++) {
                Plugin expectedPlugin = requestBody.getPluginList().get(i);
                List<WorkflowRuleAndType> ruleAndTypes =
                        ruleAndTypeRepository.findAllByLinkingIdIn(List.of(mappings.get(i).getLinkingId()));
                assertFalse(ruleAndTypes.isEmpty());
                WorkflowType dbType = ruleAndTypes.get(0).getWorkflowType();
                assertEquals(expectedPlugin.getAction().getProvider(), dbType.getProvider());
                assertEquals(expectedPlugin.getAction().getType(), dbType.getType());
                assertEquals(expectedPlugin.getAction().getRemark(), dbType.getRemark());
                assertEquals(expectedPlugin.getAction().getHttpRequestMethod(), dbType.getHttpRequestMethod());
            }
        }

        @Test
        @DisplayName("Workflow JSON in entity setting is base64-encoded and decodable")
        void workflowJsonIsBase64Encoded() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            postWorkflow(APP_NAME, requestBody);

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            assertNotNull(setting.getWorkflow());
            assertFalse(setting.getWorkflow().isEmpty());

            String decoded = com.workflow.common.util.Base64Util.base64Decode(
                    setting.getWorkflow(), true, objectMapper);
            WorkFlow decodedWorkflow = objectMapper.readValue(decoded, WorkFlow.class);
            assertEquals(requestBody.getPluginList().size(), decodedWorkflow.getPluginList().size());
        }
    }

    @Nested
    @DisplayName("Base64 encoding of type fields in DB")
    class Base64EncodingInDb {

        @Test
        @DisplayName("Type HTTP fields are base64-encoded in DB but decoded in GET response")
        void typeFieldsBase64EncodedInDb() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            Plugin consumerPlugin = requestBody.getPluginList().get(0);
            postWorkflow(APP_NAME, requestBody);

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            mappings.sort(Comparator.comparing(WorkflowEntityAndLinkingIdMapping::getLogicOrder));

            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(List.of(mappings.get(0).getLinkingId()));
            WorkflowType rawDbType = ruleAndTypes.get(0).getWorkflowType();

            String rawUrl = rawDbType.getHttpRequestUrlWithQueryParameter();
            assertNotEquals(consumerPlugin.getAction().getHttpRequestUrlWithQueryParameter(), rawUrl,
                    "URL in DB should be base64-encoded");
            assertEquals(consumerPlugin.getAction().getHttpRequestUrlWithQueryParameter(),
                    new String(Base64.getDecoder().decode(rawUrl), StandardCharsets.UTF_8));

            if (rawDbType.getHttpRequestHeaders() != null && !rawDbType.getHttpRequestHeaders().isEmpty()) {
                assertDoesNotThrow(() -> Base64.getDecoder().decode(rawDbType.getHttpRequestHeaders()),
                        "Headers in DB should be valid base64");
            }

            WorkFlow got = getWorkflow(APP_NAME);
            assertEquals(consumerPlugin.getAction().getHttpRequestUrlWithQueryParameter(),
                    got.getPluginList().get(0).getAction().getHttpRequestUrlWithQueryParameter(),
                    "GET should return decoded URL");
        }

        @Test
        @DisplayName("elseLogic JSON is base64-encoded in DB but decoded in GET response")
        void elseLogicBase64EncodedInDb() throws Exception {
            WorkFlow requestBody = loadTestWorkflow();
            Plugin ifelsePlugin = requestBody.getPluginList().get(1);
            assertNotNull(ifelsePlugin.getAction().getElseLogic());

            postWorkflow(APP_NAME, requestBody);

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            mappings.sort(Comparator.comparing(WorkflowEntityAndLinkingIdMapping::getLogicOrder));

            List<WorkflowRuleAndType> ruleAndTypes =
                    ruleAndTypeRepository.findAllByLinkingIdIn(List.of(mappings.get(1).getLinkingId()));
            String rawElseLogic = ruleAndTypes.get(0).getWorkflowType().getElseLogic();
            assertNotNull(rawElseLogic);
            assertDoesNotThrow(() -> Base64.getDecoder().decode(rawElseLogic), "elseLogic should be valid base64");

            WorkFlow got = getWorkflow(APP_NAME);
            assertEquals(ifelsePlugin.getAction().getElseLogic(),
                    got.getPluginList().get(1).getAction().getElseLogic());
        }
    }

    @Nested
    @DisplayName("Audit timestamps verification")
    class AuditTimestamps {

        @Test
        @DisplayName("Entity setting, rules, and types have populated timestamps after POST")
        void timestampsPopulatedAfterPost() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());

            WorkflowEntitySetting setting =
                    entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
            assertNotNull(setting.getCreatedDateTime());
            assertNotNull(setting.getLastModifiedDateTime());

            List<WorkflowEntityAndLinkingIdMapping> mappings =
                    linkingIdMappingRepository.findAllByWorkflowEntitySettingId(setting.getId());
            for (WorkflowEntityAndLinkingIdMapping m : mappings) {
                assertNotNull(m.getCreatedDateTime());
                assertNotNull(m.getLastModifiedDateTime());
            }

            List<String> linkingIds = mappings.stream()
                    .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).distinct().toList();
            for (WorkflowRuleAndType rat : ruleAndTypeRepository.findAllByLinkingIdIn(linkingIds)) {
                assertNotNull(rat.getWorkflowRule().getCreatedDateTime());
                assertNotNull(rat.getWorkflowType().getCreatedDateTime());
            }
        }

        @Test
        @DisplayName("GET response includes timestamps on actions and rules")
        void getResponseIncludesTimestamps() throws Exception {
            postWorkflow(APP_NAME, loadTestWorkflow());
            WorkFlow got = getWorkflow(APP_NAME);

            for (Plugin plugin : got.getPluginList()) {
                assertNotNull(plugin.getAction().getCreatedDateTime(),
                        "Action createdDateTime for plugin " + plugin.getId());
                assertNotNull(plugin.getAction().getLastModifiedDateTime());
                for (WorkflowRule rule : plugin.getRuleList()) {
                    assertNotNull(rule.getCreatedDateTime());
                    assertNotNull(rule.getLastModifiedDateTime());
                }
            }
        }
    }

    @Nested
    @DisplayName("Plugin ordering verification")
    class PluginOrdering {

        @Test
        @DisplayName("Plugins with non-sequential IDs are returned sorted by ID")
        void nonSequentialPluginIdsSortedCorrectly() throws Exception {
            WorkFlow fullWorkflow = loadTestWorkflow();
            List<Plugin> reversed = new ArrayList<>(fullWorkflow.getPluginList());
            java.util.Collections.reverse(reversed);
            postWorkflow(APP_NAME, WorkFlow.builder().pluginList(reversed).uiMapList(fullWorkflow.getUiMapList()).build());

            WorkFlow got = getWorkflow(APP_NAME);
            for (int i = 0; i < got.getPluginList().size() - 1; i++) {
                assertTrue(got.getPluginList().get(i).getId() < got.getPluginList().get(i + 1).getId(),
                        "Plugins should be sorted ascending by ID");
            }
        }

        @Test
        @DisplayName("Plugins with gaps in IDs (e.g., 1,5,10) preserve correct order and data")
        void gapPluginIdsPreserveOrder() throws Exception {
            WorkFlow fullWorkflow = loadTestWorkflow();
            List<Plugin> gapped = List.of(
                    fullWorkflow.getPluginList().get(0),
                    fullWorkflow.getPluginList().get(4),
                    fullWorkflow.getPluginList().get(9));
            postWorkflow(APP_NAME, WorkFlow.builder().pluginList(gapped).uiMapList(List.of()).build());

            WorkFlow got = getWorkflow(APP_NAME);
            assertEquals(3, got.getPluginList().size());
            assertEquals(1, got.getPluginList().get(0).getId());
            assertEquals(5, got.getPluginList().get(1).getId());
            assertEquals(10, got.getPluginList().get(2).getId());
            assertEquals("CONSUMER", got.getPluginList().get(0).getAction().getType());
            assertEquals("IFELSE", got.getPluginList().get(1).getAction().getType());
            assertEquals("MESSAGE", got.getPluginList().get(2).getAction().getType());
        }
    }

    @Nested
    @DisplayName("Special characters and complex data round-trip")
    class SpecialCharacters {

        @Test
        @DisplayName("Rule keys with regex special characters survive round-trip")
        void ruleKeysWithRegexSpecialChars() throws Exception {
            String complexRuleKey = "$.data[?(@.name =~ /^[A-Z]{2,3}\\d+$/ && @.value > 100)]";
            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(Plugin.builder().id(1).description("Complex regex rule")
                            .ruleList(List.of(WorkflowRule.builder().key(complexRuleKey)
                                    .remark("Regex with special chars").build()))
                            .action(WorkflowType.builder().provider("SYSTEM").type("IFELSE")
                                    .remark("Test action").build()).build()))
                    .uiMapList(List.of()).build();

            postWorkflow(APP_NAME, workflow);
            WorkFlow got = getWorkflow(APP_NAME);
            assertEquals(complexRuleKey, got.getPluginList().get(0).getRuleList().get(0).getKey());
        }

        @Test
        @DisplayName("Unicode characters in description and remark survive round-trip")
        void unicodeCharactersSurviveRoundTrip() throws Exception {
            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(Plugin.builder().id(1).description("步骤一：发送通知 🚀")
                            .ruleList(List.of(WorkflowRule.builder().key("$.data")
                                    .remark("规则说明：检查数据是否存在").build()))
                            .action(WorkflowType.builder().provider("SYSTEM").type("MESSAGE")
                                    .remark("动作备注：发送消息给用户 éàü ñ").build()).build()))
                    .uiMapList(List.of()).build();

            postWorkflow(APP_NAME, workflow);
            WorkFlow got = getWorkflow(APP_NAME);
            assertEquals("步骤一：发送通知 🚀", got.getPluginList().get(0).getDescription());
            assertEquals("规则说明：检查数据是否存在", got.getPluginList().get(0).getRuleList().get(0).getRemark());
            assertEquals("动作备注：发送消息给用户 éàü ñ", got.getPluginList().get(0).getAction().getRemark());
        }

        @Test
        @DisplayName("URL with query parameters and special chars survives base64 round-trip")
        void urlWithSpecialCharsSurvivesRoundTrip() throws Exception {
            String complexUrl = "https://api.example.com/v2/search?q=hello+world&filter=type%3Dactive&page=1&size=50";
            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(Plugin.builder().id(1).description("URL with query params")
                            .ruleList(List.of(WorkflowRule.builder().key("$.data").remark("rule").build()))
                            .action(WorkflowType.builder().provider("ExternalAPI").type("CONSUMER").remark("Call external")
                                    .httpRequestMethod("POST")
                                    .httpRequestUrlWithQueryParameter(complexUrl)
                                    .internalHttpRequestUrlWithQueryParameter(complexUrl)
                                    .httpRequestHeaders("{\"Authorization\":\"Bearer abc.def.ghi\",\"Content-Type\":\"application/json\"}")
                                    .httpRequestBody("{\"query\":\"SELECT * FROM table WHERE id = 'test' AND status != \\\"deleted\\\"\"}")
                                    .trackingNumberSchemaInHttpResponse("{\"path\":\"$.result[0].trackingId\"}").build()).build()))
                    .uiMapList(List.of()).build();

            postWorkflow(APP_NAME, workflow);
            var action = getWorkflow(APP_NAME).getPluginList().get(0).getAction();
            assertEquals(complexUrl, action.getHttpRequestUrlWithQueryParameter());
            assertEquals(complexUrl, action.getInternalHttpRequestUrlWithQueryParameter());
            assertEquals("{\"Authorization\":\"Bearer abc.def.ghi\",\"Content-Type\":\"application/json\"}",
                    action.getHttpRequestHeaders());
        }

        @Test
        @DisplayName("elseLogic with deeply nested JSON survives round-trip")
        void deeplyNestedElseLogicSurvivesRoundTrip() throws Exception {
            String deepJson = "{\"level1\":{\"level2\":{\"level3\":{\"level4\":{\"value\":\"deep\",\"array\":[1,2,3]}}}}}";
            WorkFlow workflow = WorkFlow.builder()
                    .pluginList(List.of(Plugin.builder().id(1).description("Deep JSON")
                            .ruleList(List.of(WorkflowRule.builder().key("$.x").remark("r").build()))
                            .action(WorkflowType.builder().provider("SYSTEM").type("IFELSE")
                                    .remark("Deep nested").elseLogic(deepJson).build()).build()))
                    .uiMapList(List.of()).build();

            postWorkflow(APP_NAME, workflow);
            WorkFlow got = getWorkflow(APP_NAME);

            assertEquals(objectMapper.readTree(deepJson),
                    objectMapper.readTree(got.getPluginList().get(0).getAction().getElseLogic()),
                    "Deeply nested JSON should survive round-trip");
        }
    }
}
