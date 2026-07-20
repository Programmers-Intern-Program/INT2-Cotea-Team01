package com.cotea.service.recommend;

import org.springframework.stereotype.Component;

/**
 * 학습 로그 연동 전 폴백. 항상 빈 프로필을 반환해 stateless 추천과 동일하게 동작한다.
 */
@Component
public class NoOpUserWeaknessProvider implements UserWeaknessProvider {

    @Override
    public UserRecommendationProfile getProfile(long userId) {
        return UserRecommendationProfile.empty();
    }
}
