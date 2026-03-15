package com.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.envers.repository.config.EnableEnversRepositories;

@SpringBootApplication
@EnableEnversRepositories
public class WorkflowOperationApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowOperationApiApplication.class, args);
    }
}
