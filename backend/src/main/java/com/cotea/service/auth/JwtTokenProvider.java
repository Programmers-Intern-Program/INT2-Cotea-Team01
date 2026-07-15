package com.cotea.service.auth;

import com.cotea.config.CoteaProperties;
import com.cotea.exception.CoteaException;
import com.cotea.service.auth.entity.UserEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final CoteaProperties properties;
    private final ObjectMapper objectMapper;

    public String createAccessToken(UserEntity user) {
        validateJwtConfig();

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getJwt().getAccessTokenTtlSeconds());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", properties.getJwt().getIssuer());
        payload.put("sub", String.valueOf(user.getId()));
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("provider", user.getProvider().name());
        payload.put("nickname", user.getNickname());

        String unsignedToken = base64UrlJson(header) + "." + base64UrlJson(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public long accessTokenExpiresIn() {
        return properties.getJwt().getAccessTokenTtlSeconds();
    }

    private String base64UrlJson(Map<String, Object> value) {
        try {
            return base64Url(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new CoteaException("AUTH_TOKEN_ERROR", "인증 토큰 생성에 실패했습니다.", 500);
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return base64Url(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CoteaException("AUTH_TOKEN_ERROR", "인증 토큰 서명에 실패했습니다.", 500);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateJwtConfig() {
        if (properties.getJwt().getSecret() == null || properties.getJwt().getSecret().isBlank()) {
            throw new CoteaException("JWT_CONFIG_ERROR", "COTEA_JWT_SECRET이 설정되지 않았습니다.", 500);
        }
    }
}
