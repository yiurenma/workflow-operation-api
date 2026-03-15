package com.workflow.common.configuration;

import com.workflow.dao.repository.WorkflowEntityAndLinkingIdMapping;
import com.workflow.dao.repository.WorkflowEntitySetting;
import com.workflow.dao.repository.WorkflowRecord;
import com.workflow.dao.repository.WorkflowReport;
import com.workflow.dao.repository.WorkflowRule;
import com.workflow.dao.repository.WorkflowRuleAndType;
import com.workflow.dao.repository.WorkflowType;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@Configuration
public class SpringDataRestConfiguration implements RepositoryRestConfigurer {

    @Override
    public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config, CorsRegistry cors) {
        // Disable ALPS profile metadata endpoints (e.g. /profile, /profile/{resource}).
        config.getMetadataConfiguration().setAlpsEnabled(false);
        // Expose DB ids in Spring Data REST responses.
        config.exposeIdsFor(
                WorkflowEntitySetting.class,
                WorkflowEntityAndLinkingIdMapping.class,
                WorkflowRule.class,
                WorkflowType.class,
                WorkflowRuleAndType.class,
                WorkflowRecord.class,
                WorkflowReport.class
        );
    }
}
