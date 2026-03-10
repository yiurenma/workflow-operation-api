package com.workflow.common.configuration;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SpringDocOpenUiConfiguration {

    private static final String HEADER = "header";

    @Bean
    public OperationCustomizer customize() {
        return (operation, handlerMethod) -> {
            return operation;
        };
    }
}
