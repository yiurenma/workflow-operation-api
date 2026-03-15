package com.workflow.common.exception;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApiErrorResponse", description = "Unified API error schema")
public class ApiErrorResponse {

    @ArraySchema(arraySchema = @Schema(description = "Error list"))
    private List<ApiErrorInfo> errorInfo;

    @Schema(description = "Request correlation id", example = "69b6ca24f5aa1f948fa61bf75ef836e3")
    private String requestCorrelation;

    @Schema(description = "Session correlation id", example = "8fa61bf75ef836e3")
    private String sessionCorrelation;
}
