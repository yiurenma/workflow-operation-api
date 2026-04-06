package com.workflow.integration;

import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMapping;
import com.workflow.dao.repository.WorkflowEntitySetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AutoCopy workflow integration tests")
class WorkflowAutoCopyIntegrationTest extends AbstractWorkflowIntegrationTest {

    @Test
    @DisplayName("AutoCopy creates target with same plugin structure as source")
    void autoCopyCreatesTargetWithSameStructure() throws Exception {
        WorkFlow original = loadTestWorkflow();
        postWorkflow(APP_NAME, original);

        mockMvc.perform(post("/api/workflow/autoCopy")
                        .param("fromApplicationName", APP_NAME)
                        .param("toApplicationName", APP_NAME_COPY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        WorkFlow source = getWorkflow(APP_NAME);
        WorkFlow target = getWorkflow(APP_NAME_COPY);

        assertEquals(source.getPluginList().size(), target.getPluginList().size());
        for (int i = 0; i < source.getPluginList().size(); i++) {
            Plugin sp = source.getPluginList().get(i);
            Plugin tp = target.getPluginList().get(i);
            assertEquals(sp.getId(), tp.getId());
            assertEquals(sp.getAction().getType(), tp.getAction().getType());
            assertEquals(sp.getAction().getProvider(), tp.getAction().getProvider());
            assertEquals(sp.getRuleList().size(), tp.getRuleList().size());
            for (int j = 0; j < sp.getRuleList().size(); j++) {
                assertEquals(sp.getRuleList().get(j).getKey(), tp.getRuleList().get(j).getKey());
            }
        }
    }

    @Test
    @DisplayName("AutoCopy creates independent DB records for target")
    void autoCopyCreatesIndependentDbRecords() throws Exception {
        postWorkflow(APP_NAME, loadTestWorkflow());

        mockMvc.perform(post("/api/workflow/autoCopy")
                        .param("fromApplicationName", APP_NAME)
                        .param("toApplicationName", APP_NAME_COPY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        WorkflowEntitySetting sourceSetting =
                entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
        WorkflowEntitySetting targetSetting =
                entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME_COPY).get(0);
        assertNotEquals(sourceSetting.getId(), targetSetting.getId());

        List<WorkflowEntityAndLinkingIdMapping> sourceMappings =
                linkingIdMappingRepository.findAllByWorkflowEntitySettingId(sourceSetting.getId());
        List<WorkflowEntityAndLinkingIdMapping> targetMappings =
                linkingIdMappingRepository.findAllByWorkflowEntitySettingId(targetSetting.getId());
        assertEquals(sourceMappings.size(), targetMappings.size());

        List<String> sourceLinkingIds = sourceMappings.stream()
                .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList();
        for (String targetLinkingId : targetMappings.stream()
                .map(WorkflowEntityAndLinkingIdMapping::getLinkingId).toList()) {
            assertFalse(sourceLinkingIds.contains(targetLinkingId),
                    "Target linkingId must differ from source IDs");
        }
    }

    @Test
    @DisplayName("Deleting target does not affect source")
    void deletingTargetDoesNotAffectSource() throws Exception {
        WorkFlow original = loadTestWorkflow();
        postWorkflow(APP_NAME, original);

        mockMvc.perform(post("/api/workflow/autoCopy")
                        .param("fromApplicationName", APP_NAME)
                        .param("toApplicationName", APP_NAME_COPY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/workflow").param("applicationName", APP_NAME_COPY))
                .andExpect(status().isOk());

        getWorkflow(APP_NAME);

        WorkflowEntitySetting sourceSetting =
                entitySettingRepository.getWorkflowEntitySettingByApplicationName(APP_NAME).get(0);
        assertEquals(original.getPluginList().size(),
                linkingIdMappingRepository.findAllByWorkflowEntitySettingId(sourceSetting.getId()).size());
    }

    @Test
    @DisplayName("AutoCopy with same source and target returns 400")
    void autoCopySameNameReturns400() throws Exception {
        postWorkflow(APP_NAME, loadTestWorkflow());
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

    @Test
    @DisplayName("AutoCopy copies entity setting metadata (retry, eimId, region, etc.)")
    void autoCopyCopiesEntitySettingMetadata() throws Exception {
        postWorkflow(APP_NAME, loadTestWorkflow());

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
        assertEquals(APP_NAME_COPY, targetSetting.getApplicationName());
        assertEquals(sourceSetting.isRetry(), targetSetting.isRetry());
        assertEquals(sourceSetting.isTracking(), targetSetting.isTracking());
        assertEquals(sourceSetting.getEimId(), targetSetting.getEimId());
        assertEquals(sourceSetting.getDefaultServiceAccount(), targetSetting.getDefaultServiceAccount());
        assertEquals(sourceSetting.getRegion(), targetSetting.getRegion());
    }

    @Test
    @DisplayName("AutoCopy to existing target returns 400")
    void autoCopyToExistingTargetReturns400() throws Exception {
        WorkFlow fullWorkflow = loadTestWorkflow();
        WorkFlow partialWorkflow = loadTestWorkflow();
        partialWorkflow.setPluginList(partialWorkflow.getPluginList().subList(0, 2));

        postWorkflow(APP_NAME, fullWorkflow);
        postWorkflow(APP_NAME_COPY, partialWorkflow);

        mockMvc.perform(post("/api/workflow/autoCopy")
                        .param("fromApplicationName", APP_NAME)
                        .param("toApplicationName", APP_NAME_COPY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        assertEquals(2, getWorkflow(APP_NAME_COPY).getPluginList().size(),
                "Target should be unchanged after rejected copy");
    }
}
