package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMapping;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowEntitySettingRepository;
import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMappingRepository;
import com.workflow.dao.repository.WorkflowRuleAndType;
import com.workflow.dao.repository.WorkflowRuleAndTypeRepository;
import com.workflow.dao.repository.WorkflowType;
import com.workflow.common.exception.ApiBusinessException;
import com.workflow.common.exception.ApiErrorCatalog;
import com.workflow.common.util.Base64Util;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(
        name = "Workflow API",
        description = "Core workflow management APIs: query, create/update, delete, and copy by application name."
)
@Validated
@RequiredArgsConstructor
public class WorkflowGetController {

    private final WorkflowEntitySettingRepository workflowEntitySettingRepository;
    private final WorkflowEntityAndLinkingIdMappingRepository workflowEntityAndLinkingIdMappingRepository;
    private final WorkflowRuleAndTypeRepository workflowRuleAndTypeRepository;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "Get workflow by application name",
            description = "Returns the current workflow definition including plugin list and UI metadata."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workflow loaded successfully"),
            @ApiResponse(responseCode = "400", description = "Application name does not exist exactly once")
    })
    @GetMapping(value = "/workflow", produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkFlow getWorkFlow(
            @RequestParam(required = true) @Parameter(example = "APP_A", required = true,
                    description = "Application identifier for the workflow") @NotNull String applicationName) {

        List<WorkflowEntitySetting> entitySettingList =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(applicationName);

        if (entitySettingList.size() != 1) {
            throw new ApiBusinessException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCatalog.WORKFLOW_APP_NOT_UNIQUE,
                    "Application name must exist exactly once; found: " + entitySettingList.size()
            );
        }

        WorkflowEntitySetting entitySetting = entitySettingList.get(0);
        WorkFlow currentWorkFlow = null;
        if (StringUtils.hasText(entitySetting.getWorkflow())) {
            try {
                String decoded = Base64Util.base64Decode(entitySetting.getWorkflow(), true, objectMapper);
                currentWorkFlow = objectMapper.readValue(decoded, WorkFlow.class);
            } catch (Exception e) {
                log.warn("Failed to parse workflow JSON: {}", e.getMessage());
            }
        }

        List<WorkflowEntityAndLinkingIdMapping> linkingIdMappingList =
                workflowEntityAndLinkingIdMappingRepository.findAllByWorkflowEntitySettingId(entitySetting.getId());
        linkingIdMappingList.sort(Comparator.comparing(WorkflowEntityAndLinkingIdMapping::getLogicOrder));

        List<String> linkingIds = linkingIdMappingList.stream()
                .map(WorkflowEntityAndLinkingIdMapping::getLinkingId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<WorkflowRuleAndType> allRuleAndTypeList = linkingIds.isEmpty()
                ? List.of()
                : workflowRuleAndTypeRepository.findAllByLinkingIdIn(linkingIds);
        // Group once to avoid O(n²) repeated filtering by linkingId in the loop.
        Map<String, List<WorkflowRuleAndType>> ruleAndTypeByLinkingId = allRuleAndTypeList.stream()
                .filter(rt -> rt.getLinkingId() != null)
                .collect(Collectors.groupingBy(WorkflowRuleAndType::getLinkingId));

        List<Plugin> pluginList = new ArrayList<>();
        for (WorkflowEntityAndLinkingIdMapping mapping : linkingIdMappingList) {
            log.info("Get information for step: {} and remark: {}", mapping.getLogicOrder(), mapping.getRemark());
            String linkingId = mapping.getLinkingId();
            List<WorkflowRuleAndType> ruleAndTypeList = linkingId != null
                    ? ruleAndTypeByLinkingId.getOrDefault(linkingId, List.of())
                    : List.of();

            if (!ruleAndTypeList.isEmpty()) {
                WorkflowType typeView = ruleAndTypeList.get(0).getWorkflowType();
                WorkflowType copyType = copyAndDecodeWorkflowType(typeView);

                Object uiMap = currentWorkFlow != null && currentWorkFlow.getPluginList() != null
                        ? currentWorkFlow.getPluginList().stream()
                        .filter(p -> p.getId() != null && p.getId().equals(mapping.getLogicOrder()))
                        .findFirst()
                        .map(Plugin::getUiMap)
                        .orElse(createDefaultUiMap(mapping.getLogicOrder(), typeView.getType()))
                        : createDefaultUiMap(mapping.getLogicOrder(), typeView.getType());

                pluginList.add(Plugin.builder()
                        .id(mapping.getLogicOrder())
                        .description(mapping.getRemark())
                        .linkingIdOfRuleListAndAction(linkingId)
                        .action(copyType)
                        .ruleList(ruleAndTypeList.stream().map(WorkflowRuleAndType::getWorkflowRule).toList())
                        .uiMap(uiMap)
                        .build());
            } else {
                log.warn("No rule and action linking for step: {} and remark: {}", mapping.getLogicOrder(), mapping.getRemark());
                pluginList.add(Plugin.builder()
                        .id(mapping.getLogicOrder())
                        .description(mapping.getRemark())
                        .linkingIdOfRuleListAndAction(linkingId)
                        .uiMap(createDefaultUiMap(mapping.getLogicOrder(), "Unknown"))
                        .build());
            }
        }
        pluginList.sort(Comparator.comparing(Plugin::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        for (Plugin plugin : pluginList) {
            if (plugin.getUiMap() == null && plugin.getAction() != null) {
                plugin.setUiMap(createDefaultUiMap(plugin.getId(), plugin.getAction().getType()));
            }
        }

        List<Object> uiMapList = (currentWorkFlow != null && currentWorkFlow.getUiMapList() != null && !currentWorkFlow.getUiMapList().isEmpty())
                ? currentWorkFlow.getUiMapList()
                : createDefaultUiMapList(pluginList);

        return WorkFlow.builder().pluginList(pluginList).uiMapList(uiMapList).build();
    }

    private Object createDefaultUiMap(Integer id, String type) {
        String normalizedType = StringUtils.hasText(type) ? type : "Unknown";
        Map<String, Object> uiMap = new LinkedHashMap<>();
        uiMap.put("id", normalizedType + "_" + id);
        uiMap.put("type", normalizedType);
        uiMap.put("position", Map.of("x", 100, "y", 100 * (id != null ? id : 0)));
        uiMap.put("measured", Map.of("width", 160, "height", 38));
        return uiMap;
    }

    private WorkflowType copyAndDecodeWorkflowType(WorkflowType source) {
        WorkflowType copyType = WorkflowType.builder().build();
        org.springframework.beans.BeanUtils.copyProperties(source, copyType);
        copyType.setElseLogic(Base64Util.base64Decode(source.getElseLogic(), true, objectMapper));
        copyType.setHttpRequestUrlWithQueryParameter(Base64Util.base64Decode(source.getHttpRequestUrlWithQueryParameter(), false, objectMapper));
        copyType.setInternalHttpRequestUrlWithQueryParameter(Base64Util.base64Decode(source.getInternalHttpRequestUrlWithQueryParameter(), false, objectMapper));
        copyType.setHttpRequestHeaders(Base64Util.base64Decode(source.getHttpRequestHeaders(), true, objectMapper));
        copyType.setHttpRequestBody(Base64Util.base64Decode(source.getHttpRequestBody(), true, objectMapper));
        copyType.setTrackingNumberSchemaInHttpResponse(Base64Util.base64Decode(source.getTrackingNumberSchemaInHttpResponse(), true, objectMapper));
        return copyType;
    }

    @SuppressWarnings("unchecked")
    private List<Object> createDefaultUiMapList(List<Plugin> pluginList) {
        List<Object> uiMapList = new ArrayList<>();
        for (int i = 0; i < pluginList.size() - 1; i++) {
            Object uiMap = pluginList.get(i).getUiMap();
            Object nextUiMap = pluginList.get(i + 1).getUiMap();
            String sourceId = uiMap instanceof Map m ? (String) m.get("id") : "";
            String targetId = nextUiMap instanceof Map m ? (String) m.get("id") : "";
            Map<String, Object> connection = new LinkedHashMap<>();
            connection.put("animated", true);
            connection.put("markerEnd", Map.of("type", "arrowclosed"));
            connection.put("type", "buttonEdge");
            connection.put("style", Map.of("strokeWidth", 2));
            connection.put("zIndex", 1001);
            connection.put("source", sourceId);
            connection.put("sourceHandle", "source-handle");
            connection.put("target", targetId);
            connection.put("targetHandle", "target-handle");
            connection.put("id", "xy-edge__" + sourceId + "source-handle-" + targetId + "target-handle");
            uiMapList.add(connection);
        }
        return uiMapList;
    }
}
