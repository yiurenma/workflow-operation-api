package com.workflow.dao.repository;

import com.querydsl.core.types.dsl.StringExpression;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Repository
@RequestMapping(value = "/workflow/")
@RepositoryRestResource(path = "entity-setting")
@Tag(
        name = "Entity Setting Repository API",
        description = """
                Spring Data REST endpoints for entity setting CRUD and search.
                                
                Default CRUD endpoints:
                - GET /workflow/entity-setting
                - POST /workflow/entity-setting
                - GET /workflow/entity-setting/{id}
                - PUT /workflow/entity-setting/{id}
                - PATCH /workflow/entity-setting/{id}
                - DELETE /workflow/entity-setting/{id}
                                
                Search endpoint:
                - GET /workflow/entity-setting/search/getWorkflowEntitySettingByApplicationName?applicationName=ITEST_APP
                """
)
public interface WorkflowEntitySettingRepository
    extends JpaRepository<WorkflowEntitySetting, Long>,
    PagingAndSortingRepository<WorkflowEntitySetting, Long>,
    JpaSpecificationExecutor<WorkflowEntitySetting>,
    RevisionRepository<WorkflowEntitySetting, Long, Integer> ,
    QuerydslPredicateExecutor<WorkflowEntitySetting>,
    QuerydslBinderCustomizer<QWorkflowEntitySetting> {

    @RestResource(path = "getWorkflowEntitySettingByApplicationName", rel = "getWorkflowEntitySettingByApplicationName")
    @Operation(
            summary = "Search entity setting by exact applicationName",
            description = "Repository search endpoint used by Spring Data REST. "
                    + "Returns entity settings whose applicationName matches exactly."
    )
    List<WorkflowEntitySetting> getWorkflowEntitySettingByApplicationName(
            @Parameter(description = "Exact application identifier", example = "ITEST_APP")
            String applicationName
    );

    @Override
    default void customize(QuerydslBindings bindings, QWorkflowEntitySetting root) {
        // Make applicationName search fuzzy by default for querydsl predicate API.
        bindings.bind(root.applicationName).first(StringExpression::containsIgnoreCase);
    }
}
