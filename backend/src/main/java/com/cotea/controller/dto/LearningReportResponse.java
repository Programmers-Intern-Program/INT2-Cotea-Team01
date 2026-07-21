package com.cotea.controller.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LearningReportResponse {

    private final int periodDays;
    private final int totalHintCount;
    private final List<ReportCountItem> topWeaknessTypes;
    private final List<ReportCountItem> topIntents;
    private final List<ReportCountItem> topTags;
    private final String summary;
}
