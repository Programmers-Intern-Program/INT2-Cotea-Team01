package com.cotea.controller.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {

    private final Long id;
    private final String nickname;
    private final String email;
    private final String profileImageUrl;
}
