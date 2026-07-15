package com.cotea.controller.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;
    private final UserProfileResponse user;
}
