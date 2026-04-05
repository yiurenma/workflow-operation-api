package com.workflow.controller.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Partial update payload for WorkflowEntitySetting. Only fields present in the request body are applied; omitted fields are left unchanged.")
public class EntitySettingPatchRequest {

    @Schema(description = "Whether this entity setting is active")
    private Boolean enabled;

    @Schema(description = "Whether enrichment and dispatch execute asynchronously (true) or synchronously (false)", example = "true")
    private Boolean asyncMode;

    @Schema(description = "Whether retry is enabled for this entity")
    private Boolean retry;

    @Schema(description = "Whether tracking is enabled for this entity")
    private Boolean tracking;

    @Schema(description = "Whether duplicate-record errors should be ignored")
    private Boolean ignoreDuplicateRecordError;

    @Schema(description = "EIM (Enterprise Identity Manager) identifier or integration ID")
    private String eimId;

    @Schema(description = "Default service account for API calls")
    private String defaultServiceAccount;

    @Schema(description = "Region or environment (e.g. prod, dev)")
    private String region;

    @Schema(description = "Retry configuration payload (JSON/text)", maxLength = 10000)
    private String retryProperties;
}
