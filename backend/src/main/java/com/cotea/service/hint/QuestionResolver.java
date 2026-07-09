package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QuestionResolver {

    private static final Map<String, String> BUTTON_QUESTIONS = Map.ofEntries(
            Map.entry("hint_level_1", "이 문제를 어떤 관점에서 바라봐야 할지 모르겠어요"),
            Map.entry("hint_level_2", "어떤 알고리즘으로 접근해야 할지 모르겠어요"),
            Map.entry("hint_level_3", "구현 순서가 잘 안 잡혀요"),
            Map.entry("hint_level_4", "제 코드에서 문제가 있는지 봐주세요"),
            Map.entry("wrong_result_only", "채점이 끝났어요"),
            Map.entry("why_wrong", "왜 틀렸는지 알려주세요"),
            Map.entry("why_tle", "왜 시간초과가 났는지 알려주세요"),
            Map.entry("why_runtime_error", "왜 런타임 에러가 났는지 알려주세요")
    );

    public String resolve(HintRequest request) {
        if ("FREE_TEXT".equals(request.getQuestionType())) {
            return request.getQuestionText() == null ? "" : request.getQuestionText().trim();
        }
        return BUTTON_QUESTIONS.getOrDefault(request.getButtonId(), "힌트를 주세요");
    }

    public boolean userAsksReason(String question) {
        return question.matches(".*(왜|이유|원인|뭐가\\s*틀|어디가\\s*틀|잘못).*");
    }
}
