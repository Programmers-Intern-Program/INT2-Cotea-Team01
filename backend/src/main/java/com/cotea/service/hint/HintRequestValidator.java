package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import com.cotea.exception.CoteaException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class HintRequestValidator {

    private static final Set<String> STAGES = Set.of(
            "BEFORE_SOLVE",
            "SOLVING",
            "WRONG_ANSWER",
            "AFTER_SOLVE"
    );
    private static final String SUPPORTED_LANGUAGE = "java";
    private static final Set<String> QUESTION_TYPES = Set.of("BUTTON", "FREE_TEXT");
    private static final Set<String> HINT_BUTTONS = Set.of(
            "hint_level_1",
            "hint_level_2",
            "hint_level_3",
            "hint_level_4"
    );
    private static final Set<String> WRONG_ANSWER_BUTTONS = Set.of(
            "wrong_result_only",
            "why_wrong",
            "why_tle",
            "why_runtime_error"
    );
    private static final Set<String> SUBMISSION_RESULTS = Set.of(
            "WRONG_ANSWER",
            "TIME_LIMIT_EXCEEDED",
            "RUNTIME_ERROR"
    );

    private final QuestionResolver questionResolver;

    public HintRequestValidator(QuestionResolver questionResolver) {
        this.questionResolver = questionResolver;
    }

    public void validate(HintRequest request) {
        validateProblemId(request);
        validateStage(request.getStage());
        validateLanguage(request.getLanguage());
        validateHintLevel(request.getHintLevel());
        validateQuestionType(request.getQuestionType());
        validateQuestionPayload(request);
        validateSubmissionResult(request);
    }

    private void validateProblemId(HintRequest request) {
        if (request.getProblemId() == null) {
            throw new CoteaException("MISSING_PROBLEM_ID", "problemId가 필요합니다.", 400);
        }
    }

    private void validateStage(String stage) {
        if (!STAGES.contains(stage)) {
            throw new CoteaException("INVALID_STAGE", "stage 값이 올바르지 않습니다.", 400);
        }
    }

    private void validateLanguage(String language) {
        // 프론트엔드가 언어 미지원 시 경고만 띄우고 전송 자체는 막지 않으므로(작업자
        // 경고 무시 가능), 그리고 API를 직접 호출하는 경로는 프론트 검사를 아예 거치지
        // 않으므로, 백엔드가 최종 방어선 역할을 한다. "java"만 대소문자 무관 정확히
        // 일치할 때만 허용 - 프론트의 /java/i 부분일치 검사와 달리 "JavaScript" 같은
        // 값이 잘못 통과하지 않도록 정확히 일치하는지 확인한다.
        if (!SUPPORTED_LANGUAGE.equalsIgnoreCase(language)) {
            throw new CoteaException("UNSUPPORTED_LANGUAGE", "지원하지 않는 언어입니다. Java만 지원합니다.", 400);
        }
    }

    private void validateHintLevel(Integer hintLevel) {
        if (hintLevel != null && (hintLevel < 1 || hintLevel > 4)) {
            throw new CoteaException("INVALID_HINT_LEVEL", "hintLevel은 1~4 사이여야 합니다.", 400);
        }
    }

    private void validateQuestionType(String questionType) {
        if (!QUESTION_TYPES.contains(questionType)) {
            throw new CoteaException("INVALID_QUESTION_TYPE", "questionType 값이 올바르지 않습니다.", 400);
        }
    }

    private void validateQuestionPayload(HintRequest request) {
        if ("FREE_TEXT".equals(request.getQuestionType())) {
            if (isBlank(request.getQuestionText())) {
                throw new CoteaException("MISSING_QUESTION_TEXT", "questionText가 필요합니다.", 400);
            }
            return;
        }

        if (isBlank(request.getButtonId())) {
            throw new CoteaException("MISSING_BUTTON_ID", "buttonId가 필요합니다.", 400);
        }
        validateButtonId(request.getStage(), request.getButtonId());
    }

    private void validateButtonId(String stage, String buttonId) {
        if (!questionResolver.supportsButtonId(buttonId)) {
            throw new CoteaException("INVALID_BUTTON_ID", "buttonId 값이 올바르지 않습니다.", 400);
        }
        if ("WRONG_ANSWER".equals(stage) && !WRONG_ANSWER_BUTTONS.contains(buttonId)) {
            throw new CoteaException("INVALID_BUTTON_ID", "WRONG_ANSWER 단계에서 사용할 수 없는 buttonId입니다.", 400);
        }
        if (("BEFORE_SOLVE".equals(stage) || "SOLVING".equals(stage)) && !HINT_BUTTONS.contains(buttonId)) {
            throw new CoteaException("INVALID_BUTTON_ID", "해당 단계에서 사용할 수 없는 buttonId입니다.", 400);
        }
    }

    private void validateSubmissionResult(HintRequest request) {
        if (!"WRONG_ANSWER".equals(request.getStage())) {
            return;
        }
        if (isBlank(request.getSubmissionResult())) {
            throw new CoteaException("MISSING_SUBMISSION_RESULT", "submissionResult가 필요합니다.", 400);
        }
        if (!SUBMISSION_RESULTS.contains(request.getSubmissionResult())) {
            throw new CoteaException("INVALID_SUBMISSION_RESULT", "submissionResult 값이 올바르지 않습니다.", 400);
        }
        validateWrongAnswerButtonMatchesSubmissionResult(request);
    }

    private void validateWrongAnswerButtonMatchesSubmissionResult(HintRequest request) {
        if (!"BUTTON".equals(request.getQuestionType())) {
            return;
        }
        String buttonId = request.getButtonId();
        String submissionResult = request.getSubmissionResult();
        if ("why_wrong".equals(buttonId) && !"WRONG_ANSWER".equals(submissionResult)) {
            throw new CoteaException("INVALID_SUBMISSION_RESULT", "buttonId와 submissionResult가 일치하지 않습니다.", 400);
        }
        if ("why_tle".equals(buttonId) && !"TIME_LIMIT_EXCEEDED".equals(submissionResult)) {
            throw new CoteaException("INVALID_SUBMISSION_RESULT", "buttonId와 submissionResult가 일치하지 않습니다.", 400);
        }
        if ("why_runtime_error".equals(buttonId) && !"RUNTIME_ERROR".equals(submissionResult)) {
            throw new CoteaException("INVALID_SUBMISSION_RESULT", "buttonId와 submissionResult가 일치하지 않습니다.", 400);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
