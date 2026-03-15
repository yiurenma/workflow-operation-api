package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.exception.ApiBusinessException;
import com.workflow.common.exception.ApiErrorCatalog;
import com.workflow.controller.domain.Plugin;
import com.workflow.controller.domain.WorkFlow;
import com.workflow.dao.repository.*;
import com.workflow.common.util.Base64Util;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(
        name = "Workflow API",
        description = "Core workflow management APIs: query, create/update, delete, and copy by application name."
)
@Validated
@RequiredArgsConstructor
public class WorkflowUpdateController {

    private static final int MAX_RETRY_ON_DUPLICATE_KEY = 3;
    private static final String WORKFLOW_REQUEST_BODY_EXAMPLE = """
            {
              "uiMapList": [
                {
                  "animated": true,
                  "markerEnd": { "type": "arrowclosed" },
                  "type": "buttonEdge",
                  "style": { "strokeWidth": 2 },
                  "zIndex": 1001,
                  "source": "CONSUMER_1",
                  "sourceHandle": "source-handle",
                  "target": "IFELSE_2",
                  "targetHandle": "target-handle",
                  "id": "xy-edge__CONSUMER_1source-handle-IFELSE_2target-handle"
                },
                {
                  "animated": true,
                  "markerEnd": { "type": "arrowclosed" },
                  "type": "buttonEdge",
                  "style": { "strokeWidth": 2 },
                  "zIndex": 1001,
                  "source": "IFELSE_2",
                  "sourceHandle": "source-handle",
                  "target": "IFELSE_3",
                  "targetHandle": "target-handle",
                  "id": "xy-edge__IFELSE_2source-handle-IFELSE_3target-handle"
                },
                {
                  "animated": true,
                  "markerEnd": { "type": "arrowclosed" },
                  "type": "buttonEdge",
                  "style": { "strokeWidth": 2 },
                  "zIndex": 1001,
                  "source": "IFELSE_3",
                  "sourceHandle": "source-handle",
                  "target": "IFELSE_4",
                  "targetHandle": "target-handle",
                  "id": "xy-edge__IFELSE_3source-handle-IFELSE_4target-handle"
                },
                {
                  "animated": true,
                  "markerEnd": { "type": "arrowclosed" },
                  "type": "buttonEdge",
                  "style": { "strokeWidth": 2 },
                  "zIndex": 1001,
                  "source": "IFELSE_4",
                  "sourceHandle": "source-handle",
                  "target": "IFELSE_5",
                  "targetHandle": "target-handle",
                  "id": "xy-edge__IFELSE_4source-handle-IFELSE_5target-handle"
                },
                {
                  "animated": true,
                  "markerEnd": { "type": "arrowclosed" },
                  "type": "buttonEdge",
                  "style": { "strokeWidth": 2 },
                  "zIndex": 1001,
                  "source": "IFELSE_5",
                  "sourceHandle": "source-handle",
                  "target": "IFELSE_6",
                  "targetHandle": "target-handle",
                  "id": "xy-edge__IFELSE_5source-handle-IFELSE_6target-handle"
                },
                {
                  "animated": true,
                  "markerEnd": { "type": "arrowclosed" },
                  "type": "buttonEdge",
                  "style": { "strokeWidth": 2 },
                  "zIndex": 1001,
                  "source": "IFELSE_6",
                  "sourceHandle": "source-handle",
                  "target": "IFELSE_7",
                  "targetHandle": "target-handle",
                  "id": "xy-edge__IFELSE_6source-handle-IFELSE_7target-handle"
                },
                {
                  "animated": true,
                  "markerEnd": { "type": "arrowclosed" },
                  "type": "buttonEdge",
                  "style": { "strokeWidth": 2 },
                  "zIndex": 1001,
                  "source": "IFELSE_7",
                  "sourceHandle": "source-handle",
                  "target": "IFELSE_8",
                  "targetHandle": "target-handle",
                  "id": "xy-edge__IFELSE_7source-handle-IFELSE_8target-handle"
                },
                {
                  "animated": true,
                  "markerEnd": { "type": "arrowclosed" },
                  "type": "buttonEdge",
                  "style": { "strokeWidth": 2 },
                  "zIndex": 1001,
                  "source": "IFELSE_8",
                  "sourceHandle": "source-handle",
                  "target": "MESSAGE_9",
                  "targetHandle": "target-handle",
                  "id": "xy-edge__IFELSE_8source-handle-MESSAGE_9target-handle"
                },
                {
                  "animated": true,
                  "markerEnd": { "type": "arrowclosed" },
                  "type": "buttonEdge",
                  "style": { "strokeWidth": 2 },
                  "zIndex": 1001,
                  "source": "MESSAGE_9",
                  "sourceHandle": "source-handle",
                  "target": "MESSAGE_10",
                  "targetHandle": "target-handle",
                  "id": "xy-edge__MESSAGE_9source-handle-MESSAGE_10target-handle"
                }
              ],
              "pluginList": [
                {
                  "id": 1,
                  "linkingIdOfRuleListAndAction": "4_1_1",
                  "ruleList": [
                    {
                      "key": "$.messageInformation.[?(!(@.enrichInformation.jointCustomerNumberList=~ /.+?/))]",
                      "remark": "Record has no co-owners"
                    }
                  ],
                  "action": {
                    "provider": "CustomerDataService",
                    "type": "CONSUMER",
                    "remark": "Fetch entity info from data service by record ID",
                    "httpRequestMethod": "GET",
                    "httpRequestUrlWithQueryParameter": "https://example.com/api/entities",
                    "internalHttpRequestUrlWithQueryParameter": "https://example.com/api/entities",
                    "httpRequestHeaders": "{\\"Content-Type\\":\\"application/json\\"}",
                    "httpRequestBody": "",
                    "trackingNumberSchemaInHttpResponse": "{}"
                  },
                  "description": "Step 1: Fetch entity info by record ID",
                  "uiMap": {
                    "id": "CONSUMER_1",
                    "type": "CONSUMER",
                    "position": { "x": 100, "y": 100 },
                    "measured": { "width": 320, "height": 92 }
                  }
                },
                {
                  "id": 2,
                  "linkingIdOfRuleListAndAction": "4_2_2",
                  "ruleList": [
                    {
                      "key": "$.messageInformation.[?(@.enrichInformation.jointCustomerNumberList=~ /.+?/)]",
                      "remark": "Record has co-owners"
                    }
                  ],
                  "action": {
                    "provider": "SYSTEM",
                    "type": "IFELSE",
                    "remark": "Pick first participant from co-owner list",
                    "elseLogic": "{\\"messageInformation\\":{\\"enrichInformation\\":{\\"customerNumber\\":\\"\\"}}}"
                  },
                  "description": "Step 2: For shared records, pick first participant and contact details",
                  "uiMap": {
                    "id": "IFELSE_2",
                    "type": "IFELSE",
                    "position": { "x": 100, "y": 200 },
                    "measured": { "width": 320, "height": 92 }
                  }
                },
                {
                  "id": 3,
                  "linkingIdOfRuleListAndAction": "4_3_3",
                  "ruleList": [
                    {
                      "key": "$.messageInformation.enrichInformation.[?(@.customerNumber=~/.+?/)]",
                      "remark": "Entity ID is present"
                    }
                  ],
                  "action": {
                    "provider": "SYSTEM",
                    "type": "IFELSE",
                    "remark": "Use the resolved entity ID"
                  },
                  "description": "Step 3: Check that entity ID is valid",
                  "uiMap": {
                    "id": "IFELSE_3",
                    "type": "IFELSE",
                    "position": { "x": 100, "y": 300 },
                    "measured": { "width": 320, "height": 92 }
                  }
                },
                {
                  "id": 4,
                  "linkingIdOfRuleListAndAction": "4_4_4",
                  "ruleList": [
                    {
                      "key": "$.messageInformation.enrichInformation.[?(!(@.digitalGuidIdentity =~/.+?/))]",
                      "remark": "Digital identity is missing"
                    }
                  ],
                  "action": {
                    "provider": "SYSTEM",
                    "type": "IFELSE",
                    "remark": "Set entity identifier to empty when no digital identity",
                    "elseLogic": "{\\"messageInformation\\":{\\"enrichInformation\\":{\\"customerIdentifer\\":\\"\\"}}}"
                  },
                  "description": "Step 4: When no digital identity, set entity identifier to empty",
                  "uiMap": {
                    "id": "IFELSE_4",
                    "type": "IFELSE",
                    "position": { "x": 100, "y": 400 },
                    "measured": { "width": 320, "height": 92 }
                  }
                },
                {
                  "id": 5,
                  "linkingIdOfRuleListAndAction": "4_5_5",
                  "ruleList": [
                    {
                      "key": "$.messageInformation.enrichInformation.[?(@.digitalGuidIdentity =~/.+?/)]",
                      "remark": "Digital identity is present"
                    }
                  ],
                  "action": {
                    "provider": "SYSTEM",
                    "type": "IFELSE",
                    "remark": "Set entity identifier to SourceA when digital identity exists",
                    "elseLogic": "{\\"messageInformation\\":{\\"enrichInformation\\":{\\"customerIdentifer\\":\\"SourceA\\"}}}"
                  },
                  "description": "Step 5: When digital identity exists, set entity identifier to SourceA",
                  "uiMap": {
                    "id": "IFELSE_5",
                    "type": "IFELSE",
                    "position": { "x": 100, "y": 500 },
                    "measured": { "width": 320, "height": 92 }
                  }
                },
                {
                  "id": 6,
                  "linkingIdOfRuleListAndAction": "4_6_6",
                  "ruleList": [
                    {
                      "key": "$.messageInformation[?(@.event == \\"EventTypeA\\" && @.direction== \\"Inbound\\")]",
                      "remark": "Type A event and inbound direction"
                    }
                  ],
                  "action": {
                    "provider": "SYSTEM",
                    "type": "IFELSE",
                    "remark": "Set counterparty description to \\"with the counterparty\\"",
                    "elseLogic": "{\\"messageInformation\\":{\\"enrichInformation\\":{\\"receivingBankDes\\":\\" with the counterparty\\"}}}"
                  },
                  "description": "Step 6: For type A event, set counterparty description",
                  "uiMap": {
                    "id": "IFELSE_6",
                    "type": "IFELSE",
                    "position": { "x": 100, "y": 600 },
                    "measured": { "width": 320, "height": 92 }
                  }
                },
                {
                  "id": 7,
                  "linkingIdOfRuleListAndAction": "4_7_7",
                  "ruleList": [
                    {
                      "key": "$.messageInformation[?((@.event == \\"EventTypeB\\" || @.event == \\"EventTypeC\\") && @.direction== \\"Inbound\\")]",
                      "remark": "Type B or C event and inbound direction"
                    }
                  ],
                  "action": {
                    "provider": "SYSTEM",
                    "type": "IFELSE",
                    "remark": "Set counterparty description to \\"requested by the counterparty\\"",
                    "elseLogic": "{\\"messageInformation\\":{\\"enrichInformation\\":{\\"receivingBankDes\\":\\" requested by the counterparty\\"}}}"
                  },
                  "description": "Step 7: For type B/C event, set receiver description",
                  "uiMap": {
                    "id": "IFELSE_7",
                    "type": "IFELSE",
                    "position": { "x": 100, "y": 700 },
                    "measured": { "width": 320, "height": 92 }
                  }
                },
                {
                  "id": 8,
                  "linkingIdOfRuleListAndAction": "4_8_8",
                  "ruleList": [
                    {
                      "key": "$.messageInformation[?(@.event == \\"EventTypeD\\" && @.direction== \\"Inbound\\")]",
                      "remark": "Type D event and inbound direction"
                    }
                  ],
                  "action": {
                    "provider": "SYSTEM",
                    "type": "IFELSE",
                    "remark": "Set counterparty description to \\"by the counterparty\\"",
                    "elseLogic": "{\\"messageInformation\\":{\\"enrichInformation\\":{\\"receivingBankDes\\":\\" by the counterparty\\"}}}"
                  },
                  "description": "Step 8: For type D event, set counterparty description",
                  "uiMap": {
                    "id": "IFELSE_8",
                    "type": "IFELSE",
                    "position": { "x": 100, "y": 800 },
                    "measured": { "width": 320, "height": 92 }
                  }
                },
                {
                  "id": 9,
                  "linkingIdOfRuleListAndAction": "4_9_9",
                  "ruleList": [
                    {
                      "key": "$.[?(@.messageInformation.enrichInformation.globalUserIdOfSoleCustomer=~/.+?/ || @.messageContactInfo.emailAddress=~/.+?/)]",
                      "remark": "At least one of user ID, email, or phone is present"
                    }
                  ],
                  "action": {
                    "provider": "MessagingService",
                    "type": "MESSAGE",
                    "remark": "Send notification via messaging platform",
                    "httpRequestMethod": "POST",
                    "httpRequestUrlWithQueryParameter": "https://example.com/async",
                    "internalHttpRequestUrlWithQueryParameter": "https://example.com/async",
                    "httpRequestHeaders": "{\\"Content-Type\\":\\"application/json\\"}",
                    "httpRequestBody": "{}",
                    "trackingNumberSchemaInHttpResponse": "{}"
                  },
                  "description": "Step 9: Send message notification",
                  "uiMap": {
                    "id": "MESSAGE_9",
                    "type": "MESSAGE",
                    "position": { "x": 100, "y": 900 },
                    "measured": { "width": 320, "height": 92 }
                  }
                },
                {
                  "id": 10,
                  "linkingIdOfRuleListAndAction": "4_10_10",
                  "ruleList": [
                    {
                      "key": "$.messageInformation.[?(@.enrichInformation.jointCustomerNumberList=~ /.+?/)]",
                      "remark": "Record has co-owners"
                    },
                    {
                      "key": "$[?(!(@.originMessageRecordId=~/.+?/))]",
                      "remark": "Not a retry request"
                    }
                  ],
                  "action": {
                    "provider": "SYSTEM",
                    "type": "MESSAGE",
                    "remark": "Recursively process each co-owner",
                    "httpRequestMethod": "POST",
                    "httpRequestUrlWithQueryParameter": "http://localhost/api/message",
                    "internalHttpRequestUrlWithQueryParameter": "http://localhost/api/message",
                    "httpRequestHeaders": "{\\"Content-Type\\":\\"application/json\\"}",
                    "httpRequestBody": "<<<$.messageInformation>>>",
                    "trackingNumberSchemaInHttpResponse": ""
                  },
                  "description": "Step 10: For shared records, recursively process each co-owner",
                  "uiMap": {
                    "id": "MESSAGE_10",
                    "type": "MESSAGE",
                    "position": { "x": 100, "y": 1000 },
                    "measured": { "width": 320, "height": 92 }
                  }
                }
              ]
            }
            """;

    private final WorkflowEntitySettingRepository workflowEntitySettingRepository;
    private final WorkflowEntityAndLinkingIdMappingRepository workflowEntityAndLinkingIdMappingRepository;
    private final WorkflowRuleAndTypeRepository workflowRuleAndTypeRepository;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final WorkflowTypeRepository workflowTypeRepository;
    private final WorkflowGetController workflowGetController;
    private final WorkflowDeleteController workflowDeleteController;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "Create or update workflow",
            description = "Upserts workflow by application name. Existing workflow internals are cleared and rebuilt."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workflow saved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid application/workflow payload"),
            @ApiResponse(responseCode = "409", description = "Workflow update failed after duplicate key retries")
    })
    @Transactional
    @PostMapping(value = "/workflow", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkFlow updateWorkFlow(
            @RequestParam(required = true) @Parameter(example = "APP_A", required = true,
                    description = "Application identifier for the workflow") @NotNull String applicationName,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Workflow definition with ordered plugin list and optional uiMapList",
                    content = @Content(
                            schema = @Schema(implementation = WorkFlow.class),
                            examples = @ExampleObject(
                                    name = "workflow-integration-test-request-body",
                                    summary = "Request body used in integration tests",
                                    description = "Copied from src/test/resources/workflow-integration-test-data.json",
                                    value = WORKFLOW_REQUEST_BODY_EXAMPLE
                            )
                    )
            )
            @RequestBody(required = false) @Valid WorkFlow workFlow) {
        if (workFlow == null) {
            throw new ApiBusinessException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCatalog.WORKFLOW_BODY_REQUIRED,
                    "Workflow body is required for update operation"
            );
        }

        List<WorkflowEntitySetting> entitySettingList =
                workflowEntitySettingRepository.getWorkflowEntitySettingByApplicationName(applicationName);

        WorkflowEntitySetting entitySetting;
        if (entitySettingList.isEmpty()) {
            entitySetting = WorkflowEntitySetting.builder()
                    .applicationName(applicationName)
                    .enabled(true)
                    .build();
        } else if (entitySettingList.size() > 1) {
            throw new ApiBusinessException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCatalog.WORKFLOW_APP_NOT_UNIQUE,
                    "Application name must exist exactly once; found: " + entitySettingList.size()
            );
        } else {
            entitySetting = entitySettingList.get(0);
        }
        for (int attempt = 1; attempt <= MAX_RETRY_ON_DUPLICATE_KEY; attempt++) {
            try {
                deleteAndAddWorkFlow(workFlow, entitySetting);
                return workflowGetController.getWorkFlow(applicationName);
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate key during workflow update (attempt {}/{}): {}",
                        attempt, MAX_RETRY_ON_DUPLICATE_KEY, e.getMessage());
                if (attempt == MAX_RETRY_ON_DUPLICATE_KEY) {
                    throw new ApiBusinessException(
                            HttpStatus.CONFLICT,
                            ApiErrorCatalog.WORKFLOW_UPDATE_DUPLICATE_KEY,
                            "Failed to update workflow after " + MAX_RETRY_ON_DUPLICATE_KEY + " retries due to duplicate key conflicts",
                            e
                    );
                }
            }
        }
        throw new ApiBusinessException(
                HttpStatus.CONFLICT,
                ApiErrorCatalog.WORKFLOW_UPDATE_DUPLICATE_KEY,
                "Failed to update workflow"
        );
    }

    @Transactional
    protected void deleteAndAddWorkFlow(WorkFlow workFlow, WorkflowEntitySetting entitySetting) {
        try {
            entitySetting.setWorkflow(Base64Util.base64Encode(objectMapper.writeValueAsString(workFlow), true, objectMapper));
        } catch (Exception e) {
            entitySetting.setWorkflow(null);
        }
        workflowEntitySettingRepository.saveAndFlush(entitySetting);

        workflowDeleteController.deleteWorkflowRulesMappingsAndTypes(entitySetting);

        List<WorkflowRuleAndType> savedRuleAndTypeList = new ArrayList<>();
        List<WorkflowEntityAndLinkingIdMapping> savedLinkingIdMappingList = new ArrayList<>();

        List<Plugin> pluginList = workFlow.getPluginList() != null ? workFlow.getPluginList() : List.of();
        for (Plugin plugin : pluginList) {
            log.info("Plugin order: {}", plugin.getId());
            if (plugin.getRuleList() != null && !plugin.getRuleList().isEmpty()) {
                List<WorkflowRule> savedRuleList = new ArrayList<>();
                for (WorkflowRule rule : plugin.getRuleList()) {
                    WorkflowRule copyRule = WorkflowRule.builder().build();
                    org.springframework.beans.BeanUtils.copyProperties(rule, copyRule);
                    copyRule.setId(null);
                    WorkflowRule savedRule = workflowRuleRepository.saveAndFlush(copyRule);
                    log.info("Store rule success, rule id: {}", savedRule.getId());
                    savedRuleList.add(savedRule);
                }

                WorkflowType copyType = encodeWorkflowTypeForPersistence(plugin.getAction());

                WorkflowType savedType = workflowTypeRepository.saveAndFlush(copyType);
                log.info("Store action success, action id: {}", savedType.getId());

                String linkingId = entitySetting.getId() + "_" + savedType.getId() + "_" + plugin.getId();
                for (WorkflowRule savedRule : savedRuleList) {
                    savedRuleAndTypeList.add(WorkflowRuleAndType.builder()
                            .workflowRule(savedRule)
                            .workflowType(savedType)
                            .linkingId(linkingId)
                            .build());
                    log.info("Save rule and action linking: {} {} {}", savedRule.getId(), savedType.getId(), linkingId);
                }
                savedLinkingIdMappingList.add(WorkflowEntityAndLinkingIdMapping.builder()
                        .logicOrder(plugin.getId())
                        .workflowEntitySetting(entitySetting)
                        .linkingId(linkingId)
                        .remark(plugin.getDescription())
                        .build());
                log.info("Save entity and linking: {} {}", entitySetting.getApplicationName(), linkingId);
            } else {
                WorkflowRule emptyRule = workflowRuleRepository.saveAndFlush(WorkflowRule.builder().key("").build());
                WorkflowType savedType = workflowTypeRepository.saveAndFlush(encodeWorkflowTypeForPersistence(plugin.getAction()));
                String linkingId = entitySetting.getId() + "_" + savedType.getId() + "_" + plugin.getId();
                savedRuleAndTypeList.add(WorkflowRuleAndType.builder()
                        .workflowRule(emptyRule)
                        .workflowType(savedType)
                        .linkingId(linkingId)
                        .build());
                savedLinkingIdMappingList.add(WorkflowEntityAndLinkingIdMapping.builder()
                        .logicOrder(plugin.getId())
                        .workflowEntitySetting(entitySetting)
                        .linkingId(linkingId)
                        .remark(plugin.getDescription())
                        .build());
            }
        }

        log.info("Going to store rule and action mapping: {}", savedRuleAndTypeList);
        workflowRuleAndTypeRepository.saveAll(savedRuleAndTypeList);
        workflowRuleAndTypeRepository.flush();

        log.info("Going to store entity and linking mapping: {}", savedLinkingIdMappingList);
        workflowEntityAndLinkingIdMappingRepository.saveAll(savedLinkingIdMappingList);
        workflowEntityAndLinkingIdMappingRepository.flush();
    }

    private WorkflowType encodeWorkflowTypeForPersistence(WorkflowType source) {
        WorkflowType copyType = WorkflowType.builder().build();
        if (source != null) {
            org.springframework.beans.BeanUtils.copyProperties(source, copyType);
        }
        copyType.setId(null);
        copyType.setElseLogic(Base64Util.base64Encode(copyType.getElseLogic(), true, objectMapper));
        copyType.setHttpRequestUrlWithQueryParameter(Base64Util.base64Encode(copyType.getHttpRequestUrlWithQueryParameter(), false, objectMapper));
        copyType.setInternalHttpRequestUrlWithQueryParameter(Base64Util.base64Encode(copyType.getInternalHttpRequestUrlWithQueryParameter(), false, objectMapper));
        copyType.setHttpRequestHeaders(Base64Util.base64Encode(copyType.getHttpRequestHeaders(), true, objectMapper));
        copyType.setHttpRequestBody(Base64Util.base64Encode(copyType.getHttpRequestBody(), true, objectMapper));
        copyType.setTrackingNumberSchemaInHttpResponse(Base64Util.base64Encode(copyType.getTrackingNumberSchemaInHttpResponse(), true, objectMapper));
        return copyType;
    }
}
