package com.workflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Profile endpoint exposure tests")
class ProfileEndpointExposureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/profile endpoint should not be exposed")
    void profileEndpointShouldReturn404() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("OpenAPI docs should not include /profile paths")
    void openApiShouldNotIncludeProfilePaths() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(apiDocs.contains("\"/profile\""));
        assertFalse(apiDocs.contains("\"/profile/entity-setting\""));
    }
}
