package com.workflow.common.configuration;

import com.workflow.common.exception.ApiErrorCatalog;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocOpenUiConfiguration {

    @Bean
    public OpenAPI workflowOpenAPI() {
        return new OpenAPI()
                .components(errorComponents())
                .info(new Info()
                        .title("Workflow Operation API")
                        .description("""
                                APIs for workflow CRUD plus entity-setting query and revision history.

                                Unified Error Codes:
                                %s
                                """.formatted(ApiErrorCatalog.documentationText()))
                        .version("v1"));
    }

    @Bean
    public OperationCustomizer customize() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }
            ensureErrorResponse(responses, "400", "Business Error", businessErrorExample());
            ensureErrorResponse(responses, "409", "Business Error", businessErrorExample());
            ensureErrorResponse(responses, "500", "System Error", systemErrorExample());
            return operation;
        };
    }

    private void ensureErrorResponse(
            ApiResponses responses,
            String httpCode,
            String defaultDescription,
            Example example) {
        ApiResponse response = responses.get(httpCode);
        if (response == null) {
            response = new ApiResponse().description(defaultDescription);
            responses.addApiResponse(httpCode, response);
        } else if (response.getDescription() == null || response.getDescription().isBlank()) {
            response.setDescription(defaultDescription);
        }
        if (response.getContent() == null) {
            response.setContent(new Content());
        }
        MediaType mediaType = response.getContent().get("application/json");
        if (mediaType == null) {
            mediaType = new MediaType();
            response.getContent().addMediaType("application/json", mediaType);
        }
        if (mediaType.getSchema() == null) {
            mediaType.setSchema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"));
        }
        if (mediaType.getExamples() == null || mediaType.getExamples().isEmpty()) {
            mediaType.addExamples(httpCode.equals("500") ? "System Error" : "Business Error", example);
        }
    }

    private Example businessErrorExample() {
        return new Example().value("""
                {
                  "errorInfo": [
                    {
                      "code": "WF-400-000",
                      "detail": {
                        "cause": "Bad request payload or parameters"
                      }
                    }
                  ],
                  "requestCorrelation": "69b6ca24f5aa1f948fa61bf75ef836e3",
                  "sessionCorrelation": "8fa61bf75ef836e3"
                }
                """);
    }

    private Example systemErrorExample() {
        return new Example().value("""
                {
                  "errorInfo": [
                    {
                      "code": "WF-500-000",
                      "detail": {
                        "cause": "Internal server error"
                      }
                    }
                  ],
                  "requestCorrelation": "69b6ca24f5aa1f948fa61bf75ef836e3",
                  "sessionCorrelation": "8fa61bf75ef836e3"
                }
                """);
    }

    private Components errorComponents() {
        Components components = new Components();

        components.addSchemas("ApiErrorDetail", new ObjectSchema()
                .addProperty("cause", new StringSchema().example("Application name must exist exactly once; found: 0")));

        components.addSchemas("ApiErrorInfo", new ObjectSchema()
                .addProperty("code", new StringSchema().example("WF-400-000"))
                .addProperty("detail", new Schema<>().$ref("#/components/schemas/ApiErrorDetail")));

        components.addSchemas("ApiErrorResponse", new ObjectSchema()
                .addProperty("errorInfo", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/ApiErrorInfo")))
                .addProperty("requestCorrelation", new StringSchema().example("69b6ca24f5aa1f948fa61bf75ef836e3"))
                .addProperty("sessionCorrelation", new StringSchema().example("8fa61bf75ef836e3")));

        return components;
    }
}
