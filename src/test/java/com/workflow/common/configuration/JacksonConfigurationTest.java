package com.workflow.common.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JacksonConfigurationTest {

    @Test
    void objectMapperBeanShouldBeConfigured() {
        JacksonConfiguration configuration = new JacksonConfiguration();
        ObjectMapper objectMapper = configuration.objectMapper();

        assertNotNull(objectMapper);
        assertEquals(Boolean.FALSE, objectMapper.configOverride(ArrayNode.class).getMergeable());
    }
}
