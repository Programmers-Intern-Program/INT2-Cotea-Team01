package com.cotea.service.auth;

import com.cotea.client.KakaoClient;
import com.cotea.client.dto.KakaoTokenResponse;
import com.cotea.client.dto.KakaoUserResponse;
import com.cotea.config.CoteaProperties;
import com.cotea.controller.dto.AuthResponse;
import com.cotea.controller.dto.KakaoAuthorizeUrlResponse;
import com.cotea.controller.dto.KakaoLoginRequest;
import com.cotea.controller.dto.UserProfileResponse;
import com.cotea.exception.CoteaException;
import com.cotea.service.auth.entity.AuthProvider;
import com.cotea.service.auth.entity.UserEntity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoClient kakaoClient;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final CoteaProperties properties;

    public KakaoAuthorizeUrlResponse getKakaoAuthorizeUrl(String redirectUri, String state) {
        validateKakaoClientId();
        String authorizeUrl = buildKakaoAuthorizeUrl(redirectUri, state);

        return KakaoAuthorizeUrlResponse.builder()
                .authorizeUrl(authorizeUrl)
                .build();
    }

    private String buildKakaoAuthorizeUrl(String redirectUri, String state) {
        StringBuilder url = new StringBuilder(properties.getKakao().getAuthBaseUrl())
                .append("/oauth/authorize")
                .append("?response_type=code")
                .append("&client_id=").append(encodeQueryParam(properties.getKakao().getClientId()))
                .append("&redirect_uri=").append(encodeQueryParam(redirectUri));

        if (state != null && !state.isBlank()) {
            url.append("&state=").append(encodeQueryParam(state));
        }
        return url.toString();
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Transactional
    public AuthResponse loginWithKakao(KakaoLoginRequest request) {
        String redirectUri = resolveRedirectUri(request.getRedirectUri());
        KakaoTokenResponse token = kakaoClient.requestToken(request.getCode(), redirectUri);
        KakaoUserResponse kakaoUser = kakaoClient.requestUser(token.getAccessToken());

        UserEntity user = upsertKakaoUser(kakaoUser);
        String accessToken = jwtTokenProvider.createAccessToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.accessTokenExpiresIn())
                .user(toProfile(user))
                .build();
    }

    private UserEntity upsertKakaoUser(KakaoUserResponse kakaoUser) {
        String providerId = String.valueOf(kakaoUser.getId());
        return userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId)
                .map(user -> {
                    user.updateProfile(kakaoUser.getEmail(), kakaoUser.getNickname(), kakaoUser.getProfileImageUrl());
                    return user;
                })
                .orElseGet(() -> userRepository.save(UserEntity.kakao(
                        providerId,
                        kakaoUser.getEmail(),
                        kakaoUser.getNickname(),
                        kakaoUser.getProfileImageUrl()
                )));
    }

    private String resolveRedirectUri(String requestRedirectUri) {
        if (requestRedirectUri != null && !requestRedirectUri.isBlank()) {
            return requestRedirectUri;
        }
        String configured = properties.getKakao().getRedirectUri();
        if (configured == null || configured.isBlank()) {
            throw new CoteaException("KAKAO_CONFIG_ERROR", "KAKAO_REDIRECT_URI가 설정되지 않았습니다.", 500);
        }
        return configured;
    }

    private void validateKakaoClientId() {
        if (properties.getKakao().getClientId() == null || properties.getKakao().getClientId().isBlank()) {
            throw new CoteaException("KAKAO_CONFIG_ERROR", "KAKAO_CLIENT_ID가 설정되지 않았습니다.", 500);
        }
    }

    private UserProfileResponse toProfile(UserEntity user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
