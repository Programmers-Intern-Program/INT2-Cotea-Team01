package com.cotea.controller;

import com.cotea.controller.dto.AuthResponse;
import com.cotea.controller.dto.KakaoAuthorizeUrlResponse;
import com.cotea.controller.dto.KakaoLoginRequest;
import com.cotea.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/kakao/authorize-url")
    public KakaoAuthorizeUrlResponse kakaoAuthorizeUrl(
            @RequestParam String redirectUri,
            @RequestParam(required = false) String state
    ) {
        return authService.getKakaoAuthorizeUrl(redirectUri, state);
    }

    @GetMapping("/kakao/authorize")
    public RedirectView kakaoAuthorize(
            @RequestParam String redirectUri,
            @RequestParam(required = false) String state
    ) {
        return new RedirectView(authService.getKakaoAuthorizeUrl(redirectUri, state).getAuthorizeUrl());
    }

    @PostMapping("/kakao")
    public AuthResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.loginWithKakao(request);
    }

    @GetMapping("/kakao/callback")
    public AuthResponse kakaoCallback(@RequestParam String code) {
        KakaoLoginRequest request = new KakaoLoginRequest();
        request.setCode(code);
        return authService.loginWithKakao(request);
    }
}
