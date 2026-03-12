package com.workflow.dao.repository;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Repository
@RequestMapping(value = "/workflow/")
@RepositoryRestResource(path = "entity-setting")
@Tag(name = "DB Repository", description = "[Tracking] Workflow DB Records")
public interface WorkflowEntitySettingRepository
    extends JpaRepository<WorkflowEntitySetting, Long>,
    JpaSpecificationExecutor<WorkflowEntitySetting>,
    RevisionRepository<WorkflowEntitySetting, Long, Integer> ,
    QuerydslPredicateExecutor<WorkflowEntitySetting> {
    List<WorkflowEntitySetting> getWorkflowEntitySettingByApplicationName(String applicationName);
}
