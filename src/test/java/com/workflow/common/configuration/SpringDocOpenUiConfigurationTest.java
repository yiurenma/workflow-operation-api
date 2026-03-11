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
        assertEquals("Internal server error", customized.getResponses().get("500").getDescription());
    }

    @Test
    void workflowOpenApiShouldExposeBasicMetadata() {
        SpringDocOpenUiConfiguration configuration = new SpringDocOpenUiConfiguration();
        OpenAPI openAPI = configuration.workflowOpenAPI();

        assertEquals("Low-code Workflow API", openAPI.getInfo().getTitle());
        assertEquals("v1", openAPI.getInfo().getVersion());
    }

    private void helper() {
    }
}
