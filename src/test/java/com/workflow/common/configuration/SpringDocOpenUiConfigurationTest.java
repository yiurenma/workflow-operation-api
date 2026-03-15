package com.workflow.common.configuration;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    private void helper() {
    }
}
