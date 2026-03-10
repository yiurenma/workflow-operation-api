package com.workflow.common.configuration;

import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertSame;

class SpringDocOpenUiConfigurationTest {

    @Test
    void customizeShouldReturnSameOperationInstance() throws NoSuchMethodException {
        SpringDocOpenUiConfiguration configuration = new SpringDocOpenUiConfiguration();
        OperationCustomizer customizer = configuration.customize();

        Operation operation = new Operation().summary("s");
        Method method = SpringDocOpenUiConfigurationTest.class.getDeclaredMethod("helper");
        HandlerMethod handlerMethod = new HandlerMethod(this, method);

        Operation customized = customizer.customize(operation, handlerMethod);
        assertSame(operation, customized);
    }

    private void helper() {
    }
}
