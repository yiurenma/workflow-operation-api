package com.workflow.common.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.data.rest.core.config.MetadataConfiguration;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringDataRestConfigurationTest {

    @Test
    void configureRepositoryRestConfigurationShouldDisableAlps() {
        SpringDataRestConfiguration configuration = new SpringDataRestConfiguration();
        RepositoryRestConfiguration repositoryRestConfiguration = mock(RepositoryRestConfiguration.class);
        MetadataConfiguration metadataConfiguration = mock(MetadataConfiguration.class);
        CorsRegistry corsRegistry = mock(CorsRegistry.class);

        when(repositoryRestConfiguration.getMetadataConfiguration()).thenReturn(metadataConfiguration);

        configuration.configureRepositoryRestConfiguration(repositoryRestConfiguration, corsRegistry);

        verify(metadataConfiguration).setAlpsEnabled(false);
    }
}
