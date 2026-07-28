package com.cotea.controller.dto;

import lombok.Getter;

@Getter
public class KakaoAuthorizeUrlResponse {

    private final String authorizeUrl;

    public KakaoAuthorizeUrlResponse(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }
}
