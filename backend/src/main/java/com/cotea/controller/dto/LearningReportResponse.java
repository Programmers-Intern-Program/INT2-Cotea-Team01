package com.cotea.controller.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LearningReportResponse {

    private final int periodDays;
    private final int totalHintCount;
    private final int solvedProblemCount;
    private final int solvedCount;
    private final int unresolvedHintCount;
    private final List<ReportCountItem> topWeaknessTypes;
    private final List<ReportCountItem> topIntents;
    private final List<ReportCountItem> topTags;
    private final List<String> insights;
    private final List<RecommendedProblem> recommendations;
    private final List<RecommendedProblem> reviewRecommendations;
    private final List<RecommendedProblem> diversityRecommendations;
    private final String summary;
}
