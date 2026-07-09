package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class HintLevelResolver {

    public int resolve(HintRequest request, JsonNode policy) {
        if (request.getHintLevel() != null) {
            return request.getHintLevel();
        }
        String stage = request.getStage();
        JsonNode stagePolicy = policy.path("phasePolicy").path(stage);
        if (stagePolicy.has("defaultHintLevel")) {
            return stagePolicy.get("defaultHintLevel").asInt();
        }
        return 2;
    }
}
