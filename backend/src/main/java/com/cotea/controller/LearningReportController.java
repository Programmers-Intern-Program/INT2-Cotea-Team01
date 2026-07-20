package com.cotea.controller;

import com.cotea.controller.dto.LearningReportResponse;
import com.cotea.service.learning.LearningReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class LearningReportController {

    private final LearningReportService learningReportService;

    @GetMapping("/me/weekly")
    public LearningReportResponse weekly(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "days", required = false, defaultValue = "7") int days
    ) {
        return learningReportService.weekly(authorization, days);
    }
}
