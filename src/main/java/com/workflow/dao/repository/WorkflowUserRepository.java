package com.workflow.dao.repository;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestMapping;

@Repository
@RequestMapping(value = "/workflow/")
@RepositoryRestResource(path = "workflow-user")
@Tag(
        name = "Workflow User Repository API",
        description = """
                Spring Data REST endpoints for workflow user CRUD and query.

                Default CRUD endpoints:
                - GET /api/workflow-user
                - POST /api/workflow-user
                - GET /api/workflow-user/{id}
                - PUT /api/workflow-user/{id}
                - PATCH /api/workflow-user/{id}
                - DELETE /api/workflow-user/{id}
                """
)
public interface WorkflowUserRepository extends
        QuerydslPredicateExecutor<WorkflowUser>,
        JpaRepository<WorkflowUser, Long>,
        JpaSpecificationExecutor<WorkflowUser> {
}
