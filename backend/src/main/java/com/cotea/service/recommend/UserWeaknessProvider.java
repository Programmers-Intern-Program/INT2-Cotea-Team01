package com.cotea.service.recommend;

/**
 * 로그인 사용자의 추천 개인화에 쓰는 읽기 계약.
 *
 * <p>구현체는 학습 로그(user_hint_log 등)를 조회해 프로필을 반환한다.
 * 데이터 없음은 빈 프로필을 반환해 stateless 추천과 동일하게 동작한다.
 */
public interface UserWeaknessProvider {

    UserRecommendationProfile getProfile(long userId);
}
