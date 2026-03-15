package com.workflow.common.configuration;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringDocOpenUiConfigurationTest {

    @Test
    void customizeShouldReturnSameOperationInstanceAndAddDefault500Response() throws NoSuchMethodException {
        SpringDocOpenUiConfiguration configuration = new SpringDocOpenUiConfiguration();
        OperationCustomizer customizer = configuration.customize();

        Operation operation = new Operation().summary("s");
        Method method = SpringDocOpenUiConfigurationTest.class.getDeclaredMethod("helper");
        HandlerMethod handlerMethod = new HandlerMethod(this, method);

        Operation customized = customizer.customize(operation, handlerMethod);
        assertSame(operation, customized);
        assertEquals("Business Error", customized.getResponses().get("400").getDescription());
        assertEquals("Business Error", customized.getResponses().get("409").getDescription());
        assertEquals("System Error", customized.getResponses().get("500").getDescription());
    }

    @Test
    void workflowOpenApiShouldExposeBasicMetadata() {
        SpringDocOpenUiConfiguration configuration = new SpringDocOpenUiConfiguration();
        OpenAPI openAPI = configuration.workflowOpenAPI();

        assertEquals("Workflow Operation API", openAPI.getInfo().getTitle());
        assertEquals("v1", openAPI.getInfo().getVersion());
        org.junit.jupiter.api.Assertions.assertTrue(openAPI.getInfo().getDescription().contains("WF-400-000"));
        org.junit.jupiter.api.Assertions.assertTrue(openAPI.getInfo().getDescription().contains("WF-500-000"));
    }

    @Test
    void repositoryTagDocumentationCustomizerShouldAddTopLevelRepositoryTags() {
        SpringDocOpenUiConfiguration configuration = new SpringDocOpenUiConfiguration();
        OpenApiCustomizer customizer = configuration.repositoryTagDocumentationCustomizer();
        OpenAPI openAPI = new OpenAPI();

        customizer.customise(openAPI);

        assertTrue(openAPI.getTags().stream().anyMatch(t ->
                "Entity Setting Repository API".equals(t.getName())
                        && t.getDescription() != null
                        && t.getDescription().contains("/api/entity-setting")));
        assertTrue(openAPI.getTags().stream().anyMatch(t ->
                "Workflow User Repository API".equals(t.getName())
                        && t.getDescription() != null
                        && t.getDescription().contains("/api/workflow-user")));
    }

    @Test
    void customizeCalledTwiceShouldNotDuplicateResponsesOrExamples() throws NoSuchMethodException {
        SpringDocOpenUiConfiguration configuration = new SpringDocOpenUiConfiguration();
        OperationCustomizer customizer = configuration.customize();

        Operation operation = new Operation().summary("s");
        Method method = SpringDocOpenUiConfigurationTest.class.getDeclaredMethod("helper");
        HandlerMethod handlerMethod = new HandlerMethod(this, method);

        customizer.customize(operation, handlerMethod);
        customizer.customize(operation, handlerMethod);

        assertNotNull(operation.getResponses().get("400"));
        assertEquals("Business Error", operation.getResponses().get("400").getDescription());
        assertEquals(1, operation.getResponses().get("400").getContent()
                .get("application/json").getExamples().size());
    }

    @Test
    void repositoryTagCustomizerCalledTwiceShouldNotDuplicateTags() {
        SpringDocOpenUiConfiguration configuration = new SpringDocOpenUiConfiguration();
        OpenApiCustomizer customizer = configuration.repositoryTagDocumentationCustomizer();

        OpenAPI openAPI = new OpenAPI();
        openAPI.setTags(new ArrayList<>());
        openAPI.getTags().add(new Tag()
                .name("Entity Setting Repository API")
                .description("Existing description"));

        customizer.customise(openAPI);

        long count = openAPI.getTags().stream()
                .filter(t -> "Entity Setting Repository API".equals(t.getName()))
                .count();
        assertEquals(1, count);
        assertEquals("Existing description", openAPI.getTags().stream()
                .filter(t -> "Entity Setting Repository API".equals(t.getName()))
                .findFirst().orElseThrow().getDescription());
    }

    private void helper() {
    }
}
