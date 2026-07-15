package com.cotea.controller.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoAuthorizeUrlResponse {

    private final String authorizeUrl;
}
