package com.cotea.client;

import com.cotea.controller.dto.ConversationMessage;
import java.util.List;

public interface LlmClient {

    String generate(String systemPrompt, List<ConversationMessage> history, String userMessage);
}
