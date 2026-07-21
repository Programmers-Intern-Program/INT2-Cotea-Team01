package com.cotea.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotea.config.CoteaProperties;
import com.cotea.exception.CoteaException;
import com.cotea.service.auth.entity.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        CoteaProperties properties = new CoteaProperties();
        properties.getJwt().setSecret("test-secret-for-jwt-signing");
        properties.getJwt().setIssuer("cotea-test");
        jwtTokenProvider = new JwtTokenProvider(properties, new ObjectMapper());
    }

    @Test
    void 토큰을_발급하고_userId를_파싱한다() {
        UserEntity user = UserEntity.kakao("12345", "a@b.com", "테스트", null);
        ReflectionTestUtils.setField(user, "id", 42L);

        String token = jwtTokenProvider.createAccessToken(user);
        String authorization = "Bearer " + token;

        assertThat(jwtTokenProvider.parseUserId(authorization)).isEqualTo(42L);
        assertThat(jwtTokenProvider.resolveUserId(authorization)).contains(42L);
    }

    @Test
    void 토큰이_없으면_resolveUserId는_empty를_반환한다() {
        assertThat(jwtTokenProvider.resolveUserId(null)).isEmpty();
        assertThat(jwtTokenProvider.resolveUserId("")).isEmpty();
    }

    @Test
    void 잘못된_토큰은_resolveUserId가_empty를_반환한다() {
        assertThat(jwtTokenProvider.resolveUserId("Bearer invalid.token.here")).isEmpty();
    }

    @Test
    void parseUserId는_유효하지_않은_토큰에_401을_던진다() {
        assertThatThrownBy(() -> jwtTokenProvider.parseUserId("Bearer bad.token"))
                .isInstanceOf(CoteaException.class);
    }
}
