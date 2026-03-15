package com.workflow.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApiErrorInfo", description = "Single error entry")
public class ApiErrorInfo {

    @Schema(description = "Centralized error code", example = "WF-400-001")
    private String code;

    @Schema(description = "Error detail payload")
    private ApiErrorDetail detail;
}
