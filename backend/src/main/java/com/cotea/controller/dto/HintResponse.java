package com.cotea.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HintResponse {

    private final String responseText;
    private final String stage;
    private final Integer hintLevel;

    private final Boolean dryRun;
    private final List<String> tags;
    private final String systemPrompt;
    private final String userMessage;
    private final Integer ragChunkCount;
}
