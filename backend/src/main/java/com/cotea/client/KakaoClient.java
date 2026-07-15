package com.cotea.client;

import com.cotea.client.dto.KakaoTokenResponse;
import com.cotea.client.dto.KakaoUserResponse;
import com.cotea.config.CoteaProperties;
import com.cotea.exception.CoteaException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class KakaoClient {

    private final WebClient kakaoAuthWebClient;
    private final WebClient kakaoApiWebClient;
    private final CoteaProperties properties;

    public KakaoClient(
            @Qualifier("kakaoAuthWebClient") WebClient kakaoAuthWebClient,
            @Qualifier("kakaoApiWebClient") WebClient kakaoApiWebClient,
            CoteaProperties properties
    ) {
        this.kakaoAuthWebClient = kakaoAuthWebClient;
        this.kakaoApiWebClient = kakaoApiWebClient;
        this.properties = properties;
    }

    public KakaoTokenResponse requestToken(String code, String redirectUri) {
        validateKakaoConfig();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.getKakao().getClientId());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (properties.getKakao().getClientSecret() != null
                && !properties.getKakao().getClientSecret().isBlank()) {
            form.add("client_secret", properties.getKakao().getClientSecret());
        }

        try {
            KakaoTokenResponse response = kakaoAuthWebClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(KakaoTokenResponse.class)
                    .block(Duration.ofSeconds(10));

            if (response == null || response.getAccessToken() == null || response.getAccessToken().isBlank()) {
                throw new CoteaException("KAKAO_AUTH_ERROR", "카카오 토큰 응답이 비어 있습니다.", 502);
            }
            return response;
        } catch (WebClientResponseException e) {
            log.warn("Kakao token API returned status {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new CoteaException("KAKAO_AUTH_ERROR", "카카오 로그인 인증에 실패했습니다.", 401);
        } catch (WebClientRequestException | IllegalStateException e) {
            log.warn("Kakao token API request failed: {}", e.getMessage());
            throw new CoteaException("KAKAO_SERVICE_UNAVAILABLE", "카카오 로그인 서비스 연결에 실패했습니다.", 502);
        }
    }

    public KakaoUserResponse requestUser(String kakaoAccessToken) {
        try {
            KakaoUserResponse response = kakaoApiWebClient.get()
                    .uri("/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(kakaoAccessToken))
                    .retrieve()
                    .bodyToMono(KakaoUserResponse.class)
                    .block(Duration.ofSeconds(10));

            if (response == null || response.getId() == null) {
                throw new CoteaException("KAKAO_AUTH_ERROR", "카카오 사용자 응답이 비어 있습니다.", 502);
            }
            return response;
        } catch (WebClientResponseException e) {
            log.warn("Kakao user API returned status {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new CoteaException("KAKAO_AUTH_ERROR", "카카오 사용자 정보 조회에 실패했습니다.", 401);
        } catch (WebClientRequestException | IllegalStateException e) {
            log.warn("Kakao user API request failed: {}", e.getMessage());
            throw new CoteaException("KAKAO_SERVICE_UNAVAILABLE", "카카오 사용자 정보 조회에 실패했습니다.", 502);
        }
    }

    private void validateKakaoConfig() {
        if (properties.getKakao().getClientId() == null || properties.getKakao().getClientId().isBlank()) {
            throw new CoteaException("KAKAO_CONFIG_ERROR", "KAKAO_CLIENT_ID가 설정되지 않았습니다.", 500);
        }
    }
}
