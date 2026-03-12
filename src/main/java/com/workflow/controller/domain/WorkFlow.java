package com.workflow.controller.domain;

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
@Schema(description = "Workflow definition used by UI and persistence layer")
public class WorkFlow {

    @ArraySchema(arraySchema = @Schema(description = "Ordered workflow steps (plugins)"))
    private List<Plugin> pluginList;

    @ArraySchema(arraySchema = @Schema(description = "Workflow UI edge list / graph metadata"))
    private List<Object> uiMapList;
}
