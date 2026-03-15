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
@Schema(name = "ApiErrorDetail", description = "Error detail object")
public class ApiErrorDetail {

    @Schema(description = "Error cause detail", example = "Application name must exist exactly once; found: 0")
    private String cause;
}
