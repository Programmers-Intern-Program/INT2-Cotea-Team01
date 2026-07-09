package com.cotea.service.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptPolicyLoader {

    private final JsonNode policy;

    public PromptPolicyLoader(ObjectMapper objectMapper) throws IOException {
        try (InputStream in = new ClassPathResource("config/prompt-policy.json").getInputStream()) {
            this.policy = objectMapper.readTree(in);
        }
    }

    public JsonNode getPolicy() {
        return policy;
    }
}
