package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.cotea.client.OpenAiClient;
import com.cotea.exception.CoteaException;
import com.cotea.service.hint.OffTopicQuestionClassifier.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OffTopicRouteLlmClassifierTest {

    @Mock
    private OpenAiClient openAiClient;

    @InjectMocks
    private OffTopicRouteLlmClassifier classifier;

    @Test
    void openAi미설정이면_RELATED() {
        when(openAiClient.isConfigured()).thenReturn(false);

        assertThat(classifier.classify("영역이 뭐예요?", "카카오프렌즈 컬러링북", "Lv2"))
                .isEqualTo(Verdict.RELATED);
    }

    @Test
    void RELATED_토큰을_파싱한다() {
        when(openAiClient.isConfigured()).thenReturn(true);
        when(openAiClient.generate(anyString(), anyList(), anyString())).thenReturn("RELATED");

        assertThat(classifier.classify("영역이 뭐예요?", "카카오프렌즈 컬러링북", "Lv2"))
                .isEqualTo(Verdict.RELATED);
    }

    @Test
    void OFF_TOPIC_토큰을_파싱한다() {
        when(openAiClient.isConfigured()).thenReturn(true);
        when(openAiClient.generate(anyString(), anyList(), anyString())).thenReturn("OFF_TOPIC");

        assertThat(classifier.classify("자바 HashMap이 뭐야?", "카카오프렌즈 컬러링북", "Lv2"))
                .isEqualTo(Verdict.OFF_TOPIC);
    }

    @Test
    void 호출_실패_시_RELATED() {
        when(openAiClient.isConfigured()).thenReturn(true);
        when(openAiClient.generate(anyString(), anyList(), anyString()))
                .thenThrow(new CoteaException("AI_SERVICE_ERROR", "fail", 500));

        assertThat(classifier.classify("영역이 뭐예요?", "제목", "Lv2"))
                .isEqualTo(Verdict.RELATED);
    }

    @Test
    void 파싱_불가_시_RELATED() {
        assertThat(classifier.parse("maybe")).isEqualTo(Verdict.RELATED);
        assertThat(classifier.parse(null)).isEqualTo(Verdict.RELATED);
    }
}
