package com.cotea.service.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

public record HintLogContext(
        JsonNode policy,
        JsonNode problem,
        ObjectNode problemContext,
        List<String> tags,
        String question,
        String route,
        String llmProvider
) {
}
