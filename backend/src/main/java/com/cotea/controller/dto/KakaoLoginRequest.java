package com.cotea.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KakaoLoginRequest {

    @NotBlank
    private String code;

    private String redirectUri;
}
