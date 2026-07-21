package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotea.controller.dto.HintRequest;
import com.cotea.exception.CoteaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HintRequestValidatorTest {

    private HintRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HintRequestValidator(new QuestionResolver());
    }

    @Test
    void validatesButtonRequest() {
        HintRequest request = baseRequest();
        request.setQuestionType("BUTTON");
        request.setButtonId("hint_level_1");

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void acceptsJavaLanguageRegardlessOfCase() {
        HintRequest request = baseRequest();
        request.setQuestionType("BUTTON");
        request.setButtonId("hint_level_1");
        request.setLanguage("JAVA");

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedLanguage() {
        HintRequest request = baseRequest();
        request.setLanguage("Python");

        assertErrorCode(request, "UNSUPPORTED_LANGUAGE");
    }

    @Test
    void rejectsMissingLanguage() {
        HintRequest request = baseRequest();
        request.setLanguage(null);

        assertErrorCode(request, "UNSUPPORTED_LANGUAGE");
    }

    @Test
    void rejectsLanguageThatMerelyContainsJava() {
        HintRequest request = baseRequest();
        request.setLanguage("JavaScript");

        assertErrorCode(request, "UNSUPPORTED_LANGUAGE");
    }

    @Test
    void rejectsInvalidHintLevel() {
        HintRequest request = baseRequest();
        request.setHintLevel(5);
        request.setQuestionType("BUTTON");
        request.setButtonId("hint_level_1");

        assertErrorCode(request, "INVALID_HINT_LEVEL");
    }

    @Test
    void rejectsInvalidQuestionType() {
        HintRequest request = baseRequest();
        request.setQuestionType("TEXT");

        assertErrorCode(request, "INVALID_QUESTION_TYPE");
    }

    @Test
    void requiresButtonIdForButtonRequest() {
        HintRequest request = baseRequest();
        request.setQuestionType("BUTTON");

        assertErrorCode(request, "MISSING_BUTTON_ID");
    }

    @Test
    void rejectsUnknownButtonId() {
        HintRequest request = baseRequest();
        request.setQuestionType("BUTTON");
        request.setButtonId("unknown_button");

        assertErrorCode(request, "INVALID_BUTTON_ID");
    }

    @Test
    void rejectsWrongAnswerButtonOutsideWrongAnswerStage() {
        HintRequest request = baseRequest();
        request.setQuestionType("BUTTON");
        request.setButtonId("why_wrong");

        assertErrorCode(request, "INVALID_BUTTON_ID");
    }

    @Test
    void requiresQuestionTextForFreeTextRequest() {
        HintRequest request = baseRequest();
        request.setQuestionType("FREE_TEXT");
        request.setQuestionText(" ");

        assertErrorCode(request, "MISSING_QUESTION_TEXT");
    }

    @Test
    void requiresSubmissionResultForWrongAnswerStage() {
        HintRequest request = baseRequest();
        request.setStage("WRONG_ANSWER");
        request.setQuestionType("BUTTON");
        request.setButtonId("why_wrong");

        assertErrorCode(request, "MISSING_SUBMISSION_RESULT");
    }

    @Test
    void rejectsInvalidSubmissionResult() {
        HintRequest request = baseRequest();
        request.setStage("WRONG_ANSWER");
        request.setQuestionType("BUTTON");
        request.setButtonId("why_wrong");
        request.setSubmissionResult("FAILED");

        assertErrorCode(request, "INVALID_SUBMISSION_RESULT");
    }

    @Test
    void rejectsWrongAnswerButtonThatDoesNotMatchSubmissionResult() {
        HintRequest request = baseRequest();
        request.setStage("WRONG_ANSWER");
        request.setQuestionType("BUTTON");
        request.setButtonId("why_tle");
        request.setSubmissionResult("WRONG_ANSWER");

        assertErrorCode(request, "INVALID_SUBMISSION_RESULT");
    }

    @Test
    void validatesWrongAnswerButtonRequest() {
        HintRequest request = baseRequest();
        request.setStage("WRONG_ANSWER");
        request.setQuestionType("BUTTON");
        request.setButtonId("why_tle");
        request.setSubmissionResult("TIME_LIMIT_EXCEEDED");

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    private HintRequest baseRequest() {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("BEFORE_SOLVE");
        request.setLanguage("Java");
        return request;
    }

    private void assertErrorCode(HintRequest request, String errorCode) {
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(CoteaException.class)
                .satisfies(error -> assertThat(((CoteaException) error).getErrorCode()).isEqualTo(errorCode));
    }
}
