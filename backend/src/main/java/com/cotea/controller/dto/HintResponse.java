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

    /** 개념/접근 자체가 부재한 것으로 판정되면 true — FE가 "관련 유형 문제 추천" 버튼 노출 */
    private final Boolean suggestConceptDrill;

    /** RELATED | OFF_TOPIC — dryRun/관측용 */
    private final String route;
    /** claude | openai | claude_fallback — dryRun/관측용 */
    private final String llmProvider;

    private final Boolean dryRun;
    private final List<String> tags;
    private final String systemPrompt;
    private final String userMessage;
    private final Integer ragChunkCount;
}
