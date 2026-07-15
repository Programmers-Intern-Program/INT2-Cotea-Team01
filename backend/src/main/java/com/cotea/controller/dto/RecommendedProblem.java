package com.cotea.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendedProblem {

    private final Integer problemId;
    private final String title;
    private final String level;
    private final String url;
    private final String tag;
    private final String subcategory;

    /** SIMILAR_PATTERN | BASIC_CONCEPT_REVIEW | SAME_ALGORITHM_PRACTICE */
    private final String recommendationType;

    private final String reason;
}
