package com.workflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API documentation endpoint tests")
class ApiDocumentationEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ReDoc endpoint should be exposed")
    void redocEndpointShouldBeExposed() throws Exception {
        mockMvc.perform(get("/redoc.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("redoc.standalone.js")));
    }

    @Test
    @DisplayName("Swagger UI endpoint should remain available")
    void swaggerUiEndpointShouldRemainAvailable() throws Exception {
        MvcResult result = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn();

        int statusCode = result.getResponse().getStatus();
        assertTrue(
                statusCode == 200 || (statusCode >= 300 && statusCode < 400),
                "Expected Swagger UI endpoint to be available (200 or 3xx), actual: " + statusCode
        );
    }

    @Test
    @DisplayName("OpenAPI docs should include unified error schema and codes")
    void openApiShouldIncludeUnifiedErrorSchemaAndCodes() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(apiDocs.contains("\"ApiErrorResponse\""));
        assertTrue(apiDocs.contains("WF-400-000"));
        assertTrue(apiDocs.contains("WF-400-001"));
        assertTrue(apiDocs.contains("WF-500-000"));
        assertTrue(apiDocs.contains("\"Business Error\""));
        assertTrue(apiDocs.contains("\"System Error\""));
    }
}
