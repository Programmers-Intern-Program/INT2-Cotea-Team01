package com.cotea.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendationResponse {

    private final Integer sourceProblemId;
    private final List<String> sourceTags;
    private final List<RecommendedProblem> recommendations;
}
