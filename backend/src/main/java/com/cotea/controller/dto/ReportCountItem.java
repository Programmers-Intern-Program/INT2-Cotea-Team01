package com.cotea.controller.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportCountItem {

    private final String name;
    private final long count;
    private final String message;
}
