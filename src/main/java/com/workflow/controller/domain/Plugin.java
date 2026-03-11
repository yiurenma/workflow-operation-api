package com.workflow.controller.domain;

import com.workflow.dao.repository.WorkflowRule;
import com.workflow.dao.repository.WorkflowType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single workflow step that combines rules, action and UI node data")
public class Plugin {

    @Schema(description = "Step order in the workflow")
    private Integer id;

    @Schema(description = "Step description displayed in workflow UI")
    private String description;

    @Schema(description = "Internal linking id between rules and action")
    private String linkingIdOfRuleListAndAction;

    @Schema(description = "Action configuration executed when rules are matched")
    private WorkflowType action;

    @ArraySchema(arraySchema = @Schema(description = "Rules evaluated for this step"))
    private List<WorkflowRule> ruleList;

    @Schema(description = "UI node information for this step")
    private Object uiMap;
}
