package com.workflow.controller.domain;

import com.workflow.dao.repository.WorkflowRule;
import com.workflow.dao.repository.WorkflowType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plugin {

    private Integer id;
    private String description;
    private String linkingIdOfRuleListAndAction;
    private WorkflowType action;
    private List<WorkflowRule> ruleList;
    private Object uiMap;
}
