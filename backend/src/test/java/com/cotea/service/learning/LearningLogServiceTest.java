package com.cotea.service.learning;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotea.controller.dto.HintRequest;
import com.cotea.service.auth.JwtTokenProvider;
import com.cotea.service.auth.UserRepository;
import com.cotea.service.learning.LearningLogService.SaveResult;
import com.cotea.service.learning.entity.UserHintLogEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LearningLogServiceTest {

    @Test
    void 토큰_userId가_DB에_없으면_힌트로그를_저장하지_않는다() {
        JwtTokenProvider jwtTokenProvider = Mockito.mock(JwtTokenProvider.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        UserHintLogRepository userHintLogRepository = Mockito.mock(UserHintLogRepository.class);
        WeaknessClassifier weaknessClassifier = Mockito.mock(WeaknessClassifier.class);
        LearningLogService service = new LearningLogService(
                jwtTokenProvider,
                userRepository,
                userHintLogRepository,
                weaknessClassifier,
                new ObjectMapper()
        );
        when(jwtTokenProvider.parseUserId("Bearer stale-token")).thenReturn(99L);
        when(userRepository.existsById(99L)).thenReturn(false);

        SaveResult result = service.saveIfAuthenticated("Bearer stale-token", request(), context());

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(SaveResult.REAUTH_REQUIRED);
        verify(userHintLogRepository, never()).save(any(UserHintLogEntity.class));
        verify(weaknessClassifier, never()).classify(any(), any(), any());
    }

    private HintRequest request() {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("SOLVING");
        request.setQuestionType("FREE_TEXT");
        request.setQuestionText("방문 처리를 모르겠어요");
        request.setLanguage("Java");
        return request;
    }

    private HintLogContext context() {
        return new HintLogContext(
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(),
                List.of("dfs"),
                "방문 처리를 모르겠어요",
                "RELATED",
                "claude"
        );
    }
}
