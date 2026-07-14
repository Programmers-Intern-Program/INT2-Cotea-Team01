package com.cotea.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HintRequest {

    @NotNull
    private Integer problemId;

    @NotBlank
    private String stage;

    private Integer hintLevel;

    @NotBlank
    private String questionType;

    private String buttonId;
    private String questionText;
    private String userCode;
    private String language;
    private String submissionResult;
    private List<ConversationMessage> conversationHistory = new ArrayList<>();

    /** 로컬 테스트용 — api-spec 공개 필드 아님 */
    private Boolean dryRun;
}
