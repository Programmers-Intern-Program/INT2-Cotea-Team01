package com.cotea.client;

import com.cotea.controller.dto.ConversationMessage;
import java.util.List;

public interface LlmClient {

    String generate(String systemPrompt, List<ConversationMessage> history, String userMessage);

    /**
     * 이미지(문제 페이지에 포함된 격자/그래프 그림 등)를 함께 전달해서 생성한다.
     * 대화 히스토리는 지원하지 않는다 — 문제 데이터 생성처럼 단발성 호출에서만 쓰는 용도라 히스토리가
     * 필요 없다. 이미지 입력을 지원하지 않는 구현체는 기본적으로 예외를 던진다.
     */
    default String generateWithImages(String systemPrompt, String userMessage, List<String> imageUrls) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + "는 이미지 입력을 지원하지 않습니다.");
    }
}
